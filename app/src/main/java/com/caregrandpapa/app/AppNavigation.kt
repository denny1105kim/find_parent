package com.caregrandpapa.app


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.caregrandpapa.feature.guardian.ClipPlayerScreen
import com.caregrandpapa.feature.guardian.GuardianHomeScreen
import com.caregrandpapa.feature.guardian.LocationScreen

private object GuardianRoute {
    const val HOME = "home"
    const val LOCATION = "location"
    const val CLIP = "clip"
    const val CLIP_ARG = "requestId"
}

/**
 * 간병인 역할의 화면 이동.
 *
 * navigation-compose 를 쓰는 이유는 목적지마다 자기 ViewModelStore 를 가져,
 * 화면 회전에는 상태가 살아남고 뒤로 나갈 때만 정리되기 때문이다.
 */
@Composable
internal fun GuardianNavHost(pairId: String, onUnpaired: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = GuardianRoute.HOME) {
        composable(GuardianRoute.HOME) {
            GuardianHomeScreen(
                pairId = pairId,
                onOpenLocation = { navController.navigate(GuardianRoute.LOCATION) },
                onOpenClip = { requestId ->
                    navController.navigate("${GuardianRoute.CLIP}/$requestId")
                },
                onUnpaired = onUnpaired,
            )
        }
        composable(GuardianRoute.LOCATION) {
            LocationScreen(pairId = pairId, onBack = { navController.popBackStack() })
        }
        composable(
            route = "${GuardianRoute.CLIP}/{${GuardianRoute.CLIP_ARG}}",
            arguments = listOf(navArgument(GuardianRoute.CLIP_ARG) { type = NavType.StringType }),
        ) { entry ->
            ClipPlayerScreen(
                pairId = pairId,
                requestId = entry.arguments?.getString(GuardianRoute.CLIP_ARG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** 익명 로그인이 끝나기를 기다리는 화면. 실패하면 재시도를 제공한다. */
@Composable
internal fun AuthGateScreen(error: String?, onRetry: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "준비 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "서버에 연결할 수 없습니다",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "인터넷 연결을 확인해 주세요.\n$error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) { Text("다시 시도") }
            }
        }
    }
}
