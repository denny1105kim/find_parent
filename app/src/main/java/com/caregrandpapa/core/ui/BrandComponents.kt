package com.caregrandpapa.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 앱 전역 공용 UI 조각. 화면마다 같은 톤을 유지하기 위해 한곳에 모은다.

/** 상태 표시등 색. */
val DotReady = Color(0xFF2E9E5B)
val DotWarn = Color(0xFFE0A100)

/**
 * 원형 아이콘 배지. 기본은 브랜드 컨테이너(연한 파랑) 위에 틴트 아이콘.
 * [container]/[tint] 를 주면 색을 바꿀 수 있다.
 */
@Composable
fun IconBadge(
    iconRes: Int,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
