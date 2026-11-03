package com.caregrandpapa.core.util

import kotlinx.coroutines.CancellationException

/** Reports operation failures without swallowing cancellation of the owning job. */
inline fun <T> suspendResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
