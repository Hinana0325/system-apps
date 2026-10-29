package cn.xinxitong.coreui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---- 中性灰白配色（与 PocketVM 灰白主题同一组色值，无暖色） ----
// 状态色仅在有含义的地方使用：绿 = 正常/成功，红 = 失败/删除，灰 = 停用/暂态。

/**  muted 绿色：正常 / 成功。 */
val Success = Color(0xFF4C9A6B)

/** 中性灰：暂态 / 提醒（不用暖色表示警告）。 */
val Warning = Color(0xFF8A8A8A)

/**  muted 红色：失败 / 删除确认。 */
val Danger = Color(0xFFC15B5B)

val HeroGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF3A3A3A),
        Color(0xFF262626),
        Color(0xFF121212),
    ),
)

val ShapeHero = RoundedCornerShape(24.dp)
val ShapeCard = RoundedCornerShape(16.dp)
val ShapeChip = RoundedCornerShape(50.dp)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9CDD3),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFF9AA0A6),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFF2F2F2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
    error = Danger,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF262626),
    scrim = Color(0xFF000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F4756),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E1E1),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF6B7280),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainer = Color(0xFFF1F1F1),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFDEDEDE),
    error = Danger,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE8E8E8),
    scrim = Color(0xFF000000),
)

/**
 * 三个应用共用的主题。调用方传入由 [ThemePreference] 决定好的
 * [darkTheme]，不要各自再判断一次系统主题。
 */
@Composable
fun SystemAppsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(
            displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
            titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp),
        ),
        content = content,
    )
}
