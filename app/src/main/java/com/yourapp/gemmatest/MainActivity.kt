package com.yourapp.gemmatest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.yourapp.gemmatest.theme.DarkNova
import com.yourapp.gemmatest.theme.LightNova
import com.yourapp.gemmatest.theme.LocalNovaColors
import com.yourapp.gemmatest.ui.NovaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            var useSystemTheme by rememberSaveable { mutableStateOf(true) }
            var manualDark by rememberSaveable { mutableStateOf(true) }
            val systemDark = isSystemInDarkTheme()
            val isDark = if (useSystemTheme) systemDark else manualDark
            val colors = if (isDark) DarkNova else LightNova

            val materialScheme = if (isDark) {
                darkColorScheme(background = colors.Bg0, surface = colors.Surface)
            } else {
                lightColorScheme(background = colors.Bg0, surface = colors.Surface)
            }

            MaterialTheme(colorScheme = materialScheme) {
                CompositionLocalProvider(LocalNovaColors provides colors) {
                    NovaApp(
                        isDark = isDark,
                        onToggleTheme = {
                            useSystemTheme = false
                            manualDark = !isDark
                        }
                    )
                }
            }
        }
    }
}
