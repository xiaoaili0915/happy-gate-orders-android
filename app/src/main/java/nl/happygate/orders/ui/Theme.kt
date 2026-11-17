package nl.happygate.orders.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Red = Color(0xFFB71C1C)
val Green = Color(0xFF2E7D32)
val Amber = Color(0xFFF9A825)
val Grey = Color(0xFF616161)

private val scheme = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    secondary = Green,
    onSecondary = Color.White,
    tertiary = Amber,
    error = Color(0xFFC62828),
)

@Composable
fun HappyGateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
