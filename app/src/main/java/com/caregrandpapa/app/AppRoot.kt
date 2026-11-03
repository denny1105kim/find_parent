package com.caregrandpapa.app


import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caregrandpapa.data.Auth
import com.caregrandpapa.data.ClipSync
import com.caregrandpapa.data.Prefs
import com.caregrandpapa.feature.pairing.GuardianPairingScreen
import com.caregrandpapa.feature.pairing.PatientPairingScreen
import com.caregrandpapa.feature.patient.PatientHomeScreen
import com.caregrandpapa.data.model.Role
import com.caregrandpapa.feature.role.RoleSelectionScreen

/**
 * 앱 전체 화면 흐름을 담당하는 최상위 컴포저블.
 *
 * 익명 로그인 → 역할 선택 → QR 페어링 → 간병인 홈 / 환자 홈.
 *
 * 역할과 페어 ID 는 [Prefs] 에 영속화되므로 앱을 다시 열거나 폰을 재부팅해도 유지된다.
 * Firestore 보안 규칙이 UID 를 요구하기 때문에, 로그인 전에는 어떤 화면도 띄우지 않는다.
 */
@Composable
fun AppRoot() {
    if (!ServiceSetup.isConfigured()) {
        SetupScreen()
        return
    }
    val context = LocalContext.current
    val session: AppSessionViewModel = viewModel()
    val role = session.role
    val pairId = session.pairId
    var authAttempt by remember { mutableIntStateOf(0) }

    val uid by Auth.uid.collectAsStateWithLifecycle()
    val authError by Auth.lastError.collectAsStateWithLifecycle()

    LaunchedEffect(authAttempt) { Auth.awaitUidOrNull() }

    if (uid == null) {
        AuthGateScreen(error = authError, onRetry = { authAttempt++ })
        return
    }

    val resetRole = { session.selectRole(null) }
    val disconnect = { session.disconnect() }

    when {
        role == null -> RoleSelectionScreen(
            onRoleSelected = {
                session.selectRole(it)
            },
        )

        pairId == null && role == Role.GUARDIAN -> GuardianPairingScreen(
            onPaired = {
                session.pair(it)
            },
            onBack = resetRole,
        )

        pairId == null -> PatientPairingScreen(
            onPaired = {
                session.pair(it)
            },
            onBack = resetRole,
        )

        role == Role.PATIENT -> PatientHomeScreen(
            pairId = pairId!!,
            onUnpaired = disconnect,
        )

        else -> {
            val id = pairId!!
            // 클립 다운로드와 서버 정리는 화면보다 오래 살아야 하므로 앱 수준에서 돌린다.
            LaunchedEffect(id) { ClipSync.start(context, id) }
            GuardianNavHost(
                pairId = id,
                onUnpaired = disconnect,
            )
        }
    }
}
