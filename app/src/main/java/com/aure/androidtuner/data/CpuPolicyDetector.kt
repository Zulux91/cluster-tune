package com.aure.androidtuner.data

import android.util.Log
import com.aure.androidtuner.model.CpuPolicyInfo

class CpuPolicyDetector(
    private val fileSystem: SysfsFileSystem = RealSysfsFileSystem(),
    private val privilegedReader: PrivilegedSysfsReader,
    private val policyRoot: String = "/sys/devices/system/cpu/cpufreq",
) {
    fun detectPolicies(): List<CpuPolicyInfo> {
        return fileSystem.listPolicyDirectories(policyRoot)
            .sortedBy(::policyIdOrMax)
            .mapNotNull(::parsePolicy)
            .sortedBy { it.id }
    }

    fun readCurrentMaxValues(policies: List<CpuPolicyInfo>): Map<Int, Int> {
        return policies.mapNotNull { policy ->
            readText(policy.scalingMaxPath)?.toIntOrNull()?.let { policy.id to it }
        }.toMap()
    }

    private fun parsePolicy(policyPath: String): CpuPolicyInfo? {
        val policyName = policyPath.substringAfterLast('/')
        val id = policyName.removePrefix("policy").toIntOrNull() ?: return null
        val scalingMaxPath = "$policyPath/scaling_max_freq"
        val rawSupported = parseFrequencies(readText("$policyPath/scaling_available_frequencies"))
        val cpuInfoMax = readText("$policyPath/cpuinfo_max_freq")?.toIntOrNull()
        val scalingMax = readText(scalingMaxPath)?.toIntOrNull()
        val minFreq = readText("$policyPath/scaling_min_freq")?.toIntOrNull()
            ?: rawSupported.firstOrNull()
            ?: 0
        val supported = rawSupported
            .let { frequencies ->
                appendExtraTopBins(
                    base = frequencies,
                    extraCandidates = listOfNotNull(scalingMax, cpuInfoMax),
                )
            }
            .ifEmpty {
                buildFallbackFrequencies(
                    minFreq = minFreq,
                    maxFreq = maxOfNotNull(cpuInfoMax, scalingMax) ?: 0,
                    currentMaxFreq = scalingMax ?: cpuInfoMax ?: 0,
                )
            }
        val stockMax = maxOfNotNull(cpuInfoMax, scalingMax, supported.lastOrNull()) ?: return null
        val currentMax = scalingMax ?: supported.lastOrNull() ?: cpuInfoMax ?: stockMax
        logPolicySummary(
            id = id,
            scalingMax = scalingMax,
            cpuInfoMax = cpuInfoMax,
            minFreq = minFreq,
            supported = supported,
            currentMax = currentMax,
            stockMax = stockMax,
        )

        return CpuPolicyInfo(
            id = id,
            policyPath = policyPath,
            scalingMaxPath = scalingMaxPath,
            currentMaxFreq = currentMax,
            stockMaxFreq = stockMax,
            minFreq = minFreq,
            supportedFrequencies = supported,
        )
    }

    internal fun parseFrequencies(raw: String?): List<Int> {
        return raw.orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sorted()
    }

    internal fun buildFallbackFrequencies(
        minFreq: Int,
        maxFreq: Int,
        currentMaxFreq: Int,
    ): List<Int> {
        return listOf(minFreq, currentMaxFreq, maxFreq)
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    internal fun appendExtraTopBins(
        base: List<Int>,
        extraCandidates: List<Int>,
    ): List<Int> {
        if (base.isEmpty()) return emptyList()
        val highestBase = base.last()
        return (base + extraCandidates.filter { it > highestBase })
            .distinct()
            .sorted()
    }

    private fun maxOfNotNull(vararg values: Int?): Int? {
        return values.filterNotNull().maxOrNull()
    }

    private fun readText(path: String): String? {
        val privilegedValue = privilegedReader
            .readText(path)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (privilegedValue != null) {
            logRead(path, "pserver", privilegedValue)
        } else {
            logRead(path, "missing", null)
        }
        return privilegedValue
    }

    private fun logRead(path: String, source: String, value: String?) {
        if (!isDebugLoggingEnabled()) return
        runCatching {
            Log.d(TAG, "read ${path.substringAfterLast('/')} from $source: ${value ?: "<null>"}")
        }
    }

    private fun logPolicySummary(
        id: Int,
        scalingMax: Int?,
        cpuInfoMax: Int?,
        minFreq: Int,
        supported: List<Int>,
        currentMax: Int,
        stockMax: Int,
    ) {
        if (!isDebugLoggingEnabled()) return
        runCatching {
            Log.d(
                TAG,
                "policy$id summary: currentMax=$currentMax stockMax=$stockMax minFreq=$minFreq " +
                    "scalingMax=$scalingMax cpuInfoMax=$cpuInfoMax supported=$supported",
            )
        }
    }

    private fun policyIdOrMax(policyPath: String): Int {
        return policyPath.substringAfterLast('/').removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun isDebugLoggingEnabled(): Boolean {
        return runCatching { Log.isLoggable(TAG, Log.DEBUG) }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "CpuPolicyDetector"
    }
}
