package cu.maxwell.maxfirewall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class InfoFragment : Fragment() {

    private lateinit var tvVersion: TextView
    private lateinit var btnGithub: LinearLayout
    private lateinit var btnLicense: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar vistas
        tvVersion = view.findViewById(R.id.tv_version)
        btnGithub = view.findViewById(R.id.btn_github)
        btnLicense = view.findViewById(R.id.btn_license)

        // Cargar versión
        loadVersionInfo()

        // Configurar listeners
        setupListeners()
    }

    private fun loadVersionInfo() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = packageInfo.longVersionCode
            tvVersion.text = getString(R.string.info_version, versionName, versionCode)
        } catch (e: Exception) {
            tvVersion.text = getString(R.string.info_version_default)
        }
    }

    private fun setupListeners() {
        btnGithub.setOnClickListener {
            openUrl("https://github.com")
        }

        btnLicense.setOnClickListener {
            showLicenseDialog()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun showLicenseDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.info_license))
            .setMessage(getString(R.string.info_license_full_text))
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
