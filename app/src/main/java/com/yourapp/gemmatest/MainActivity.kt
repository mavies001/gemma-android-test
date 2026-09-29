package com.yourapp.gemmatest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yourapp.gemmatest.engine.ModelDownloader
import com.yourapp.gemmatest.theme.DarkNova
import com.yourapp.gemmatest.theme.LightNova
import com.yourapp.gemmatest.theme.LocalNovaColors
import com.yourapp.gemmatest.ui.NovaApp
import com.yourapp.gemmatest.ui.NovaSplashScreen
import com.yourapp.gemmatest.ui.SetupScreen

private enum class Stage { SETUP, SPLASH, READY }

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val application = applicationContext as NovaApplication
        val downloader = ModelDownloader(this)

        setContent {
            var useSystemTheme by rememberSaveable { mutableStateOf(true) }
            var manualDark by rememberSaveable { mutableStateOf(true) }
            var stage by remember {
                mutableStateOf(if (downloader.isModelPresent()) Stage.SPLASH else Stage.SETUP)
            }
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
                    when (stage) {
                        Stage.SETUP -> SetupScreen(onSetupComplete = { stage = Stage.SPLASH })
                        Stage.SPLASH -> NovaSplashScreen(
                            onFinished = { stage = Stage.READY },
                            warmup = { application.engineHolder.warmup() },
                        )
                        Stage.READY -> NovaApp(
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
}
