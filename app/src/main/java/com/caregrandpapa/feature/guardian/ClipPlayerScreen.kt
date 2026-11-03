package com.caregrandpapa.feature.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.caregrandpapa.R
import com.caregrandpapa.data.ClipStore
import com.caregrandpapa.core.util.KeepScreenOn
import com.caregrandpapa.core.util.formatAge

/**
 * 보관된 클립 재생.
 *
 * 파일은 이 폰의 앱 전용 폴더에 있으므로 네트워크가 없어도 재생된다.
 * 영상과 소리 모두 같은 플레이어로 다룬다(소리만 있는 파일은 화면이 비어 있고 컨트롤만 보인다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipPlayerScreen(pairId: String, requestId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clip = remember(pairId, requestId) { ClipStore(context, pairId).find(requestId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clip?.type?.label ?: "기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "뒤로",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (clip == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "이 기록은 이미 지워졌습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        val player = remember(clip.file.absolutePath) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(clip.file.toURI().toString()))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        if (clip.type.isVideo) KeepScreenOn()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (clip.type.isVideo) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.large),
                )
            } else {
                com.caregrandpapa.core.ui.IconBadge(
                    iconRes = R.drawable.ic_voice,
                    size = 120.dp,
                )
                Spacer(Modifier.height(24.dp))
                PlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            val ageSeconds = ((System.currentTimeMillis() - clip.createdAtMillis) / 1000)
                .coerceAtLeast(0)
            Text(
                text = "${formatAge(ageSeconds)} 요청 · ${formatSize(clip.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "이 파일은 이 폰에만 저장되어 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Media3 의 [PlayerView] 는 아직 @UnstableApi 라 명시적으로 opt-in 한다. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        onRelease = { view -> view.player = null },
    )
}
