package com.caregrandpapa.feature.pairing

/**
 * QR 코드에 담기는 페어링 정보의 인코딩/디코딩.
 *
 * 형식: `findparent://pair?v=1&id=<UUID>`
 *
 * 안드로이드 API 에 의존하지 않는 순수 함수로 두어 단위 테스트가 가능하다.
 * (`android.net.Uri` 를 쓰면 JVM 테스트에서 못 돌린다.)
 */
object PairPayload {

    const val VERSION = 1
    private const val PREFIX = "findparent://pair?"

    /** 간병인 폰이 화면에 띄울 QR 문자열을 만든다. */
    fun encode(pairId: String): String = "${PREFIX}v=$VERSION&id=$pairId"

    /**
     * 스캔한 문자열에서 페어 ID 를 꺼낸다.
     *
     * 형식이 다르거나 버전이 맞지 않거나 ID 가 UUID 형태가 아니면 null.
     * (엉뚱한 QR 을 찍었을 때 조용히 이상한 문서를 만들지 않도록 엄격하게 검사한다.)
     */
    fun decode(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (!text.startsWith(PREFIX)) return null

        val params = text.removePrefix(PREFIX)
            .split('&')
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }
            .toMap()

        if (params["v"]?.toIntOrNull() != VERSION) return null
        val id = params["id"] ?: return null
        return if (isUuid(id)) id else null
    }

    /** 8-4-4-4-12 형태의 16진수 UUID 인지 확인. */
    fun isUuid(value: String): Boolean {
        val groups = value.split('-')
        val expected = intArrayOf(8, 4, 4, 4, 12)
        if (groups.size != expected.size) return false
        groups.forEachIndexed { i, g ->
            if (g.length != expected[i]) return false
            if (!g.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
        }
        return true
    }
}
