package com.caregrandpapa.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Application-lifetime I/O work; UI state updates must explicitly use Main. */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
