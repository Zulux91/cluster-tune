package com.aure.clustertune.data

import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.ProfileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileGeneratorTest {

    private val policies = listOf(
        policy(id = 0, selectableMax = 3_000_000, supported = listOf(1_000_000, 1_500_000, 2_000_000, 2_500_000, 3_000_000)),
        policy(id = 4, selectableMax = 2_000_000, supported = listOf(800_000, 1_200_000, 1_600_000, 2_000_000)),
    )

    @Test
    fun `generates three tiers`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        assertEquals(3, profiles.size)
        assertEquals(listOf("Light Underclock", "Medium Underclock", "Heavy Underclock"), profiles.map { it.name })
    }

    @Test
    fun `all generated frequencies are from supported list`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        val supportedByPolicy = policies.associate { it.id to it.supportedFrequencies.toSet() }
        profiles.forEach { profile ->
            profile.maxFrequencies.forEach { (policyId, freq) ->
                assertTrue("freq $freq not in supported list for policy $policyId", freq in supportedByPolicy[policyId]!!)
            }
        }
    }

    @Test
    fun `source is BUNDLED and profiles are editable and deletable`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        profiles.forEach { profile ->
            assertEquals(ProfileSource.BUNDLED, profile.source)
            assertTrue(profile.isEditable)
            assertTrue(profile.isDeletable)
        }
    }

    @Test
    fun `id contains sanitized soc model`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        assertTrue(profiles.all { it.id.startsWith("bundled_sm8750_") })
    }

    @Test
    fun `heavy tier frequency is lower than light tier`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        val light = profiles.first { it.name == "Light Underclock" }
        val heavy = profiles.first { it.name == "Heavy Underclock" }
        policies.forEach { policy ->
            assertTrue(heavy.maxFrequencies[policy.id]!! <= light.maxFrequencies[policy.id]!!)
        }
    }

    private fun policy(id: Int, selectableMax: Int, supported: List<Int>) = CpuPolicyInfo(
        id = id,
        policyPath = "/sys/devices/system/cpu/cpufreq/policy$id",
        scalingMaxPath = "/sys/devices/system/cpu/cpufreq/policy$id/scaling_max_freq",
        currentMaxFreq = selectableMax,
        selectableMaxFreq = selectableMax,
        observedMaxFreq = selectableMax,
        minFreq = supported.first(),
        supportedFrequencies = supported,
    )
}
