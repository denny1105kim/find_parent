package com.caregrandpapa.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = SurfaceLight,
    primaryContainer = BrandContainerLight,
    onPrimaryContainer = OnBrandContainerLight,
    secondary = TealAccent,
    onSecondary = SurfaceLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = BrandBlueDark,
    background = BgLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = OnBrandContainerLight,
    primaryContainer = BrandContainerDark,
    onPrimaryContainer = OnBrandContainerDark,
    secondary = TealLight,
    onSecondary = OnTealContainerLight,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = BrandBlueLight,
    background = BgDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * 앱 테마. 앱의 색상을 라이트·다크 모드에 일관되게 적용한다.
 */
@Composable
fun CareGrandpapaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
