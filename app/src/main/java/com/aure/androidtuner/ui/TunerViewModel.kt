package com.aure.androidtuner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aure.androidtuner.data.PerformanceRepository
import com.aure.androidtuner.data.SettingsStorage
import com.aure.androidtuner.model.AppColorSource
import com.aure.androidtuner.model.AppSettings
import com.aure.androidtuner.model.CpuPolicyInfo
import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.PresetStateResolver
import com.aure.androidtuner.model.ProfileSource
import com.aure.androidtuner.model.TileInteractionBehavior
import com.aure.androidtuner.model.TunerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class TunerViewModel(
    private val repository: PerformanceRepository,
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    private val edits = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val transientMessage = MutableStateFlow<String?>(null)
    private val transientError = MutableStateFlow<String?>(null)

    val state: StateFlow<TunerState> = combine(
        repository.observeState(),
        edits,
        transientMessage,
        transientError,
    ) { repoState, localEdits, message, error ->
        PresetStateResolver.resolve(
            repoState.copy(
                statusMessage = message,
                errorMessage = error,
            ),
            currentValues = repoState.currentValues + localEdits,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TunerState(),
    )

    val settings: StateFlow<AppSettings> = settingsStorage.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setPolicyValue(policy: CpuPolicyInfo, rawValue: Int) {
        val snapped = snapToSupported(policy, rawValue)
        val updatedEdits = edits.value + (policy.id to snapped)
        edits.value = updatedEdits
        transientMessage.value = null
        transientError.value = null

        val baseValues = state.value.policies.associate { cpuPolicy ->
            cpuPolicy.id to (state.value.actualValues[cpuPolicy.id] ?: cpuPolicy.currentMaxFreq)
        }
        val pendingValues = baseValues + updatedEdits
        val selectedProfile = state.value.displayProfiles
            .firstOrNull { it.id == state.value.selectedProfileId }

        if (selectedProfile != null && !PresetStateResolver.matchesProfile(pendingValues, selectedProfile)) {
            viewModelScope.launch {
                repository.selectProfile(null)
            }
        }
    }

    fun applyProfile(profile: PerformanceProfile) {
        edits.value = edits.value + profile.maxFrequencies
        viewModelScope.launch {
            repository.selectProfile(profile.id.takeUnless { it == PresetStateResolver.STOCK_PROFILE_ID })
        }
    }

    fun clearSelection() {
        viewModelScope.launch {
            repository.selectProfile(null)
        }
    }

    fun applyCurrent(state: TunerState, onApplied: (String) -> Unit = {}) {
        transientMessage.value = null
        transientError.value = null

        viewModelScope.launch {
            val appliedProfile = state.displayProfiles.firstOrNull { profile ->
                PresetStateResolver.matchesProfile(state.currentValues, profile)
            }
            val applyResult = repository.applyValues(
                policies = state.policies,
                selectedValues = state.currentValues,
                isReset = appliedProfile?.id == PresetStateResolver.STOCK_PROFILE_ID,
                appliedDisplayProfileId = appliedProfile?.id ?: PresetStateResolver.MANUAL_PROFILE_ID,
            )
            applyResult.onSuccess { outcome ->
                edits.value = emptyMap()
                transientMessage.value = if (outcome.verificationPassed) {
                    buildAppliedMessage(appliedProfile, outcome.commandOutput)
                } else {
                    buildVerificationFailureMessage(state, outcome.actualValues, outcome.commandOutput)
                }
                if (outcome.verificationPassed) {
                    onApplied(appliedProfile?.name ?: "Manual")
                }
                transientError.value = null
            }.onFailure { throwable ->
                transientError.value = throwable.message ?: "Failed to apply limits"
            }
            if (applyResult.isSuccess) {
                repository.selectProfile(appliedProfile?.id?.takeUnless { it == PresetStateResolver.STOCK_PROFILE_ID })
            }
        }
    }

    fun createUserPreset(name: String, state: TunerState) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            transientError.value = "Preset name is required"
            return
        }
        viewModelScope.launch {
            if (hasDuplicatePresetName(trimmedName, excludedId = null, state = state)) {
                transientError.value = "Preset name already exists"
                return@launch
            }
            repository.createUserPreset(trimmedName, state.currentValues)
            transientMessage.value = "Saved preset \"$trimmedName\""
            transientError.value = null
        }
    }

    fun updateProfile(profileId: String, name: String, state: TunerState) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            transientError.value = "Preset name is required"
            return
        }
        viewModelScope.launch {
            if (hasDuplicatePresetName(trimmedName, excludedId = profileId, state = state)) {
                transientError.value = "Preset name already exists"
                return@launch
            }
            repository.updateProfile(profileId, trimmedName, state.currentValues)
            transientMessage.value = "Updated preset \"$trimmedName\""
            transientError.value = null
        }
    }

    fun deletePreset(profileId: String) {
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            transientMessage.value = "Deleted preset"
            transientError.value = null
        }
    }

    fun moveProfile(profileId: String, offset: Int) {
        viewModelScope.launch {
            repository.moveProfile(profileId, offset)
        }
    }

    fun resetProfilesToDefault() {
        viewModelScope.launch {
            repository.resetProfilesToDefault()
            transientMessage.value = "Restored bundled presets and removed custom presets"
            transientError.value = null
        }
    }

    suspend fun exportProfilesJson(): String {
        return repository.exportProfilesJson()
    }

    suspend fun importProfilesJson(rawJson: String): Int {
        val importedCount = repository.importProfilesJson(rawJson)
        transientMessage.value = if (importedCount == 1) {
            "Imported 1 preset"
        } else {
            "Imported $importedCount presets"
        }
        transientError.value = null
        return importedCount
    }

    fun setTileTapBehavior(behavior: TileInteractionBehavior) {
        viewModelScope.launch {
            settingsStorage.persistTileTapBehavior(behavior)
        }
    }

    fun setTileLongPressBehavior(behavior: TileInteractionBehavior) {
        viewModelScope.launch {
            settingsStorage.persistTileLongPressBehavior(behavior)
        }
    }

    fun setApplyLastPresetOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsStorage.persistApplyLastPresetOnBoot(enabled)
        }
    }

    fun setColorSource(colorSource: AppColorSource) {
        viewModelScope.launch {
            settingsStorage.persistColorSource(colorSource)
        }
    }

    fun setAccentColor(accentColor: Int) {
        viewModelScope.launch {
            settingsStorage.persistAccentColor(accentColor)
        }
    }

    fun refreshStructureState() {
        repository.refreshStructure()
    }

    fun refreshLiveState() {
        repository.refreshLiveValues()
    }

    private fun snapToSupported(policy: CpuPolicyInfo, rawValue: Int): Int {
        return policy.supportedFrequencies.minByOrNull { supported -> abs(supported - rawValue) }
            ?: rawValue
    }

    private fun hasDuplicatePresetName(
        name: String,
        excludedId: String?,
        state: TunerState,
    ): Boolean {
        return state.displayProfiles
            .filter { profile -> profile.source != ProfileSource.VIRTUAL }
            .any { profile ->
                profile.id != excludedId && profile.name.equals(name, ignoreCase = true)
            }
    }

    private fun buildAppliedMessage(
        appliedProfile: PerformanceProfile?,
        commandOutput: String?,
    ): String {
        val base = if (appliedProfile != null) {
            "Applied preset: ${appliedProfile.name}"
        } else {
            "Applied preset: Manual"
        }
        return commandOutput?.takeIf { it.isNotBlank() }?.let { "$base | log: ${it.take(120)}" } ?: base
    }

    private fun buildVerificationFailureMessage(
        state: TunerState,
        actualValues: Map<Int, Int>,
        commandOutput: String?,
    ): String {
        val summary = state.policies.joinToString(", ") { policy ->
            val requested = state.currentValues[policy.id] ?: policy.currentMaxFreq
            val actual = actualValues[policy.id] ?: policy.currentMaxFreq
            "P${policy.id} requested ${formatFrequency(requested)}, actual ${formatFrequency(actual)}"
        }
        val base = "Apply did not stick: $summary"
        return commandOutput?.takeIf { it.isNotBlank() }?.let { "$base | log: ${it.take(120)}" } ?: base
    }

    companion object {
        fun factory(
            repository: PerformanceRepository,
            settingsStorage: SettingsStorage,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TunerViewModel(repository, settingsStorage) as T
                }
            }
        }
    }
}
