package com.aure.clustertune.data

import com.aure.clustertune.model.CpuPolicyInfo

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
        val cpuIds = parseCpuIds(readText("$policyPath/affected_cpus"))
            .ifEmpty { parseCpuIds(readText("$policyPath/related_cpus")) }
            .ifEmpty { listOf(id) }
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

        return CpuPolicyInfo(
            id = id,
            policyPath = policyPath,
            scalingMaxPath = scalingMaxPath,
            currentMaxFreq = currentMax,
            stockMaxFreq = stockMax,
            minFreq = minFreq,
            supportedFrequencies = supported,
            cpuIds = cpuIds,
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

    internal fun parseCpuIds(raw: String?): List<Int> {
        return raw.orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sorted()
    }

    private fun maxOfNotNull(vararg values: Int?): Int? {
        return values.filterNotNull().maxOrNull()
    }

    private fun readText(path: String): String? {
        return privilegedReader
            .readText(path)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun policyIdOrMax(policyPath: String): Int {
        return policyPath.substringAfterLast('/').removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE
    }
}
