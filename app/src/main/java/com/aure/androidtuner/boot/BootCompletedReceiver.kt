package com.aure.androidtuner.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aure.androidtuner.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(context)
                val settings = container.settingsStorage.settings.first()
                if (!settings.applyLastPresetOnBoot) {
                    Log.d(TAG, "Boot apply skipped: setting disabled")
                    return@launch
                }
                val result = container.repository.applyPersistedLastValuesOnBoot()
                result
                    .onSuccess { outcome ->
                        Log.d(
                            TAG,
                            "Boot apply finished: verificationPassed=${outcome.verificationPassed}",
                        )
                    }
                    .onFailure { throwable ->
                        Log.w(TAG, "Boot apply failed", throwable)
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
