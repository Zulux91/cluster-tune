package com.aure.clustertune.data

import android.util.Log
import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.PerformanceProfile
import com.aure.clustertune.model.ProfileSource
import kotlin.math.abs
import kotlin.math.roundToLong

internal object DeviceProfileGenerator {

    private const val TAG = "DeviceProfileGenerator"
    private const val NEAR_EQUIVALENT_THRESHOLD = 0.05
    private const val MIN_RATIO = 0.30
    private const val MAX_RATIO = 1.00

    private data class Tier(val suffix: String, val name: String, val ratio: Double)

    private val tiers = listOf(
        Tier("light",   "Light Underclock",   0.85),
        Tier("medium",  "Medium Underclock",  0.75),
        Tier("heavy",   "Heavy Underclock",   0.65),
        Tier("extreme", "Extreme Underclock", 0.50),
        Tier("ultra",   "Ultra Underclock",   0.40),
    )

    private data class EffectivePolicy(
        val original: CpuPolicyInfo,
        val effectiveMaxFreq: Int,
        val effectiveSupportedFrequencies: List<Int>,
    )

    private data class RankedPolicy(
        val effective: EffectivePolicy,
        val tierIndex: Int,
        val rankFactor: Double,
    )

    fun frequenciesForPercentage(policies: List<CpuPolicyInfo>, percentage: Int): Map<Int, Int> {
        val ratio = percentage / 100.0
        return policies.associate { policy ->
            val target = (policy.selectableMaxFreq * ratio).toInt()
            val nearest = policy.supportedFrequencies.minByOrNull { kotlin.math.abs(it - target) }
                ?: policy.selectableMaxFreq
            policy.id to nearest
        }
    }

    /*
     * Efficiency profiles are generated from detected CPU performance tiers instead of hardcoded
     * SoC names. Cpufreq policies are ranked by maximum selectable/effective frequency.
     * Lower-ranked tiers keep slightly more headroom, while higher-ranked tiers receive stronger
     * limits.
     *
     * Near-equivalent policies are grouped into ranking tiers using a fixed anchor frequency to
     * avoid chain-merging. This grouping is based on exposed frequency ceilings, not vendor
     * marketing core classes.
     *
     * This allows the same logic to work on classic 3-policy layouts, multi-policy Arm layouts,
     * single-policy devices, and newer 2-policy designs where one policy may contain prime cores
     * and another policy may contain the remaining performance cores.
     */
    fun generate(socModel: String, policies: List<CpuPolicyInfo>): List<PerformanceProfile> {
        val socId = socModel.lowercase().replace(Regex("[^a-z0-9]"), "_")

        // Step 1: Build effective policy data
        val validPolicies = mutableListOf<EffectivePolicy>()
        for (policy in policies) {
            val effectiveMaxFreq = when {
                policy.selectableMaxFreq > 0 -> policy.selectableMaxFreq
                policy.observedMaxFreq > 0 -> policy.observedMaxFreq
                else -> {
                    Log.w(TAG, "Skipping policy ${policy.id}: no valid max frequency " +
                        "(selectableMax=${policy.selectableMaxFreq}, observedMax=${policy.observedMaxFreq})")
                    continue
                }
            }
            val filteredFreqs = policy.supportedFrequencies.filter { it > 0 }.distinct().sorted()
            val effectiveSupportedFreqs = filteredFreqs.ifEmpty { listOf(effectiveMaxFreq) }
            validPolicies += EffectivePolicy(policy, effectiveMaxFreq, effectiveSupportedFreqs)
            Log.d(TAG, "Policy ${policy.id}: effectiveMaxFreq=$effectiveMaxFreq, " +
                "supportedFreqs=${effectiveSupportedFreqs.size} " +
                "[${effectiveSupportedFreqs.first()}..${effectiveSupportedFreqs.last()}]")
        }

        Log.d(TAG, "Valid policies: ${validPolicies.size}/${policies.size}, " +
            "skipped: ${policies.size - validPolicies.size}")

        if (validPolicies.isEmpty()) {
            Log.w(TAG, "No valid policies; returning empty profile list")
            return emptyList()
        }

        // Step 2: Sort deterministically by effectiveMaxFreq, then observedMaxFreq, then id
        val sorted = validPolicies.sortedWith(
            compareBy(
                { it.effectiveMaxFreq },
                { it.original.observedMaxFreq },
                { it.original.id },
            )
        )
        Log.d(TAG, "Sorted policy order: ${sorted.map { it.original.id }}")

        // Step 3: Assign ranking tiers using fixed anchor to prevent chain-merging
        var currentTierIndex = 0
        var tierAnchor = sorted.first().effectiveMaxFreq.toDouble()
        val tierAssignments = mutableListOf<Int>()
        val tierAnchors = mutableListOf(tierAnchor)

        for (ep in sorted) {
            val delta = abs(ep.effectiveMaxFreq.toDouble() - tierAnchor) / tierAnchor
            if (delta > NEAR_EQUIVALENT_THRESHOLD) {
                currentTierIndex++
                tierAnchor = ep.effectiveMaxFreq.toDouble()
                tierAnchors += tierAnchor
            }
            tierAssignments += currentTierIndex
        }
        val tierCount = currentTierIndex + 1

        // Step 4: Assign normalized rankFactor per policy
        val rankedPolicies = sorted.mapIndexed { i, ep ->
            val ti = tierAssignments[i]
            val rankFactor = if (tierCount == 1) 0.0 else ti.toDouble() / (tierCount - 1)
            Log.d(TAG, "Policy ${ep.original.id}: tierIndex=$ti, " +
                "tierAnchor=${tierAnchors[ti].toInt()}, rankFactor=${"%.3f".format(rankFactor)}")
            RankedPolicy(ep, ti, rankFactor)
        }

        val policyCount = validPolicies.size

        // Step 5–7: Build one PerformanceProfile per profile tier
        return tiers.mapIndexed { profileIndex, tier ->
            val targetRatio = tier.ratio
            Log.d(TAG, "--- Profile '${tier.name}' (targetRatio=$targetRatio) ---")

            val maxFrequencies = rankedPolicies.associate { ranked ->
                val ep = ranked.effective
                val adjustedRatio = calcAdjustedRatio(targetRatio, ranked.rankFactor, policyCount, tierCount)
                val rawTarget = ep.effectiveMaxFreq * adjustedRatio
                val rawTargetHz = rawTarget.roundToLong()
                val selected = ep.effectiveSupportedFrequencies.lastOrNull { it.toLong() <= rawTargetHz }
                    ?: ep.effectiveSupportedFrequencies.first()
                Log.d(TAG, "  Policy ${ep.original.id}: adjustedRatio=${"%.3f".format(adjustedRatio)}, " +
                    "rawTarget=${rawTarget.toInt()}, selected=$selected")
                ep.original.id to selected
            }

            PerformanceProfile(
                id = "bundled_${socId}_${tier.suffix}",
                name = tier.name,
                maxFrequencies = maxFrequencies,
                source = ProfileSource.BUNDLED,
                order = profileIndex,
                isEditable = true,
                isDeletable = true,
            )
        }
    }

    private fun calcAdjustedRatio(
        targetRatio: Double,
        rankFactor: Double,
        policyCount: Int,
        tierCount: Int,
    ): Double {
        if (policyCount == 1 || tierCount == 1) return targetRatio
        val (lowBias, highBias) = when {
            targetRatio >= 0.80 -> 0.05 to -0.05
            targetRatio >= 0.65 -> 0.07 to -0.09
            else                -> 0.10 to -0.15
        }
        val bias = lowBias + (highBias - lowBias) * rankFactor
        return (targetRatio + bias).coerceIn(MIN_RATIO, MAX_RATIO)
    }
}
