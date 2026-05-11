package com.aure.clustertune.data

import android.content.Context
import com.aure.clustertune.model.CpuPolicyInfo
import com.aure.clustertune.model.PerformanceProfile
import com.aure.clustertune.model.ProfileSource
import java.io.File

private fun String.sanitizeAssetFileName(): String {
    return filter { character ->
        character.isLetterOrDigit() || character == '_' || character == '-' || character == '.'
    }
}

private fun assetProfileReader(context: Context): (String) -> String? {
    val appContext = context.applicationContext
    return { socModel ->
        val fileName = "bundled_profiles/${socModel.sanitizeAssetFileName()}.json"
        runCatching {
            appContext.assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

private fun filesDirProfileReader(context: Context): (String) -> String? {
    val appContext = context.applicationContext
    return { socModel ->
        val file = File(appContext.filesDir, "bundled_profiles/${socModel.sanitizeAssetFileName()}.json")
        runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

private fun filesDirProfileWriter(context: Context): (String, String) -> Unit {
    val appContext = context.applicationContext
    return { socModel, json ->
        val dir = File(appContext.filesDir, "bundled_profiles")
        dir.mkdirs()
        File(dir, "${socModel.sanitizeAssetFileName()}.json").writeText(json)
    }
}

class BundledProfileProvider(
    private val readProfileJson: (String) -> String?,
    private val parseProfiles: (String) -> List<PerformanceProfile> = ProfileJsonCodec::parseShareProfiles,
    private val socDetector: SocDetector = SocDetector(),
    private val readFallbackJson: (String) -> String? = { null },
    private val writeFallbackJson: (String, String) -> Unit = { _, _ -> },
) {
    private var cachedSocModel: String? = null
    private var cachedProfiles: List<PerformanceProfile> = emptyList()

    constructor(
        context: Context,
        socDetector: SocDetector = SocDetector(),
    ) : this(
        readProfileJson = assetProfileReader(context),
        readFallbackJson = filesDirProfileReader(context),
        writeFallbackJson = filesDirProfileWriter(context),
        parseProfiles = ProfileJsonCodec::parseShareProfiles,
        socDetector = socDetector,
    )

    fun createProfiles(policies: List<CpuPolicyInfo>): List<PerformanceProfile> {
        val socModel = socDetector.detectSocModel() ?: return emptyList()
        val bundledProfiles = profilesForSoc(socModel)
        val policyIds = policies.associateBy { it.id }

        return bundledProfiles
            .mapIndexed { index, profile ->
                profile.copy(
                    source = ProfileSource.BUNDLED,
                    order = index,
                    isEditable = true,
                    isDeletable = true,
                )
            }
            .filter { profile ->
                !profile.id.endsWith("_stock") &&
                    profile.maxFrequencies.isNotEmpty() &&
                    profile.maxFrequencies.all { (policyId, frequency) ->
                        val policy = policyIds[policyId] ?: return@all false
                        frequency in policy.supportedFrequencies
                    }
            }
    }

    fun currentSocModel(): String? = socDetector.detectSocModel()

    fun generateAndSave(socModel: String, policies: List<CpuPolicyInfo>) {
        val profiles = DeviceProfileGenerator.generate(socModel, policies)
        val json = ProfileJsonCodec.encodeShareFile(profiles, socModel)
        writeFallbackJson(socModel, json)
        cachedSocModel = null
        cachedProfiles = emptyList()
    }

    private fun profilesForSoc(socModel: String): List<PerformanceProfile> {
        if (cachedSocModel == socModel) return cachedProfiles
        val profiles = (readProfileJson(socModel) ?: readFallbackJson(socModel))
            ?.let(parseProfiles)
            .orEmpty()
        cachedSocModel = socModel
        cachedProfiles = profiles
        return profiles
    }
}
