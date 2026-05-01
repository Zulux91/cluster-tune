package com.aure.androidtuner.data

import android.content.Context
import com.aure.androidtuner.model.CpuPolicyInfo
import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.ProfileSource

private fun String.sanitizeAssetFileName(): String {
    return filter { character ->
        character.isLetterOrDigit() || character == '_' || character == '-' || character == '.'
    }
}

private fun assetPresetReader(context: Context): (String) -> String? {
    val appContext = context.applicationContext
    return { socModel ->
        val fileName = "bundled_presets/${socModel.sanitizeAssetFileName()}.json"
        runCatching {
            appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

class BundledPresetProvider(
    private val readPresetJson: (String) -> String?,
    private val parsePresetProfiles: (String) -> List<PerformanceProfile> = PresetJsonCodec::parseProfiles,
    private val socDetector: SocDetector = SocDetector(),
) {
    constructor(
        context: Context,
        socDetector: SocDetector = SocDetector(),
    ) : this(
        readPresetJson = assetPresetReader(context),
        parsePresetProfiles = PresetJsonCodec::parseProfiles,
        socDetector = socDetector,
    )

    fun createProfiles(policies: List<CpuPolicyInfo>): List<PerformanceProfile> {
        val socModel = socDetector.detectSocModel() ?: return emptyList()
        val rawJson = readPresetJson(socModel) ?: return emptyList()
        val policyIds = policies.associateBy { it.id }

        return parsePresetProfiles(rawJson)
            .mapIndexed { index, profile ->
                profile.copy(
                    source = ProfileSource.BUNDLED,
                    order = index,
                    isEditable = true,
                    isDeletable = true,
                )
            }
            .filter { profile ->
                profile.maxFrequencies.isNotEmpty() &&
                    profile.maxFrequencies.all { (policyId, frequency) ->
                        val policy = policyIds[policyId] ?: return@all false
                        frequency in policy.supportedFrequencies || frequency == policy.stockMaxFreq
                    }
            }
    }

    fun currentSocModel(): String? = socDetector.detectSocModel()
}
