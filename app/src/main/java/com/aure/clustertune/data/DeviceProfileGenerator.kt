package com.aure.clustertune.data

import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.PerformanceProfile
import com.aure.clustertune.model.ProfileSource

internal object DeviceProfileGenerator {

    private data class Tier(val suffix: String, val name: String, val ratio: Double)

    private val tiers = listOf(
        Tier("light", "Light Underclock", 0.85),
        Tier("medium", "Medium Underclock", 0.65),
        Tier("heavy", "Heavy Underclock", 0.50),
    )

    fun generate(socModel: String, policies: List<CpuPolicyInfo>): List<PerformanceProfile> {
        val socId = socModel.lowercase().replace(Regex("[^a-z0-9]"), "_")
        return tiers.mapIndexed { index, tier ->
            val maxFrequencies = policies.associate { policy ->
                val target = (policy.selectableMaxFreq * tier.ratio).toInt()
                val nearest = policy.supportedFrequencies.minByOrNull { kotlin.math.abs(it - target) }
                    ?: policy.selectableMaxFreq
                policy.id to nearest
            }
            PerformanceProfile(
                id = "bundled_${socId}_${tier.suffix}",
                name = tier.name,
                maxFrequencies = maxFrequencies,
                source = ProfileSource.BUNDLED,
                order = index,
                isEditable = true,
                isDeletable = true,
            )
        }
    }
}
