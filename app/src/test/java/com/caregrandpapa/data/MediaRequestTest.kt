package com.caregrandpapa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 요청 상태와 타임아웃 판정.
 *
 * 아버님 폰이 꺼져 있으면 요청 문서가 pending 에서 영원히 멈춘다. 보호자가 그것을
 * "처리 중"으로 계속 보게 되면 안 되므로, 시간 기준 판정이 정확해야 한다.
 */
class MediaRequestTest {

    private val now = 1_700_000_000_000L

    private fun request(
        status: RequestStatus,
        createdSecondsAgo: Long = 0,
        type: RequestType = RequestType.AUDIO,
    ) = MediaRequest(
        id = "r1",
        type = type,
        status = status,
        createdAtMillis = now - createdSecondsAgo * 1000L,
    )

    @Test
    fun `대기 실행 업로드는 진행 중으로 본다`() {
        assertTrue(request(RequestStatus.PENDING).isInFlight)
        assertTrue(request(RequestStatus.RUNNING).isInFlight)
        assertTrue(request(RequestStatus.UPLOADING).isInFlight)
    }

    @Test
    fun `완료 전달 실패는 진행 중이 아니다`() {
        assertFalse(request(RequestStatus.READY).isInFlight)
        assertFalse(request(RequestStatus.DELIVERED).isInFlight)
        assertFalse(request(RequestStatus.FAILED).isInFlight)
    }

    @Test
    fun `대기는 90초를 넘기면 타임아웃`() {
        assertFalse(request(RequestStatus.PENDING, createdSecondsAgo = 89).isTimedOut(now))
        assertFalse(request(RequestStatus.PENDING, createdSecondsAgo = 90).isTimedOut(now))
        assertTrue(request(RequestStatus.PENDING, createdSecondsAgo = 91).isTimedOut(now))
    }

    @Test
    fun `녹음 중에는 더 오래 기다린다`() {
        // 15초 녹음 + 업로드까지 감안해 180초.
        assertFalse(request(RequestStatus.RUNNING, createdSecondsAgo = 120).isTimedOut(now))
        assertTrue(request(RequestStatus.RUNNING, createdSecondsAgo = 181).isTimedOut(now))
        assertFalse(request(RequestStatus.UPLOADING, createdSecondsAgo = 179).isTimedOut(now))
        assertTrue(request(RequestStatus.UPLOADING, createdSecondsAgo = 181).isTimedOut(now))
    }

    @Test
    fun `끝난 요청은 아무리 오래돼도 타임아웃이 아니다`() {
        assertFalse(request(RequestStatus.DELIVERED, createdSecondsAgo = 99_999).isTimedOut(now))
        assertFalse(request(RequestStatus.FAILED, createdSecondsAgo = 99_999).isTimedOut(now))
        assertFalse(request(RequestStatus.READY, createdSecondsAgo = 99_999).isTimedOut(now))
    }

    @Test
    fun `생성 시각을 모르면 타임아웃 판정을 하지 않는다`() {
        val noTimestamp = request(RequestStatus.PENDING).copy(createdAtMillis = null)
        assertFalse(noTimestamp.isTimedOut(now))
    }

    @Test
    fun `요청 종류의 파일 확장자`() {
        assertEquals("m4a", RequestType.AUDIO.fileExtension)
        assertEquals("mp4", RequestType.VIDEO_FRONT.fileExtension)
        assertEquals("mp4", RequestType.VIDEO_BACK.fileExtension)
        assertNull(RequestType.LOCATION.fileExtension)
    }

    @Test
    fun `위치만 클립이 아니다`() {
        assertTrue(RequestType.AUDIO.isClip)
        assertTrue(RequestType.VIDEO_FRONT.isClip)
        assertTrue(RequestType.VIDEO_BACK.isClip)
        assertFalse(RequestType.LOCATION.isClip)
    }

    @Test
    fun `영상 여부는 카메라 요청만`() {
        assertFalse(RequestType.AUDIO.isVideo)
        assertTrue(RequestType.VIDEO_FRONT.isVideo)
        assertTrue(RequestType.VIDEO_BACK.isVideo)
        assertFalse(RequestType.LOCATION.isVideo)
    }

    @Test
    fun `문서 값 왕복 변환`() {
        RequestType.entries.forEach {
            assertEquals(it, RequestType.fromWire(it.wireName))
        }
        RequestStatus.entries.forEach {
            assertEquals(it, RequestStatus.fromWire(it.wireName))
        }
        assertNull(RequestType.fromWire("무엇"))
        assertNull(RequestType.fromWire(null))
        assertNull(RequestStatus.fromWire("무엇"))
    }
}
