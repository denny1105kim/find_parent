package com.caregrandpapa.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 위치 신선도 판정.
 *
 * 아버님 폰이 꺼져도 지도에는 마지막 마커가 남는다. 이 판정이 틀리면 보호자가 몇 시간 전
 * 위치를 현재 위치로 오인하게 되므로, 경계값을 명확히 고정해 둔다.
 */
class FreshnessTest {

    private val now = 1_700_000_000_000L

    private fun at(secondsAgo: Long) = freshnessOf(now - secondsAgo * 1000L, now)

    @Test
    fun `기록이 없으면 UNKNOWN`() {
        val f = freshnessOf(null, now)
        assertEquals(FreshnessLevel.UNKNOWN, f.level)
        assertEquals(null, f.ageSeconds)
    }

    @Test
    fun `30초 이내는 LIVE`() {
        assertEquals(FreshnessLevel.LIVE, at(0).level)
        assertEquals(FreshnessLevel.LIVE, at(29).level)
        assertEquals(FreshnessLevel.LIVE, at(30).level)
    }

    @Test
    fun `30초 초과 3분 이내는 RECENT`() {
        assertEquals(FreshnessLevel.RECENT, at(31).level)
        assertEquals(FreshnessLevel.RECENT, at(180).level)
    }

    @Test
    fun `3분 초과는 STALE`() {
        assertEquals(FreshnessLevel.STALE, at(181).level)
        assertEquals(FreshnessLevel.STALE, at(3600).level)
    }

    @Test
    fun `두 폰 시계가 어긋나 미래 시각이 와도 0초로 본다`() {
        val f = freshnessOf(now + 60_000L, now)
        assertEquals(0L, f.ageSeconds)
        assertEquals(FreshnessLevel.LIVE, f.level)
    }

    @Test
    fun `경과 시간 문구`() {
        assertEquals("방금", formatAge(0))
        assertEquals("방금", formatAge(9))
        assertEquals("10초 전", formatAge(10))
        assertEquals("59초 전", formatAge(59))
        assertEquals("1분 전", formatAge(60))
        assertEquals("59분 전", formatAge(3_599))
        assertEquals("1시간 전", formatAge(3_600))
        assertEquals("23시간 전", formatAge(86_399))
        assertEquals("1일 전", formatAge(86_400))
    }

    @Test
    fun `STALE 문구에는 끊김 안내가 포함된다`() {
        val label = at(600).label
        assertEquals(true, label.contains("끊긴"))
    }
}
