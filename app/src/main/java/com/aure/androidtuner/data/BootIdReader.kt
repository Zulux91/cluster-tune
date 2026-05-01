package com.aure.androidtuner.data

import java.io.File

class BootIdReader(
    private val bootIdPath: String = "/proc/sys/kernel/random/boot_id",
) {
    fun currentBootId(): String {
        return runCatching {
            File(bootIdPath).readText().trim().ifEmpty { UNKNOWN_BOOT_ID }
        }.getOrDefault(UNKNOWN_BOOT_ID)
    }

    private companion object {
        const val UNKNOWN_BOOT_ID = "unknown"
    }
}
