package com.yourapp.gemmatest

import android.app.Application
import com.yourapp.gemmatest.engine.EngineHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NovaApplication : Application() {
    val engineHolder = EngineHolder()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                engineHolder.warmup()
            } catch (e: Exception) {
                // model missing or init failed — swallow here, getConversation() retries later
            }
        }
    }
}
