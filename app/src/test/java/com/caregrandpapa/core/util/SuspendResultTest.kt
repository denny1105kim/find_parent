package com.caregrandpapa.core.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SuspendResultTest {
    @Test fun `operation failures remain available to caller`() {
        val failure = IllegalStateException("offline")
        assertSame(failure, suspendResult<Unit> { throw failure }.exceptionOrNull())
    }
    @Test fun `stopping work is never converted into a normal failure`() {
        val cancellation = CancellationException("sharing stopped")
        try {
            suspendResult<Unit> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }
}
