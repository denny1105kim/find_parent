package com.caregrandpapa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보호자 폰의 1GB 상한 정리 로직.
 *
 * 클립은 클라우드가 아니라 보호자 폰에 쌓이므로, 이 함수가 잘못되면 폰 용량이 차서
 * 새 녹음을 저장하지 못하거나(정리 부족) 방금 받은 것이 사라진다(과다 정리).
 */
class ClipStoreEvictionTest {

    private val mb = 1024L * 1024L

    private fun candidate(id: String, minutesAgo: Long, sizeMb: Long) =
        EvictionCandidate(
            requestId = id,
            createdAtMillis = 1_700_000_000_000L - minutesAgo * 60_000L,
            sizeBytes = sizeMb * mb,
        )

    @Test
    fun `상한 이하면 아무것도 지우지 않는다`() {
        val candidates = listOf(
            candidate("a", 30, 100),
            candidate("b", 20, 100),
        )
        assertEquals(emptyList<String>(), selectEvictions(candidates, 1024 * mb))
    }

    @Test
    fun `정확히 상한이면 지우지 않는다`() {
        val candidates = listOf(candidate("a", 10, 500), candidate("b", 5, 524))
        val total = candidates.sumOf { it.sizeBytes }
        assertEquals(emptyList<String>(), selectEvictions(candidates, total))
    }

    @Test
    fun `오래된 것부터 지운다`() {
        val candidates = listOf(
            candidate("oldest", 100, 400),
            candidate("middle", 50, 400),
            candidate("newest", 10, 400),
        )
        // 합계 1200MB, 상한 1024MB → 가장 오래된 것 하나만 지우면 800MB
        assertEquals(listOf("oldest"), selectEvictions(candidates, 1024 * mb))
    }

    @Test
    fun `필요한 만큼만 여러 건 지운다`() {
        val candidates = listOf(
            candidate("c1", 500, 300),
            candidate("c2", 400, 300),
            candidate("c3", 300, 300),
            candidate("c4", 200, 300),
            candidate("c5", 100, 300),
        )
        // 합계 1500MB, 상한 700MB → 오래된 것부터 c1,c2,c3 을 지우면 600MB
        assertEquals(listOf("c1", "c2", "c3"), selectEvictions(candidates, 700 * mb))
    }

    @Test
    fun `입력 순서가 뒤섞여도 시간순으로 지운다`() {
        val candidates = listOf(
            candidate("newest", 1, 600),
            candidate("oldest", 999, 600),
        )
        assertEquals(listOf("oldest"), selectEvictions(candidates, 1024 * mb))
    }

    @Test
    fun `상한보다 큰 파일 하나만 있으면 남긴다`() {
        // 방금 받은 유일한 기록을 곧바로 지워 버리면 사용자가 아무것도 못 본다.
        val candidates = listOf(candidate("huge", 1, 2048))
        assertEquals(emptyList<String>(), selectEvictions(candidates, 1024 * mb))
    }

    @Test
    fun `모두 지워도 상한 아래로 못 내려가면 최신 한 건은 남긴다`() {
        val candidates = listOf(
            candidate("old", 100, 900),
            candidate("new", 10, 900),
        )
        val doomed = selectEvictions(candidates, 100 * mb)
        assertEquals(listOf("old"), doomed)
        assertTrue("최신 항목은 남아야 한다", "new" !in doomed)
    }

    @Test
    fun `빈 목록은 그대로`() {
        assertEquals(emptyList<String>(), selectEvictions(emptyList(), 1024 * mb))
    }
}
