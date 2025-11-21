package cu.maxwell.maxfirewall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    private lateinit var switchFirewallEnabled: SwitchMaterial
    private lateinit var switchRebootReminder: SwitchMaterial
    private lateinit var btnExportSettings: LinearLayout
    private lateinit var btnImportSettings: LinearLayout
    private lateinit var prefs: FirewallPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = FirewallPreferences(requireContext())

        // Inicializar vistas
        switchFirewallEnabled = view.findViewById(R.id.switch_firewall_enabled)
        switchRebootReminder = view.findViewById(R.id.switch_reboot_reminder)
        btnExportSettings = view.findViewById(R.id.btn_export_settings)
        btnImportSettings = view.findViewById(R.id.btn_import_settings)

        // Cargar estado actual
        loadSettings()

        // Configurar listeners
        setupListeners()
    }

    private fun loadSettings() {
        switchFirewallEnabled.isChecked = prefs.isFirewallEnabled()
        switchRebootReminder.isChecked = prefs.isRebootReminderEnabled()
    }

    private fun setupListeners() {
        switchFirewallEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.setFirewallEnabled(isChecked)
            Toast.makeText(
                requireContext(),
                if (isChecked) getString(R.string.firewall_enabled) else getString(R.string.firewall_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }

        switchRebootReminder.setOnCheckedChangeListener { _, isChecked ->
            prefs.setRebootReminder(isChecked)
            Toast.makeText(
                requireContext(),
                if (isChecked) "Recordatorio activado" else "Recordatorio desactivado",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnExportSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Exportar configuración (próximamente)", Toast.LENGTH_SHORT).show()
        }

        btnImportSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Importar configuración (próximamente)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }
}
