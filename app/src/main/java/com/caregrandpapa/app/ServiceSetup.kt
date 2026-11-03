package com.caregrandpapa.app

import com.caregrandpapa.BuildConfig
import com.google.firebase.FirebaseApp

object ServiceSetup {
    fun isConfigured(): Boolean = runCatching {
        val options = FirebaseApp.getInstance().options
        options.projectId != "your-firebase-project-id" &&
            options.apiKey.startsWith("AIza") &&
            !options.applicationId.contains("YOUR_") &&
            BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
    }.getOrDefault(false)
}

