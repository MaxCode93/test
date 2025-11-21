package cu.maxwell.maxfirewall

import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {
    fun applyTheme(themeMode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
    }
}
