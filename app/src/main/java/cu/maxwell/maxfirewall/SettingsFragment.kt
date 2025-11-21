package cu.maxwell.maxfirewall

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private lateinit var switchFirewallEnabled: SwitchMaterial
    private lateinit var switchRebootReminder: SwitchMaterial
    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var btnExportSettings: LinearLayout
    private lateinit var btnImportSettings: LinearLayout
    private lateinit var prefs: FirewallPreferences

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportSettings(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importSettings(it) }
    }

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
        themeRadioGroup = view.findViewById(R.id.radio_group_theme)
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
        val themeMode = prefs.getThemeMode()
        when (themeMode) {
            ThemeMode.SYSTEM -> themeRadioGroup.check(R.id.radio_theme_system)
            ThemeMode.LIGHT -> themeRadioGroup.check(R.id.radio_theme_light)
            ThemeMode.DARK -> themeRadioGroup.check(R.id.radio_theme_dark)
        }
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

        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                R.id.radio_theme_light -> ThemeMode.LIGHT
                R.id.radio_theme_dark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            prefs.setThemeMode(selectedMode)
            ThemeUtils.applyTheme(selectedMode)
        }

        btnExportSettings.setOnClickListener {
            exportLauncher.launch(getString(R.string.backup_file_name))
        }

        btnImportSettings.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    private fun exportSettings(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val json = prefs.exportAllSettings()

            if (json == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                    out.flush()
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettings(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val jsonString = runCatching {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()

            val success = if (!jsonString.isNullOrBlank()) {
                prefs.importAllSettings(jsonString)
            } else {
                false
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_SHORT).show()
                    loadSettings()
                    ThemeUtils.applyTheme(prefs.getThemeMode())
                } else {
                    Toast.makeText(requireContext(), getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }
}
