package com.caregrandpapa.feature.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QR 페어링 문자열 파싱.
 *
 * 엉뚱한 QR(다른 앱의 링크, URL 등)을 찍었을 때 조용히 이상한 페어 문서를 만들지 않는 것이
 * 이 검사의 목적이다.
 */
class PairPayloadTest {

    private val id = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    @Test
    fun `encode 한 값을 decode 하면 같은 ID`() {
        assertEquals(id, PairPayload.decode(PairPayload.encode(id)))
    }

    @Test
    fun `앞뒤 공백은 무시한다`() {
        assertEquals(id, PairPayload.decode("  ${PairPayload.encode(id)}\n"))
    }

    @Test
    fun `다른 스킴은 거부한다`() {
        assertNull(PairPayload.decode("https://example.com/pair?v=1&id=$id"))
        assertNull(PairPayload.decode("otherapp://pair?v=1&id=$id"))
    }

    @Test
    fun `버전이 다르면 거부한다`() {
        assertNull(PairPayload.decode("findparent://pair?v=2&id=$id"))
        assertNull(PairPayload.decode("findparent://pair?id=$id"))
    }

    @Test
    fun `UUID 형태가 아니면 거부한다`() {
        assertNull(PairPayload.decode("findparent://pair?v=1&id=우리집1234"))
        assertNull(PairPayload.decode("findparent://pair?v=1&id="))
        assertNull(PairPayload.decode("findparent://pair?v=1&id=3f2504e0-4f89-11d3-9a0c"))
    }

    @Test
    fun `null 과 빈 문자열은 거부한다`() {
        assertNull(PairPayload.decode(null))
        assertNull(PairPayload.decode(""))
    }

    @Test
    fun `파라미터 순서가 바뀌어도 읽는다`() {
        assertEquals(id, PairPayload.decode("findparent://pair?id=$id&v=1"))
    }

    @Test
    fun `isUuid 는 형식만 본다`() {
        assertTrue(PairPayload.isUuid(id))
        assertTrue(PairPayload.isUuid(id.uppercase()))
        assertFalse(PairPayload.isUuid("not-a-uuid"))
        assertFalse(PairPayload.isUuid("3f2504e0-4f89-11d3-9a0c-0305e82c330g")) // g 는 16진수 아님
    }
}
