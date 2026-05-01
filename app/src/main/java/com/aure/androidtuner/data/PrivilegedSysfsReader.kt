package com.aure.androidtuner.data

fun interface PrivilegedSysfsReader {
    fun readText(path: String): String?
}
