package com.yourapp.gemmatest

import android.app.Application
import com.yourapp.gemmatest.engine.DeviceAuth
import com.yourapp.gemmatest.engine.EngineHolder
import com.yourapp.gemmatest.engine.OnlineChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NovaApplication : Application() {
    val engineHolder = EngineHolder()
    val deviceAuth by lazy { DeviceAuth(this) }
    val onlineChatClient by lazy { OnlineChatClient(deviceAuth) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                engineHolder.warmup()
            } catch (e: Exception) {
                // model missing or init failed — swallow here, getConversation() retries later
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onlineChatClient.registerDeviceIfNeeded()
            } catch (e: Exception) {
                // no network / gateway down at launch — fine, sendMessage's
                // online path retries registration before its first send
            }
        }
    }
}
