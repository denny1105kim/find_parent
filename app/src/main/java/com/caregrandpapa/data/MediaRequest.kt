package com.caregrandpapa.data

/** 간병인이 환자 폰에 보내는 요청 종류. */
enum class RequestType(val wireName: String, val label: String) {
    /** 15초 주변 소리 녹음. */
    AUDIO("audio", "소리 듣기"),

    /** 15초 전면 카메라 녹화. */
    VIDEO_FRONT("video_front", "전면 카메라"),

    /** 15초 후면 카메라 녹화. */
    VIDEO_BACK("video_back", "후면 카메라"),

    /** 현재 위치 1회 측정. */
    LOCATION("location", "위치 새로고침"),
    ;

    val isClip: Boolean get() = this != LOCATION

    val isVideo: Boolean get() = this == VIDEO_FRONT || this == VIDEO_BACK

    /** 저장 파일 확장자. 위치는 파일이 없다. */
    val fileExtension: String?
        get() = when (this) {
            AUDIO -> "m4a"
            VIDEO_FRONT, VIDEO_BACK -> "mp4"
            LOCATION -> null
        }

    companion object {
        fun fromWire(value: String?): RequestType? =
            entries.firstOrNull { it.wireName == value }
    }
}

/**
 * 요청의 진행 상태.
 *
 * ```
 * PENDING → RUNNING → UPLOADING → READY → DELIVERED
 *                 └──────────────────────→ FAILED
 * ```
 * 위치 요청은 업로드가 없으므로 `RUNNING → READY` 로 바로 간다.
 */
enum class RequestStatus(val wireName: String) {
    /** 환자 폰이 아직 집어가지 않음. */
    PENDING("pending"),

    /** 녹음·녹화 또는 위치 측정 중. */
    RUNNING("running"),

    /** 파일을 Storage 에 올리는 중. */
    UPLOADING("uploading"),

    /** 간병인 폰이 받아갈 수 있는 상태. */
    READY("ready"),

    /** 간병인 폰이 내려받아 로컬에 보관했고, 서버 원본은 지웠다. */
    DELIVERED("delivered"),

    /** 권한 없음, 카메라 사용 불가 등으로 실패. */
    FAILED("failed"),
    ;

    companion object {
        fun fromWire(value: String?): RequestStatus? =
            entries.firstOrNull { it.wireName == value }
    }
}

/**
 * `pairs/{pairId}/requests/{requestId}` 문서.
 *
 * 실시간 스트리밍을 없애고 **요청 → 15초 녹음/녹화 → 업로드 → 재생** 방식으로 바꿨기 때문에,
 * 이 문서 하나가 명령이자 진행 상태이자 결과가 된다.
 */
data class MediaRequest(
    val id: String,
    val type: RequestType,
    val status: RequestStatus,
    val createdAtMillis: Long?,
    /** Storage 경로. 다운로드 후 서버 원본을 지우면 그대로 남겨 둔다(로컬 파일 찾기용 아님). */
    val storagePath: String? = null,
    val sizeBytes: Long? = null,
    /** 위치 요청의 결과. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val errorMessage: String? = null,
) {
    /** 환자 폰의 응답을 아직 기다리는 중인지. */
    val isInFlight: Boolean
        get() = status == RequestStatus.PENDING ||
            status == RequestStatus.RUNNING ||
            status == RequestStatus.UPLOADING

    /**
     * 응답이 오지 않아 포기해야 하는 상태인지.
     *
     * 환자 폰이 꺼져 있거나 서비스가 죽었으면 문서가 PENDING 에서 영원히 멈춘다.
     * 간병인이 계속 기다리게 두면 안 되므로 시간으로 판단한다.
     */
    fun isTimedOut(nowMillis: Long): Boolean {
        if (!isInFlight) return false
        val created = createdAtMillis ?: return false
        val limit = if (status == RequestStatus.PENDING) PENDING_TIMEOUT_MS else RUNNING_TIMEOUT_MS
        return nowMillis - created > limit
    }

    companion object {
        /** 환자 폰이 요청을 집어갈 때까지 기다리는 시간(FCM 깨우기 포함). */
        const val PENDING_TIMEOUT_MS = 90_000L

        /** 녹음/녹화 15초 + 업로드까지 감안한 시간. */
        const val RUNNING_TIMEOUT_MS = 180_000L

        /** 녹음·녹화 길이. */
        const val CLIP_DURATION_MS = 15_000L
    }
}
