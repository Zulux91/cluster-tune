package com.aure.androidtuner.data

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

open class SocDetector {

    open fun detectSocModel(): String? {
        val candidates = listOf(
            readProperty("ro.soc.model"),
            readProperty("ro.vendor.qti.soc_model"),
            readProperty("ro.fota.platform"),
            Build.SOC_MODEL,
            Build.HARDWARE,
            Build.BOARD,
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun readProperty(name: String): String? {
        return runCatching {
            val process = ProcessBuilder("getprop", name)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor()
            output.trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
