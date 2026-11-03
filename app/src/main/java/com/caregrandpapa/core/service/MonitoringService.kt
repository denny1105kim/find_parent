package com.caregrandpapa.core.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.caregrandpapa.MainActivity
import com.caregrandpapa.R
import com.caregrandpapa.core.media.RequestProcessor
import com.caregrandpapa.data.Auth
import com.caregrandpapa.data.PairRepository
import com.caregrandpapa.data.PairStatus
import com.caregrandpapa.data.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 환자 폰에서 간병인의 요청을 기다리는 서비스.
 *
 * 상시 송출이 아니라 **대기**만 한다. 하는 일은 Firestore 요청 문서를 구독하는 것뿐이고,
 * 요청이 들어올 때만 15초 녹음·녹화나 위치 측정을 한 번 수행한다([RequestProcessor]).
 * 그래서 예전 구조보다 배터리 소모가 훨씬 적다.
 *
 * 그래도 Foreground Service 로 두는 이유가 두 가지다.
 *  1. 앱이 백그라운드일 때 마이크·카메라를 쓰려면 해당 타입의 FGS 가 이미 떠 있어야 한다.
 *     (안드로이드 14+ 는 백그라운드에서 새로 시작하는 마이크/카메라 FGS 를 막는다.)
 *  2. Firestore 구독이 살아 있어야 요청이 즉시 전달된다. FCM 은 이 구독이 죽었을 때의
 *     복구 수단이다.
 *
 * [LifecycleService] 를 상속하는 것은 CameraX 의 `bindToLifecycle` 이 LifecycleOwner 를
 * 요구하기 때문이다.
 */
class MonitoringService : LifecycleService() {

    private var processor: RequestProcessor? = null
    private var pairWatchJob: Job? = null
    private var deviceStatusJob: Job? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            Prefs(this).sharingPaused = true
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = Prefs(this)
        if (prefs.sharingPaused || prefs.role != com.caregrandpapa.data.model.Role.PATIENT) {
            stopSelf()
            return START_NOT_STICKY
        }
        val pairId = intent?.getStringExtra(EXTRA_PAIR_ID) ?: prefs.pairId
        if (pairId.isNullOrBlank()) {
            Log.w(TAG, "페어 ID 없음 → 서비스 종료")
            stopSelf()
            return START_NOT_STICKY
        }

        val mediaReady = startForegroundSafely()
        MonitoringState.setMediaReady(mediaReady)

        if (!started) {
            started = true
            MonitoringState.setRunning(true)
            lifecycleScope.launch {
                // Firestore 보안 규칙이 UID 를 요구하므로 로그인이 끝난 뒤에 시작한다.
                if (Auth.awaitUidOrNull() == null) {
                    Log.e(TAG, "로그인 실패 → 요청 대기를 시작할 수 없음")
                    return@launch
                }
                PairRepository.setMediaReady(pairId, mediaReady)
                startProcessor(pairId)
            }
        } else {
            // 부팅 직후에는 미디어 타입 승격에 실패했을 수 있다. 사용자가 앱을 열어 다시
            // start 되면 이때 승격이 되므로, 상태를 다시 알린다.
            lifecycleScope.launch {
                if (Auth.awaitUidOrNull() == null) return@launch
                PairRepository.setMediaReady(pairId, mediaReady)
                startProcessor(pairId)
            }
        }
        return START_STICKY
    }

    private fun startProcessor(pairId: String) {
        if (processor != null) return
        processor = RequestProcessor(
            context = this,
            pairId = pairId,
            lifecycleOwner = this,
            scope = lifecycleScope,
        ).apply { start() }
        watchPair(pairId)
        deviceStatusJob = lifecycleScope.launch {
            while (true) {
                try {
                    com.caregrandpapa.data.CareStatusRepository.publishDevice(this@MonitoringService, pairId)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "기기 상태 전송 실패") }
                kotlinx.coroutines.delay(5 * 60_000L)
            }
        }
    }

    /**
     * 페어가 살아 있는지 계속 지켜본다.
     *
     * 이게 없으면 간병인이 연결을 해제한 뒤에도 이 서비스가 영구히 돌면서 배터리를 쓰고,
     * 환자 화면에는 "연결됨" 이 남는다. 재부팅해도 [BootReceiver] 가 다시 살린다.
     *
     * 오프라인이나 일시적 오류([PairStatus.Unavailable])에는 아무것도 하지 않는다 —
     * 잘 쓰던 페어링을 네트워크 문제로 지워 버리면 다시 QR 을 찍어야 한다.
     */
    private fun watchPair(pairId: String) {
        if (pairWatchJob != null) return
        pairWatchJob = lifecycleScope.launch {
            PairRepository.observeStatus(pairId) { Auth.currentUid() }.collect { status ->
                when (status) {
                    is PairStatus.Active -> Unit

                    PairStatus.Removed -> {
                        Log.i(TAG, "간병인이 연결을 해제함 → 종료")
                        finishPairing("간병인이 연결을 해제했습니다")
                    }

                    is PairStatus.NotMine -> {
                        Log.w(TAG, "이 폰은 더 이상 이 페어의 환자가 아님 → 종료")
                        finishPairing("다른 휴대폰이 이 연결에 등록되어 있습니다")
                    }

                    is PairStatus.Unavailable ->
                        Log.d(TAG, "페어 상태 확인 보류: ${status.message}")
                }
            }
        }
    }

    /** 연결을 끝내고 로컬 페어링까지 지운다. 재부팅해도 다시 살아나지 않게 한다. */
    private fun finishPairing(reason: String) {
        Prefs(this).clearAll()
        MonitoringState.setDisconnectReason(reason)
        stopSelf()
    }

    /**
     * Foreground Service 로 승격한다.
     *
     * @return 마이크/카메라 타입까지 올라갔는지. false 면 위치 요청만 처리할 수 있다.
     */
    private fun startForegroundSafely(): Boolean {
        val fullTypes = mediaTypes() or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (fullTypes != ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    buildNotification(mediaReady = true),
                    fullTypes,
                )
                return true
            } catch (e: Exception) {
                // 안드로이드 14+ 에서 백그라운드 시작 시 발생. 위치 전용으로 재시도한다.
                Log.w(TAG, "미디어 포함 startForeground 실패 → 위치 전용 재시도: ${e.message}")
            }
        }

        return try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(mediaReady = false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 실패: ${e.message}", e)
            stopSelf()
            false
        }
    }

    /** 권한이 허용된 미디어 타입만 모은다. */
    private fun mediaTypes(): Int {
        var types = 0
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (hasPermission(Manifest.permission.CAMERA)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return types
    }

    /**
     * 상시 알림.
     *
     * 이 폰의 사용자가 **지금 돌봄 공유가 켜져 있다는 사실을 늘 볼 수 있도록** 항상 떠 있는다.
     * 무엇이 공유되는지 그대로 적고, 사용자가 알림에서 바로 끌 수 있게 **"중지" 버튼**을 둔다.
     * 당사자가 자기 폰에서 언제든 중단할 수 있어야 하기 때문이다.
     *
     * [mediaReady] 가 false 면 부팅 직후처럼 위치만 가능한 상태다. 이때는 눌러서 앱을 열면
     * 마이크·카메라 타입 승격이 되어 소리·영상 공유까지 정상화된다고 안내한다.
     */
    private fun buildNotification(mediaReady: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = "돌봄 공유 켜짐"
        val text = if (mediaReady) {
            "가족이 요청하면 이 폰의 위치·소리·영상을 보냅니다. 끄려면 아래 '중지'를 누르세요."
        } else {
            "지금은 위치만 공유할 수 있습니다. 눌러서 앱을 열면 소리·영상 공유도 켜집니다."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "중지", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "돌봄 공유",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "돌봄 공유가 켜져 있음을 알려주는 상시 알림" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        PermissionHelpers.isGranted(this, permission)

    override fun onDestroy() {
        deviceStatusJob?.cancel()
        deviceStatusJob = null
        processor?.stop()
        processor = null
        pairWatchJob?.cancel()
        pairWatchJob = null
        started = false
        MonitoringState.setRunning(false)
        MonitoringState.setMediaReady(false)
        MonitoringState.setBusyLabel(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MonitoringService"
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIF_ID = 1001
        private const val EXTRA_PAIR_ID = "pair_id"
        private const val ACTION_STOP = "com.caregrandpapa.action.STOP"

        fun start(context: Context, pairId: String) {
            val prefs = Prefs(context)
            if (prefs.sharingPaused || prefs.role != com.caregrandpapa.data.model.Role.PATIENT) return
            val intent = Intent(context, MonitoringService::class.java)
                .putExtra(EXTRA_PAIR_ID, pairId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            Prefs(context).sharingPaused = true
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
