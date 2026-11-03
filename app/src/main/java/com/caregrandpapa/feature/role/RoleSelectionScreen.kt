package com.caregrandpapa.feature.role

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caregrandpapa.data.model.Role
import com.caregrandpapa.R
import com.caregrandpapa.core.ui.IconBadge


/**
 * 앱 최초 진입 시 이 폰이 "간병인(지켜보는 쪽)"인지 "환자(돌봄을 받는 쪽)"인지 고른다.
 */
@Composable
fun RoleSelectionScreen(onRoleSelected: (Role) -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconBadge(iconRes = R.drawable.ic_favorite, size = 72.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "이 휴대폰은 누구의 것인가요?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "한 번만 선택하면 됩니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(36.dp))

            RoleCard(
                iconRes = R.drawable.ic_account_box,
                title = "간병인",
                subtitle = "환자의 소리·영상·위치를 요청해서 확인합니다",
                onClick = { onRoleSelected(Role.GUARDIAN) },
            )

            Spacer(Modifier.height(16.dp))

            RoleCard(
                iconRes = R.drawable.ic_favorite,
                title = "환자",
                subtitle = "간병인이 요청할 때만 이 폰이 녹음·녹화·위치를 보냅니다",
                onClick = { onRoleSelected(Role.PATIENT) },
            )
        }
    }
}

@Composable
private fun RoleCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(iconRes = iconRes, size = 56.dp)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
