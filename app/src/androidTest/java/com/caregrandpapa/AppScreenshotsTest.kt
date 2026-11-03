package com.caregrandpapa

import com.caregrandpapa.app.SetupScreen
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.caregrandpapa.data.PairInfo
import com.caregrandpapa.feature.guardian.GuardianHomeContent
import com.caregrandpapa.feature.patient.PatientStatusScreen
import com.caregrandpapa.core.ui.theme.CareGrandpapaTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Documentation fixtures only: no real account, network calls, or patient data. */
class AppScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun guardian() {
        val now = 1_800_000_000_000L
        val pair = PairInfo("demo", "family", "patient", null, true, "okay", now - 120_000, 72, true, now - 60_000)
        compose.setContent { CareGrandpapaTheme(darkTheme = false) {
            GuardianHomeContent(pair, emptyList(), emptyList(), 0L, null, null, now, {}, {}, {}, {}, {}, {}, {})
        } }
        capture("guardian")
    }
    @Test fun patient() {
        compose.setContent { CareGrandpapaTheme(darkTheme = false) {
            PatientStatusScreen("00000000-0000-4000-8000-000000000000", true, true, true, true, true, null, {}, {}, {}, {}, {}, {}, {})
        } }
        capture("patient")
    }
    @Test fun setup() {
        compose.setContent { CareGrandpapaTheme(darkTheme = false) { SetupScreen() } }
        capture("setup")
    }
}
