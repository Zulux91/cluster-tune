package com.aure.androidtuner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.ProfileSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "android_tuner")

class ProfileStorage(private val context: Context) {

    private val profilesKey = stringPreferencesKey("user_profiles")
    private val lastValuesKey = stringPreferencesKey("last_values")
    private val selectedProfileKey = stringPreferencesKey("selected_profile")
    private val deletedBundledProfileIdsKey = stringSetPreferencesKey("deleted_bundled_profile_ids")
    private val displayOrderKey = stringPreferencesKey("display_order")

    val profiles: Flow<List<PerformanceProfile>> = context.dataStore.data.map { preferences ->
        parseProfiles(preferences[profilesKey])
    }

    val deletedBundledProfileIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[deletedBundledProfileIdsKey] ?: emptySet()
    }

    val displayOrder: Flow<List<String>> = context.dataStore.data.map { preferences ->
        parseStringList(preferences[displayOrderKey])
    }

    val lastValues: Flow<Map<Int, Int>> = context.dataStore.data.map { preferences ->
        parseIntMap(preferences[lastValuesKey])
    }

    val selectedProfileId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[selectedProfileKey]
    }

    suspend fun saveProfile(profile: PerformanceProfile) {
        context.dataStore.edit { preferences ->
            val current = parseProfiles(preferences[profilesKey]).toMutableList()
            current.removeAll { it.id == profile.id }
            current.add(profile)
            preferences[profilesKey] = encodeProfiles(normalizeOrders(current))
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { preferences ->
            val current = parseProfiles(preferences[profilesKey]).filterNot { it.id == profileId }
            preferences[profilesKey] = encodeProfiles(normalizeOrders(current))
        }
    }

    suspend fun replaceProfiles(profiles: List<PerformanceProfile>) {
        context.dataStore.edit { preferences ->
            preferences[profilesKey] = encodeProfiles(
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
            preferences[displayOrderKey] = encodeStringList(profileIds)
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
            preferences[lastValuesKey] = encodeIntMap(values)
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

    private fun encodeProfiles(profiles: List<PerformanceProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("source", profile.source.name)
                    .put("isResetProfile", profile.isResetProfile)
                    .put("order", profile.order)
                    .put("isEditable", profile.isEditable)
                    .put("isDeletable", profile.isDeletable)
                    .put("maxFrequencies", JSONObject(encodeIntMap(profile.maxFrequencies))),
            )
        }
        return array.toString()
    }

    private fun parseProfiles(raw: String?): List<PerformanceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        PerformanceProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            maxFrequencies = parseIntMap(item.get("maxFrequencies").toString()),
                            source = ProfileSource.valueOf(item.optString("source", ProfileSource.USER.name)),
                            isResetProfile = item.optBoolean("isResetProfile", false),
                            order = item.optInt("order", index),
                            isEditable = item.optBoolean("isEditable", true),
                            isDeletable = item.optBoolean("isDeletable", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList()).sortedBy { it.order }
    }

    private fun encodeIntMap(values: Map<Int, Int>): String {
        val json = JSONObject()
        values.toSortedMap().forEach { (key, value) -> json.put(key.toString(), value) }
        return json.toString()
    }

    private fun encodeStringList(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    private fun parseIntMap(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val intKey = key.toIntOrNull() ?: continue
                    val value = json.optInt(key)
                    if (value > 0) {
                        put(intKey, value)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
        }.getOrDefault(emptyList())
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
