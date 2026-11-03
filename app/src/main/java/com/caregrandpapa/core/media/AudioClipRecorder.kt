package com.caregrandpapa.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File

/**
 * 15초 주변 소리 녹음.
 *
 * 실시간 스트리밍(WebRTC)을 대신하는 쪽이다. 한 번에 정해진 길이만 녹음하므로
 * NAT 통과, TURN 서버, 재협상 같은 문제가 아예 없다.
 *
 * 음성 확인용이라 32kbps 모노로 충분하다. 15초에 약 60KB 로, 전송도 저장도 부담이 없다.
 */
class AudioClipRecorder(private val context: Context) {

    /**
     * [output] 에 [durationMs] 만큼 녹음한다.
     *
     * @throws Exception 마이크를 열 수 없는 경우(다른 앱이 점유, 권한 없음 등)
     */
    suspend fun record(output: File, durationMs: Long) {
        output.parentFile?.mkdirs()

        @Suppress("DEPRECATION") // API 31 미만은 인자 없는 생성자만 있다
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(SAMPLE_RATE)
            recorder.setAudioEncodingBitRate(BIT_RATE)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()

            delay(durationMs)

            // stop() 은 녹음이 너무 짧으면 예외를 던진다. 그 경우 파일은 버린다.
            runCatching { recorder.stop() }
                .onFailure {
                    Log.w(TAG, "녹음 종료 실패: ${it.message}")
                    output.delete()
                    throw it
                }
        } finally {
            runCatching { recorder.release() }
        }
    }

    private companion object {
        const val TAG = "AudioClipRecorder"
        const val SAMPLE_RATE = 22_050
        const val BIT_RATE = 32_000
    }
}
