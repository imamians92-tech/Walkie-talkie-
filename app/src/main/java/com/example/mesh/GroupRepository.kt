package com.example.mesh

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.walkieDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "walkie_radar_prefs"
)

data class PersistedGroupState(
    val operatorCallsign: String = "ALPHA-LEAD",
    val localSlotNumber: Int = 1,
    val selectedChannelNumber: Int = 1,
    val scanPowerMode: ScanPowerMode = ScanPowerMode.BALANCED,
    val isBroadcastEnabled: Boolean = true,
    val enrolledDevices: List<EnrolledDeviceRecord> = emptyList()
)

class GroupRepository(private val context: Context) {

    private object Keys {
        val CALLSIGN = stringPreferencesKey("operator_callsign")
        val LOCAL_SLOT = intPreferencesKey("local_slot_number")
        val CHANNEL = intPreferencesKey("selected_channel")
        val SCAN_MODE = stringPreferencesKey("scan_power_mode")
        val BROADCAST_ENABLED = booleanPreferencesKey("broadcast_enabled")
        val ENROLLED_JSON = stringPreferencesKey("enrolled_devices_json")
    }

    val groupState: Flow<PersistedGroupState> = context.walkieDataStore.data.map { prefs ->
        val mode = prefs[Keys.SCAN_MODE]?.let { name ->
            ScanPowerMode.entries.firstOrNull { it.name == name }
        } ?: ScanPowerMode.BALANCED

        PersistedGroupState(
            operatorCallsign = prefs[Keys.CALLSIGN] ?: "ALPHA-LEAD",
            localSlotNumber = (prefs[Keys.LOCAL_SLOT] ?: 1).coerceIn(1, MAX_GROUP_DEVICES),
            selectedChannelNumber = (prefs[Keys.CHANNEL] ?: 1).coerceIn(1, DEFAULT_RADIO_CHANNELS.size),
            scanPowerMode = mode,
            isBroadcastEnabled = prefs[Keys.BROADCAST_ENABLED] ?: true,
            enrolledDevices = decodeDevices(prefs[Keys.ENROLLED_JSON])
        )
    }

    suspend fun saveEnrolledDevices(records: List<EnrolledDeviceRecord>) {
        context.walkieDataStore.edit { prefs ->
            prefs[Keys.ENROLLED_JSON] = encodeDevices(records.take(MAX_GROUP_DEVICES))
        }
    }

    suspend fun setSelectedChannel(channelNumber: Int) {
        context.walkieDataStore.edit { prefs ->
            prefs[Keys.CHANNEL] = channelNumber
        }
    }

    suspend fun setScanPowerMode(mode: ScanPowerMode) {
        context.walkieDataStore.edit { prefs ->
            prefs[Keys.SCAN_MODE] = mode.name
        }
    }

    suspend fun setBroadcastEnabled(enabled: Boolean) {
        context.walkieDataStore.edit { prefs ->
            prefs[Keys.BROADCAST_ENABLED] = enabled
        }
    }

    suspend fun setOperatorIdentity(callsign: String, slotNumber: Int) {
        context.walkieDataStore.edit { prefs ->
            prefs[Keys.CALLSIGN] = callsign.trim().ifEmpty { "ALPHA-LEAD" }.take(16)
            prefs[Keys.LOCAL_SLOT] = slotNumber.coerceIn(1, MAX_GROUP_DEVICES)
        }
    }

    private fun encodeDevices(records: List<EnrolledDeviceRecord>): String {
        val array = JSONArray()
        records.forEach { rec ->
            val obj = JSONObject().apply {
                put("slotNumber", rec.slotNumber)
                put("address", rec.address)
                put("label", rec.label)
                put("isMeshPeer", rec.isMeshPeer)
                if (rec.lastSeenMs != null) {
                    put("lastSeenMs", rec.lastSeenMs)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeDevices(json: String?): List<EnrolledDeviceRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val addr = obj.optString("address")
                    if (addr.isNotBlank()) {
                        add(
                            EnrolledDeviceRecord(
                                slotNumber = obj.optInt("slotNumber", i + 1).coerceIn(1, MAX_GROUP_DEVICES),
                                address = addr,
                                label = obj.optString("label", "Unit-${i + 1}"),
                                isMeshPeer = obj.optBoolean("isMeshPeer", false),
                                lastSeenMs = if (obj.has("lastSeenMs")) obj.getLong("lastSeenMs") else null
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
