package com.yourapp.gemmatest

import android.app.Application
import com.yourapp.gemmatest.engine.DeviceAuth
import com.yourapp.gemmatest.engine.EngineHolder
import com.yourapp.gemmatest.engine.ModelDownloader
import com.yourapp.gemmatest.engine.OnlineChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NovaApplication : Application() {
    val engineHolder by lazy { EngineHolder(this) }
    val deviceAuth by lazy { DeviceAuth(this) }
    val onlineChatClient by lazy { OnlineChatClient(deviceAuth) }
    val modelDownloader by lazy { ModelDownloader(this) }

    override fun onCreate() {
        super.onCreate()
        // no eager engine warmup here anymore — Setup and the splash screen
        // now control warmup explicitly, so it never competes with either
        // screen's first-frame render
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onlineChatClient.registerDeviceIfNeeded()
            } catch (e: Exception) {
                // no network at launch — online sendMessage retries this itself
            }
        }
    }
}
