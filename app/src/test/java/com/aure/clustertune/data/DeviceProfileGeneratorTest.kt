package com.aure.clustertune.data

import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.ProfileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileGeneratorTest {

    // 2-policy baseline used by existing tests (low: id=4 max=2M, high: id=0 max=3M)
    private val policies = listOf(
        policy(id = 0, selectableMax = 3_000_000, supported = listOf(1_000_000, 1_500_000, 2_000_000, 2_500_000, 3_000_000)),
        policy(id = 4, selectableMax = 2_000_000, supported = listOf(800_000, 1_200_000, 1_600_000, 2_000_000)),
    )

    // ── Existing tests ────────────────────────────────────────────────────────

    @Test
    fun `generates five tiers`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        assertEquals(5, profiles.size)
        assertEquals(
            listOf("Light Underclock", "Medium Underclock", "Heavy Underclock", "Extreme Underclock", "Ultra Underclock"),
            profiles.map { it.name },
        )
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

    // ── Single-policy device ──────────────────────────────────────────────────

    @Test
    fun `single-policy device uses targetRatio directly without bias`() {
        // policyCount==1 → adjustedRatio == targetRatio (no bias of any kind).
        // Light target=0.85: rawTarget=1_700_000, selected=1_700_000.
        // A spurious low-tier bias (+0.05) would produce 0.90*2M=1_800_000 → different result.
        val singlePolicy = listOf(
            policy(id = 0, selectableMax = 2_000_000, supported = listOf(1_000_000, 1_700_000, 1_800_000, 2_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", singlePolicy)
        val light = profiles.first { it.name == "Light Underclock" }
        assertEquals(1_700_000, light.maxFrequencies[0])  // not 1_800_000 (biased) or 1_000_000
    }

    // ── Two-policy device ─────────────────────────────────────────────────────

    @Test
    fun `two-policy device lower tier keeps more headroom higher tier gets stronger limit`() {
        // policy0 (max=1.2M) → tier0 rankFactor=0.0 → Light adjustedRatio=0.90
        // policy4 (max=3.0M) → tier1 rankFactor=1.0 → Light adjustedRatio=0.80
        val twoPolicies = listOf(
            policy(id = 0, selectableMax = 1_200_000, supported = listOf(960_000, 1_080_000, 1_200_000)),
            policy(id = 4, selectableMax = 3_000_000, supported = listOf(2_400_000, 2_700_000, 3_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", twoPolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        assertEquals(1_080_000, light.maxFrequencies[0])  // 0.90 * 1.2M = 1.08M
        assertEquals(2_400_000, light.maxFrequencies[4])  // 0.80 * 3.0M = 2.4M
    }

    // ── Three-policy device ───────────────────────────────────────────────────

    @Test
    fun `three-policy classic layout assigns correct tier factors`() {
        // little (1M, rf=0.0), perf (2M, rf=0.5), prime (3M, rf=1.0)
        // Light adjustedRatios: 0.90 / 0.85 / 0.80
        val threePolicies = listOf(
            policy(id = 0, selectableMax = 1_000_000, supported = listOf(700_000, 850_000, 1_000_000)),
            policy(id = 4, selectableMax = 2_000_000, supported = listOf(1_400_000, 1_700_000, 2_000_000)),
            policy(id = 7, selectableMax = 3_000_000, supported = listOf(2_100_000, 2_550_000, 3_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", threePolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        assertEquals(850_000,   light.maxFrequencies[0])  // highest ≤ 0.90*1M=900K
        assertEquals(1_700_000, light.maxFrequencies[4])  // highest ≤ 0.85*2M=1.7M
        assertEquals(2_100_000, light.maxFrequencies[7])  // highest ≤ 0.80*3M=2.4M
    }

    // ── Four-policy device ────────────────────────────────────────────────────

    @Test
    fun `four-policy device assigns four distinct tiers`() {
        // 4 tiers: rf = 0.0, 1/3, 2/3, 1.0
        // Light adjustedRatios (target=0.85, band>=0.80, low=+0.05, high=-0.05):
        //   rf=0.0  → 0.90 * 1.0M  = 900K   → 900K
        //   rf=1/3  → 0.8667* 1.8M = 1560K  → 1530K
        //   rf=2/3  → 0.8333* 2.5M = 2083K  → 1750K
        //   rf=1.0  → 0.80 * 3.5M  = 2800K  → 2800K
        val fourPolicies = listOf(
            policy(id = 0, selectableMax = 1_000_000, supported = listOf(700_000, 900_000, 1_000_000)),
            policy(id = 4, selectableMax = 1_800_000, supported = listOf(1_260_000, 1_530_000, 1_800_000)),
            policy(id = 6, selectableMax = 2_500_000, supported = listOf(1_750_000, 2_250_000, 2_500_000)),
            policy(id = 7, selectableMax = 3_500_000, supported = listOf(2_450_000, 2_800_000, 3_500_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", fourPolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        assertEquals(900_000,   light.maxFrequencies[0])
        assertEquals(1_530_000, light.maxFrequencies[4])
        assertEquals(1_750_000, light.maxFrequencies[6])
        assertEquals(2_800_000, light.maxFrequencies[7])
    }

    // ── Near-equivalent policies ──────────────────────────────────────────────

    @Test
    fun `near-equivalent policies within 5pct are assigned the same ranking tier`() {
        // delta = |2080-2000|/2000 = 0.04 <= 0.05 → same tier, tierCount=1
        // tierCount=1 → adjustedRatio = targetRatio regardless of policyCount.
        // If wrongly split into 2 tiers: policy4 rf=1.0 → adj=0.80 → rawTarget=1.664M → selected=1_664_000.
        // Correct (same tier): adj=0.85 → rawTarget=1.768M → selected=1_768_000.
        val nearEqPolicies = listOf(
            policy(id = 0, selectableMax = 2_000_000, supported = listOf(1_600_000, 1_700_000, 1_800_000, 2_000_000)),
            policy(id = 4, selectableMax = 2_080_000, supported = listOf(1_664_000, 1_768_000, 1_872_000, 2_080_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", nearEqPolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        // targetRatio=0.85 applied directly (tierCount=1)
        assertEquals(1_700_000, light.maxFrequencies[0])  // highest ≤ 0.85*2.00M = 1.700M
        assertEquals(1_768_000, light.maxFrequencies[4])  // highest ≤ 0.85*2.08M = 1.768M
    }

    // ── Chain-merge prevention ────────────────────────────────────────────────

    @Test
    fun `chain-merge prevention - six near-adjacent policies produce three tiers not one`() {
        // Fixed anchors prevent chain-merging:
        //   anchor=2000: p1(2080)→Δ=4%≤5%→tier0; p2(2160)→Δ=8%>5%→tier1 anchor=2160
        //   p3(2240)→Δ=3.7%≤5%→tier1; p4(2320)→Δ=7.4%>5%→tier2 anchor=2320
        //   p5(2400)→Δ=3.4%≤5%→tier2
        // 3 tiers: rf = 0.0 / 0.5 / 1.0
        // Light (0.85): p5 rf=1.0 → adj=0.80 → rawTarget=1920K → selected=1920K
        // Without chain-merge fix (1 tier): adj=0.85 → rawTarget=2040K → selected=2040K
        val chainPolicies = listOf(
            policy(id = 0, selectableMax = 2_000_000, supported = listOf(1_800_000, 2_000_000)),
            policy(id = 1, selectableMax = 2_080_000, supported = listOf(1_872_000, 2_080_000)),
            policy(id = 2, selectableMax = 2_160_000, supported = listOf(1_836_000, 1_944_000, 2_160_000)),
            policy(id = 3, selectableMax = 2_240_000, supported = listOf(1_904_000, 2_016_000, 2_240_000)),
            policy(id = 4, selectableMax = 2_320_000, supported = listOf(1_856_000, 2_088_000, 2_320_000)),
            policy(id = 5, selectableMax = 2_400_000, supported = listOf(1_920_000, 2_040_000, 2_400_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", chainPolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        // tier0 (rf=0.0) adj=0.90: p0 rawTarget=1800K → selected=1800K
        assertEquals(1_800_000, light.maxFrequencies[0])
        // tier2 (rf=1.0) adj=0.80: p5 rawTarget=1920K → selected=1920K (not 2040K)
        assertEquals(1_920_000, light.maxFrequencies[5])
    }

    // ── Deterministic sort ────────────────────────────────────────────────────

    @Test
    fun `deterministic sort - equal effectiveMaxFreq resolved by observedMaxFreq then id`() {
        // Two policies with same selectableMaxFreq → same tier → tierCount=1 → no bias.
        // Both get adjustedRatio = targetRatio.
        val equalMaxPolicies = listOf(
            policy(id = 7, selectableMax = 2_000_000, supported = listOf(1_600_000, 1_700_000, 2_000_000)),
            policy(id = 3, selectableMax = 2_000_000, supported = listOf(1_600_000, 1_700_000, 2_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", equalMaxPolicies)
        val light = profiles.first { it.name == "Light Underclock" }
        // Both get targetRatio=0.85 → rawTarget=1.7M → selected=1.7M each
        assertEquals(1_700_000, light.maxFrequencies[3])
        assertEquals(1_700_000, light.maxFrequencies[7])
        // Reversing input order must produce identical output
        val reversed = DeviceProfileGenerator.generate("test", equalMaxPolicies.reversed())
        assertEquals(light.maxFrequencies, reversed.first { it.name == "Light Underclock" }.maxFrequencies)
    }

    // ── Frequency selection semantics ─────────────────────────────────────────

    @Test
    fun `selected frequency is highest at or below rawTarget never nearest above`() {
        // policy4 (high tier, rf=1.0) Light: adj=0.80, rawTarget=800K
        // freqs=[600K, 900K, 1M]: nearest to 800K would be 900K (above!) — must select 600K
        val twoPolicies = listOf(
            policy(id = 0, selectableMax = 500_000,   supported = listOf(400_000, 500_000)),
            policy(id = 4, selectableMax = 1_000_000, supported = listOf(600_000, 900_000, 1_000_000)),
        )
        val light = DeviceProfileGenerator.generate("test", twoPolicies).first { it.name == "Light Underclock" }
        // adj=0.80 → rawTarget=800K → 900K is ABOVE → must select 600K
        assertEquals(600_000, light.maxFrequencies[4])
    }

    @Test
    fun `generated frequency never exceeds effectiveMaxFreq`() {
        val profiles = DeviceProfileGenerator.generate("SM8750", policies)
        val maxByPolicy = policies.associate { it.id to it.selectableMaxFreq }
        profiles.forEach { profile ->
            profile.maxFrequencies.forEach { (policyId, freq) ->
                assertTrue("freq $freq exceeds max for policy $policyId", freq <= maxByPolicy[policyId]!!)
            }
        }
    }

    // ── Extreme vs Ultra distinguishability ───────────────────────────────────

    @Test
    fun `extreme and ultra remain distinguishable on highest-ranked tier`() {
        // policy4 (max=10M, rf=1.0):
        //   Extreme (0.50): adj=0.50+(0.10-0.25*1.0)=0.35 → rawTarget=3.5M → selected=3.5M
        //   Ultra   (0.40): adj=0.40+(0.10-0.25*1.0)=0.25→clamped 0.30 → rawTarget=3.0M → selected=3.0M
        val highRangePolicies = listOf(
            policy(id = 0, selectableMax = 2_000_000,  supported = listOf(1_000_000, 2_000_000)),
            policy(id = 4, selectableMax = 10_000_000, supported = listOf(3_000_000, 3_500_000, 4_000_000, 6_000_000, 10_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", highRangePolicies)
        val extreme = profiles.first { it.name == "Extreme Underclock" }
        val ultra   = profiles.first { it.name == "Ultra Underclock" }
        assertEquals(3_500_000, extreme.maxFrequencies[4])
        assertEquals(3_000_000, ultra.maxFrequencies[4])
        assertTrue(extreme.maxFrequencies[4]!! > ultra.maxFrequencies[4]!!)
    }

    // ── Policy ID coverage ────────────────────────────────────────────────────

    @Test
    fun `generated profiles include every valid detected policy id`() {
        val validPolicies = listOf(
            policy(id = 0, selectableMax = 1_000_000, supported = listOf(800_000, 1_000_000)),
            policy(id = 4, selectableMax = 2_000_000, supported = listOf(1_600_000, 2_000_000)),
            policy(id = 7, selectableMax = 3_000_000, supported = listOf(2_400_000, 3_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", validPolicies)
        profiles.forEach { profile ->
            assertTrue(0 in profile.maxFrequencies)
            assertTrue(4 in profile.maxFrequencies)
            assertTrue(7 in profile.maxFrequencies)
        }
    }

    // ── Empty supportedFrequencies fallback ───────────────────────────────────

    @Test
    fun `empty supportedFrequencies fallback - policy present in profiles using effectiveMaxFreq`() {
        // policy0 has no freq table; effectiveSupportedFreqs = [1_500_000].
        // For any adjustedRatio < 1.0, rawTarget < 1.5M, so fallback returns first()=1.5M.
        val mixedPolicies = listOf(
            policy(id = 0, selectableMax = 1_500_000, supported = emptyList()),
            policy(id = 4, selectableMax = 3_000_000, supported = listOf(2_400_000, 2_700_000, 3_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", mixedPolicies)
        profiles.forEach { profile ->
            assertTrue("policy 0 missing from ${profile.name}", 0 in profile.maxFrequencies)
            assertEquals("policy 0 should always return effectiveMaxFreq when freq table is empty",
                1_500_000, profile.maxFrequencies[0])
        }
    }

    // ── Invalid policy skip ───────────────────────────────────────────────────

    @Test
    fun `invalid policy is skipped and remaining valid policies are still generated`() {
        val mixedPolicies = listOf(
            policy(id = 0, selectableMax = 0, observedMax = 0, supported = emptyList()),
            policy(id = 4, selectableMax = 2_000_000, supported = listOf(1_600_000, 2_000_000)),
        )
        val profiles = DeviceProfileGenerator.generate("test", mixedPolicies)
        assertEquals(5, profiles.size)
        profiles.forEach { profile ->
            assertFalse("invalid policy 0 should be absent", 0 in profile.maxFrequencies)
            assertTrue("valid policy 4 should be present", 4 in profile.maxFrequencies)
        }
    }

    // ── Effective frequency source preference ─────────────────────────────────

    @Test
    fun `selectableMaxFreq is preferred over observedMaxFreq when both are positive`() {
        // effectiveMaxFreq = 3M (selectableMax), not 4M (observedMax).
        // Single policy → adjustedRatio = targetRatio = 0.85.
        // rawTarget = 3M * 0.85 = 2.55M → selected = 2_550_000.
        // If 4M were used: rawTarget = 3.4M → selected = 3_000_000 (different).
        val singlePolicy = listOf(
            policy(id = 0, selectableMax = 3_000_000, observedMax = 4_000_000,
                   supported = listOf(2_100_000, 2_550_000, 3_000_000)),
        )
        val light = DeviceProfileGenerator.generate("test", singlePolicy).first { it.name == "Light Underclock" }
        assertEquals(2_550_000, light.maxFrequencies[0])
    }

    @Test
    fun `observedMaxFreq is used as fallback when selectableMaxFreq is zero`() {
        // selectableMaxFreq=0 → effectiveMaxFreq = observedMaxFreq = 2M.
        // Single policy, Light adjustedRatio=0.85 → rawTarget=1.7M.
        val singlePolicy = listOf(
            policy(id = 0, selectableMax = 0, observedMax = 2_000_000,
                   supported = listOf(1_400_000, 1_700_000, 2_000_000)),
        )
        val light = DeviceProfileGenerator.generate("test", singlePolicy).first { it.name == "Light Underclock" }
        assertEquals(1_700_000, light.maxFrequencies[0])
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun policy(
        id: Int,
        selectableMax: Int = 0,
        observedMax: Int = selectableMax,
        supported: List<Int> = emptyList(),
    ) = CpuPolicyInfo(
        id = id,
        policyPath = "/sys/devices/system/cpu/cpufreq/policy$id",
        scalingMaxPath = "/sys/devices/system/cpu/cpufreq/policy$id/scaling_max_freq",
        currentMaxFreq = selectableMax,
        selectableMaxFreq = selectableMax,
        observedMaxFreq = observedMax,
        minFreq = supported.firstOrNull() ?: 0,
        supportedFrequencies = supported,
    )
}
