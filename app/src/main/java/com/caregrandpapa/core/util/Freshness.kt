package com.caregrandpapa.core.util

/** 마지막 위치 갱신이 얼마나 최근인지. */
enum class FreshnessLevel {
    /** 방금 확인된 위치. 현재 위치로 봐도 된다. */
    LIVE,

    /** 조금 지났지만 정상 범위. */
    RECENT,

    /** 너무 오래됐다. 환자 폰이 꺼졌거나 서비스가 죽었을 가능성. */
    STALE,

    /** 아직 한 번도 위치를 받지 못했다. */
    UNKNOWN,
}

/** 신선도 판정 결과. [label] 은 그대로 화면에 보여줄 수 있는 문구. */
data class Freshness(
    val level: FreshnessLevel,
    val ageSeconds: Long?,
    val label: String,
)

private const val LIVE_LIMIT_SECONDS = 30L
private const val RECENT_LIMIT_SECONDS = 180L

/**
 * 마지막 갱신 시각으로 신선도를 계산한다.
 *
 * 지도에 마지막으로 받은 위치가 남아 있으면 환자 폰이 꺼진 뒤에도 "실시간"처럼 보인다.
 * 돌봄 앱에서 이 오인은 위험하므로, 화면에는 항상 이 판정 결과를 함께 띄운다.
 *
 * 두 폰의 시계가 어긋나 미래 시각이 오면 0초로 취급한다.
 */
fun freshnessOf(updatedAtMillis: Long?, nowMillis: Long): Freshness {
    if (updatedAtMillis == null) {
        return Freshness(FreshnessLevel.UNKNOWN, null, "아직 위치를 받지 못했습니다")
    }
    val ageSeconds = ((nowMillis - updatedAtMillis) / 1000L).coerceAtLeast(0L)
    val level = when {
        ageSeconds <= LIVE_LIMIT_SECONDS -> FreshnessLevel.LIVE
        ageSeconds <= RECENT_LIMIT_SECONDS -> FreshnessLevel.RECENT
        else -> FreshnessLevel.STALE
    }
    val label = when (level) {
        FreshnessLevel.LIVE -> "방금 확인 · ${formatAge(ageSeconds)}"
        FreshnessLevel.RECENT -> formatAge(ageSeconds)
        FreshnessLevel.STALE -> "${formatAge(ageSeconds)} · 연결이 끊긴 것 같습니다"
        FreshnessLevel.UNKNOWN -> "아직 위치를 받지 못했습니다"
    }
    return Freshness(level, ageSeconds, label)
}

/** 경과 시간을 사람이 읽는 문구로. */
fun formatAge(ageSeconds: Long): String = when {
    ageSeconds < 10 -> "방금"
    ageSeconds < 60 -> "${ageSeconds}초 전"
    ageSeconds < 3600 -> "${ageSeconds / 60}분 전"
    ageSeconds < 86_400 -> "${ageSeconds / 3600}시간 전"
    else -> "${ageSeconds / 86_400}일 전"
}
