package cu.maxwell.maxfirewall

import android.app.Application
import com.google.android.material.color.DynamicColors

class MaxFirewallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        ThemeUtils.applyTheme(FirewallPreferences(this).getThemeMode())
    }
}
