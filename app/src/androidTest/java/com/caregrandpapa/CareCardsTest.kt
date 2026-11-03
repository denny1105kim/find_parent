package com.caregrandpapa

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.caregrandpapa.core.ui.CheckInCard
import com.caregrandpapa.core.ui.FamilyCareCard
import com.caregrandpapa.core.ui.theme.CareGrandpapaTheme
import org.junit.Rule
import org.junit.Test

class CareCardsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun helpRequiresExplicitConfirmation() {
        compose.setContent { CareGrandpapaTheme { CheckInCard("demo-pair") } }
        compose.onNodeWithText("도움이 필요해요").performClick()
        compose.onNodeWithText("도움이 필요하신가요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("괜찮아요").assertIsDisplayed()
    }

    @Test fun missingTelemetryDoesNotClaimOnline() {
        compose.setContent { CareGrandpapaTheme { FamilyCareCard(null, 0L) } }
        compose.onNodeWithText("첫 안부를 기다리고 있어요").assertIsDisplayed()
        compose.onNodeWithText("확인 전").assertIsDisplayed()
        compose.onNodeWithText("아직 없음").assertIsDisplayed()
    }
}
