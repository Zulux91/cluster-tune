package com.aure.androidtuner.data

import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.ProfileSource
import org.json.JSONArray
import org.json.JSONObject

object PresetJsonCodec {

    fun encodeProfiles(profiles: List<PerformanceProfile>): String {
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

    fun encodeShareFile(profiles: List<PerformanceProfile>, socModel: String?): String {
        return JSONObject()
            .put("schemaVersion", 1)
            .put("socModel", socModel)
            .put("profiles", JSONArray(encodeProfiles(profiles)))
            .toString(2)
    }

    fun parseProfiles(raw: String?): List<PerformanceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = raw.trim()
            val array = if (root.startsWith("{")) {
                JSONObject(root).optJSONArray("profiles") ?: JSONArray()
            } else {
                JSONArray(root)
            }
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        PerformanceProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            maxFrequencies = parseIntMap(item.get("maxFrequencies").toString()),
                            source = parseSource(item.optString("source", ProfileSource.USER.name)),
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

    fun encodeIntMap(values: Map<Int, Int>): String {
        val json = JSONObject()
        values.toSortedMap().forEach { (key, value) -> json.put(key.toString(), value) }
        return json.toString()
    }

    fun parseIntMap(raw: String?): Map<Int, Int> {
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

    private fun parseSource(raw: String): ProfileSource {
        return runCatching { ProfileSource.valueOf(raw) }.getOrDefault(ProfileSource.USER)
    }
}
