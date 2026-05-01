package com.aure.androidtuner.root

import android.content.Context
import com.aure.androidtuner.data.PrivilegedSysfsReader

class PServerSysfsReader(
    private val context: Context,
) : PrivilegedSysfsReader {

    override fun readText(path: String): String? {
        val escapedPath = path.replace("'", "'\\''")
        return RootSupport.runRootCommand(
            context = context,
            command = "cat '$escapedPath' 2>/dev/null",
        )?.trim()?.takeIf { it.isNotEmpty() }
    }
}
