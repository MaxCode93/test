package cu.maxwell.maxfirewall

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val storageValue: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
