package com.jof.ringtones

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    secondaryContainer = Color(0xFFD3E3FD),
    surface = Color(0xFFFDFCFD),
    background = Color(0xFFFDFCFD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    secondaryContainer = Color(0xFF0842A0),
    surface = Color(0xFF131314),
    background = Color(0xFF131314)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> DarkColors
                else -> LightColors
            }
            MaterialTheme(colorScheme = scheme) {
                Screen()
            }
        }
    }
}
