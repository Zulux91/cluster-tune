package com.aure.clustertune.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aure.clustertune.model.PerformanceProfile
import com.aure.clustertune.model.ProfileSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "android_tuner")

class ProfileStorage(private val context: Context) {

    private val profilesKey = stringPreferencesKey("user_profiles")
    private val lastValuesKey = stringPreferencesKey("last_values")
    private val initialStockValuesKey = stringPreferencesKey("initial_stock_values")
    private val stockBootIdKey = stringPreferencesKey("stock_boot_id")
    private val selectedProfileKey = stringPreferencesKey("selected_profile")
    private val lastAppliedDisplayProfileKey = stringPreferencesKey("last_applied_display_profile")
    private val deletedBundledProfileIdsKey = stringSetPreferencesKey("deleted_bundled_profile_ids")
    private val displayOrderKey = stringPreferencesKey("display_order")

    val profiles: Flow<List<PerformanceProfile>> = context.dataStore.data.map { preferences ->
        ProfileStorageCodec.parseProfiles(preferences[profilesKey])
    }

    val deletedBundledProfileIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[deletedBundledProfileIdsKey] ?: emptySet()
    }

    val displayOrder: Flow<List<String>> = context.dataStore.data.map { preferences ->
        ProfileStorageCodec.parseStringList(preferences[displayOrderKey])
    }

    val lastValues: Flow<Map<Int, Int>> = context.dataStore.data.map { preferences ->
        ProfileStorageCodec.parseIntMap(preferences[lastValuesKey])
    }

    val initialStockValues: Flow<Map<Int, Int>> = context.dataStore.data.map { preferences ->
        ProfileStorageCodec.parseIntMap(preferences[initialStockValuesKey])
    }

    val stockBootId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[stockBootIdKey]
    }

    val selectedProfileId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[selectedProfileKey]
    }

    val lastAppliedDisplayProfileId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[lastAppliedDisplayProfileKey]
    }

    suspend fun saveProfile(profile: PerformanceProfile) {
        context.dataStore.edit { preferences ->
            val current = ProfileStorageCodec.parseProfiles(preferences[profilesKey]).toMutableList()
            current.removeAll { it.id == profile.id }
            current.add(profile)
            preferences[profilesKey] = ProfileStorageCodec.encodeProfiles(normalizeOrders(current))
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { preferences ->
            val current = ProfileStorageCodec.parseProfiles(preferences[profilesKey]).filterNot { it.id == profileId }
            preferences[profilesKey] = ProfileStorageCodec.encodeProfiles(normalizeOrders(current))
        }
    }

    suspend fun replaceProfiles(profiles: List<PerformanceProfile>) {
        context.dataStore.edit { preferences ->
            preferences[profilesKey] = ProfileStorageCodec.encodeProfiles(
                profiles.mapIndexed { index, profile ->
                    profile.copy(
                        order = index,
                        isEditable = true,
                        isDeletable = profile.source != ProfileSource.VIRTUAL,
                    )
                },
            )
        }
    }

    suspend fun persistDisplayOrder(profileIds: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[displayOrderKey] = ProfileStorageCodec.encodeStringList(profileIds)
        }
    }

    suspend fun resetProfiles() {
        context.dataStore.edit { preferences ->
            preferences.remove(profilesKey)
            preferences.remove(deletedBundledProfileIdsKey)
            preferences.remove(displayOrderKey)
        }
    }

    suspend fun markBundledProfileDeleted(profileId: String) {
        context.dataStore.edit { preferences ->
            val deleted = (preferences[deletedBundledProfileIdsKey] ?: emptySet()).toMutableSet()
            deleted.add(profileId)
            preferences[deletedBundledProfileIdsKey] = deleted
        }
    }

    suspend fun unmarkBundledProfileDeleted(profileId: String) {
        context.dataStore.edit { preferences ->
            val deleted = (preferences[deletedBundledProfileIdsKey] ?: emptySet()).toMutableSet()
            deleted.remove(profileId)
            if (deleted.isEmpty()) {
                preferences.remove(deletedBundledProfileIdsKey)
            } else {
                preferences[deletedBundledProfileIdsKey] = deleted
            }
        }
    }

    suspend fun persistLastValues(values: Map<Int, Int>) {
        context.dataStore.edit { preferences ->
            preferences[lastValuesKey] = ProfileStorageCodec.encodeIntMap(values)
        }
    }

    suspend fun persistInitialStockValues(values: Map<Int, Int>) {
        context.dataStore.edit { preferences ->
            preferences[initialStockValuesKey] = ProfileStorageCodec.encodeIntMap(values)
        }
    }

    suspend fun persistStockBootId(bootId: String) {
        context.dataStore.edit { preferences ->
            preferences[stockBootIdKey] = bootId
        }
    }

    suspend fun persistSelectedProfile(profileId: String?) {
        context.dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(selectedProfileKey)
            } else {
                preferences[selectedProfileKey] = profileId
            }
        }
    }

    suspend fun persistLastAppliedDisplayProfile(profileId: String?) {
        context.dataStore.edit { preferences ->
            if (profileId == null) {
                preferences.remove(lastAppliedDisplayProfileKey)
            } else {
                preferences[lastAppliedDisplayProfileKey] = profileId
            }
        }
    }

    private fun normalizeOrders(profiles: List<PerformanceProfile>): List<PerformanceProfile> {
        return profiles
            .sortedBy { it.order }
            .mapIndexed { index, profile ->
                profile.copy(
                    order = index,
                    isEditable = true,
                    isDeletable = profile.source != ProfileSource.VIRTUAL,
                )
            }
    }
}
