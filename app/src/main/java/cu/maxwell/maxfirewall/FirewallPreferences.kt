package cu.maxwell.maxfirewall

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject

class FirewallPreferences(context: Context) {

    private val vpnPrefs: SharedPreferences =
        context.getSharedPreferences(FirewallMode.VPN.key, Context.MODE_PRIVATE)
        
    private val defaultPrefs: SharedPreferences =
        context.getSharedPreferences("default_prefs", Context.MODE_PRIVATE)

    private val DEFAULT_PREFS_KEY = "defaults"
        
    private val TAG = "FirewallPreferences"

    private fun getPrefs(mode: FirewallMode): SharedPreferences {
        return vpnPrefs
    }

    private fun getKey(packageName: String, type: String): String {
        return "${packageName}_${type}"
    }

    // --- Wi-Fi Controls ---

    fun setWifiBlocked(mode: FirewallMode, packageName: String, isBlocked: Boolean) {
        getPrefs(mode).edit().putBoolean(getKey(packageName, "wifi"), isBlocked).commit()
    }

    fun isWifiBlocked(mode: FirewallMode, packageName: String): Boolean {
        return getPrefs(mode).getBoolean(getKey(packageName, "wifi"), false)
    }

    // --- Data Controls ---

    fun setDataBlocked(mode: FirewallMode, packageName: String, isBlocked: Boolean) {
        getPrefs(mode).edit().putBoolean(getKey(packageName, "data"), isBlocked).commit()
    }

    fun isDataBlocked(mode: FirewallMode, packageName: String): Boolean {
        return getPrefs(mode).getBoolean(getKey(packageName, "data"), false)
    }

    // --- Copy Logic ---

    fun copySettings(from: FirewallMode, to: FirewallMode) {
        val fromPrefs = getPrefs(from)
        val toEditor = getPrefs(to).edit()

        toEditor.clear()

        fromPrefs.all.forEach { (key, value) ->
            if (value is Boolean) {
                toEditor.putBoolean(key, value)
            }
        }
        toEditor.commit()
    }
    
    // --- Master Firewall State ---
    
    fun setFirewallEnabled(isEnabled: Boolean) {
        defaultPrefs.edit().putBoolean("is_firewall_enabled", isEnabled).apply()
    }
    
    fun isFirewallEnabled(): Boolean {
        return defaultPrefs.getBoolean("is_firewall_enabled", false)
    }
    
    // --- Sort Preference ---
    
    fun setSortBlockedFirst(isBlockedFirst: Boolean) {
        defaultPrefs.edit().putBoolean("sort_blocked_first", isBlockedFirst).apply()
    }
    
    fun isSortBlockedFirst(): Boolean {
        return defaultPrefs.getBoolean("sort_blocked_first", false)
    }
    
    // --- Reboot Reminder Preference ---
    
    fun setRebootReminder(isEnabled: Boolean) {
        defaultPrefs.edit().putBoolean("reboot_reminder_enabled", isEnabled).apply()
    }

    fun isRebootReminderEnabled(): Boolean {
        return defaultPrefs.getBoolean("reboot_reminder_enabled", false)
    }

    // --- Theme Mode ---

    fun setThemeMode(themeMode: ThemeMode) {
        defaultPrefs.edit().putString("theme_mode", themeMode.storageValue).apply()
    }

    fun getThemeMode(): ThemeMode {
        val storedValue = defaultPrefs.getString("theme_mode", ThemeMode.SYSTEM.storageValue)
        return ThemeMode.fromStorage(storedValue)
    }

    // --- Filter Chips ---
    
    fun setFilterChipState(chipId: String, isChecked: Boolean) {
        defaultPrefs.edit().putBoolean("filter_chip_$chipId", isChecked).apply()
    }

    fun getFilterChipState(chipId: String, defaultValue: Boolean = false): Boolean {
        return defaultPrefs.getBoolean("filter_chip_$chipId", defaultValue)
    }
    
    fun getSelectedFilters(): Set<String> {
        val filters = mutableSetOf<String>()
        val chipIds = listOf("all", "user", "system", "internet", "allowed", "blocked")
        chipIds.forEach { chipId ->
            if (getFilterChipState(chipId)) {
                filters.add(chipId)
            }
        }
        return filters
    }

    // --- VPN Helper ---
    
    fun getBlockedPackagesForNetwork(mode: FirewallMode, isWifi: Boolean): Set<String> {
        val prefs = getPrefs(mode)
        val blockedPackages = mutableSetOf<String>()
        val keyType = if (isWifi) "wifi" else "data"
        
        prefs.all.forEach { (key, value) ->
            // Find keys that are blocked (value == true)
            if (value is Boolean && value) {
                // Check if the key matches the current network type
                if (key.endsWith(keyType)) {
                    val packageName = key.substringBeforeLast('_')
                    if (packageName.isNotEmpty()) {
                        blockedPackages.add(packageName)
                    }
                }
            }
        }
        return blockedPackages
    }
    
    // --- Import / Export ---
    
    fun exportAllSettings(): String? {
        return try {
            val masterJson = JSONObject()

            val vpnJson = JSONObject()
            vpnPrefs.all.forEach { (key, value) ->
                vpnJson.put(key, value)
            }

            masterJson.put(FirewallMode.VPN.key, vpnJson)

            val defaultJson = JSONObject()
            defaultPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> defaultJson.put(key, value)
                    is String -> defaultJson.put(key, value)
                }
            }

            masterJson.put(DEFAULT_PREFS_KEY, defaultJson)

            masterJson.toString(2) // Indent for readability
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting settings", e)
            null
        }
    }

    /**
     * Imports and replaces all settings from a JSON string.
     */
    fun importAllSettings(jsonString: String): Boolean {
        return try {
            val masterJson = JSONObject(jsonString)

            val vpnJson = masterJson.getJSONObject(FirewallMode.VPN.key)
            val vpnEditor = vpnPrefs.edit().clear()
            vpnJson.keys().forEach { key ->
                vpnEditor.putBoolean(key, vpnJson.getBoolean(key))
            }
            vpnEditor.commit()

            if (masterJson.has(DEFAULT_PREFS_KEY)) {
                val defaultJson = masterJson.getJSONObject(DEFAULT_PREFS_KEY)
                val defaultEditor = defaultPrefs.edit().clear()
                defaultJson.keys().forEach { key ->
                    when (val value = defaultJson.get(key)) {
                        is Boolean -> defaultEditor.putBoolean(key, value)
                        is String -> defaultEditor.putString(key, value)
                    }
                }
                defaultEditor.commit()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing settings", e)
            false
        }
    }
}

