package cu.maxwell.maxfirewall

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

enum class BottomNavTab {
    FIREWALL, SETTINGS, INFO
}

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter
    private lateinit var loadingContainer: LinearLayout
    private lateinit var buttonMasterToggle: Button
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var chipGroupFilters: ChipGroup
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var prefs: FirewallPreferences
    
    private var currentBottomNavTab = BottomNavTab.FIREWALL

    private val masterAppList = mutableListOf<AppInfo>()
    private var currentSortFilterMode = SortFilterMode.NAME
    private var isSortBlockedFirst = false
    private var currentSearchQuery: String? = null
    private val selectedFilters = mutableSetOf<String>()

    private var actionMode: ActionMode? = null
    private var isInSelectionMode = false

    private val vpnRequestCode = 101

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            exportSettings(it)
        }
    }

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            importSettings(it)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
            prefs.setRebootReminder(false)
            invalidateOptionsMenu() 
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        prefs = FirewallPreferences(this)
        
        isSortBlockedFirst = prefs.isSortBlockedFirst()
        
        loadingContainer = findViewById(R.id.loading_container)
        recyclerView = findViewById(R.id.recycler_view_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        setupSwipeRefresh()
        
        chipGroupFilters = findViewById(R.id.chip_group_filters)
        setupFilterChips()

        setupAdapter()
        buttonMasterToggle = findViewById(R.id.button_master_toggle)

        setupMasterToggle()
        setupBottomNavigation()

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        
        // Sincronizar el estado del botón master toggle con el estado actual
        updateMasterButton(prefs.isFirewallEnabled())
        
        if (prefs.isFirewallEnabled()) {
             // VPN mode is always active
        }
    }

    private fun setupAdapter() {
        appAdapter = AppAdapter(
            emptyList(),
            onItemClick = { app ->
                if (isInSelectionMode) {
                    toggleSelection(app)
                }
            },
            onItemLongClick = { app ->
                if (!isInSelectionMode) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                toggleSelection(app)
            },
            onWifiClick = { app ->
                onToggleClicked(app, "wifi")
            },
            onDataClick = { app ->
                onToggleClicked(app, "data")
            }
        )
        recyclerView.adapter = appAdapter
    }

    private fun setupMasterToggle() {
        updateMasterButton(prefs.isFirewallEnabled())

        buttonMasterToggle.setOnClickListener {
            val isCurrentlyEnabled = prefs.isFirewallEnabled()
            val newEnabledState = !isCurrentlyEnabled

            prefs.setFirewallEnabled(newEnabledState)
            updateMasterButton(newEnabledState)
            onMasterToggleChanged(newEnabledState)
        }
    }

    private fun updateMasterButton(isEnabled: Boolean) {
        buttonMasterToggle.text = if (isEnabled) {
            getString(R.string.master_disable)
        } else {
            getString(R.string.master_enable)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadApps()
        }
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.neon_cyan)
        )
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.nav_firewall
        
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_firewall -> {
                    currentBottomNavTab = BottomNavTab.FIREWALL
                    showFirewallTab()
                    true
                }
                R.id.nav_settings -> {
                    currentBottomNavTab = BottomNavTab.SETTINGS
                    showSettingsTab()
                    true
                }
                R.id.nav_info -> {
                    currentBottomNavTab = BottomNavTab.INFO
                    showInfoTab()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFilterChips() {
        // Load saved filter states, with defaults for "user"
        selectedFilters.clear()
        
        val chipIds = listOf("all", "user", "system", "allowed", "blocked")
        chipIds.forEach { chipId ->
            val isChecked = when (chipId) {
                "user" -> prefs.getFilterChipState(chipId, true)
                else -> prefs.getFilterChipState(chipId, false)
            }
            if (isChecked) {
                selectedFilters.add(chipId)
            }
        }
        
        // Set up chip listeners
        val chipMap = mapOf(
            "all" to R.id.chip_all,
            "user" to R.id.chip_user,
            "system" to R.id.chip_system,
            "allowed" to R.id.chip_allowed,
            "blocked" to R.id.chip_blocked
        )
        
        var isUpdatingChips = false
        
        chipMap.forEach { (chipId, chipResId) ->
            val chip = findViewById<Chip>(chipResId)
            chip.isCheckable = true
            chip.isChecked = selectedFilters.contains(chipId)
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isUpdatingChips) return@setOnCheckedChangeListener
                
                isUpdatingChips = true
                
                when {
                    isChecked && chipId == "all" -> {
                        // Selecting "All": deselect User and System
                        selectedFilters.apply {
                            clear()
                            add("all")
                            // Re-add status filters if they were selected
                            if (prefs.getFilterChipState("allowed", false)) add("allowed")
                            if (prefs.getFilterChipState("blocked", false)) add("blocked")
                        }
                        // Update UI
                        findViewById<Chip>(chipMap["user"]!!).isChecked = false
                        findViewById<Chip>(chipMap["system"]!!).isChecked = false
                        if ("allowed" in selectedFilters) findViewById<Chip>(chipMap["allowed"]!!).isChecked = true
                        if ("blocked" in selectedFilters) findViewById<Chip>(chipMap["blocked"]!!).isChecked = true
                    }
                    isChecked && chipId == "user" -> {
                        // Selecting User: deselect All and System
                        selectedFilters.apply {
                            remove("all")
                            remove("system")
                            add("user")
                        }
                        // Update UI
                        findViewById<Chip>(chipMap["all"]!!).isChecked = false
                        findViewById<Chip>(chipMap["system"]!!).isChecked = false
                    }
                    isChecked && chipId == "system" -> {
                        // Selecting System: deselect All and User
                        selectedFilters.apply {
                            remove("all")
                            remove("user")
                            add("system")
                        }
                        // Update UI
                        findViewById<Chip>(chipMap["all"]!!).isChecked = false
                        findViewById<Chip>(chipMap["user"]!!).isChecked = false
                    }
                    isChecked && chipId == "allowed" -> {
                        // Selecting Allowed: deselect Blocked
                        selectedFilters.apply {
                            remove("blocked")
                            add("allowed")
                        }
                        findViewById<Chip>(chipMap["blocked"]!!).isChecked = false
                    }
                    isChecked && chipId == "blocked" -> {
                        // Selecting Blocked: deselect Allowed
                        selectedFilters.apply {
                            remove("allowed")
                            add("blocked")
                        }
                        findViewById<Chip>(chipMap["allowed"]!!).isChecked = false
                    }
                    !isChecked && chipId == "all" -> {
                        // Deselecting All: auto-select User
                        selectedFilters.apply {
                            clear()
                            add("user")
                        }
                        chipMap.forEach { (cId, cResId) ->
                            findViewById<Chip>(cResId).isChecked = cId == "user"
                        }
                    }
                    !isChecked && (chipId == "user" || chipId == "system") -> {
                        // Deselecting User/System: if nothing left, select All
                        selectedFilters.remove(chipId)
                        if (selectedFilters.filter { it in listOf("all", "user", "system") }.isEmpty()) {
                            selectedFilters.add("all")
                            chipMap.forEach { (cId, cResId) ->
                                findViewById<Chip>(cResId).isChecked = cId == "all"
                            }
                        }
                    }
                    !isChecked && (chipId == "allowed" || chipId == "blocked") -> {
                        // Deselecting Allowed/Blocked: just remove it
                        selectedFilters.remove(chipId)
                    }
                }
                
                // Save preferences
                chipIds.forEach { cId ->
                    prefs.setFilterChipState(cId, selectedFilters.contains(cId))
                }
                
                isUpdatingChips = false
                sortAndDisplayApps()
            }
        }
    }

    private fun showFirewallTab() {
        // Mostrar RecyclerView de aplicaciones y chips
        val firewallContainer = findViewById<View>(R.id.firewall_content_container)
        firewallContainer?.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
        findViewById<View>(R.id.fragment_container).visibility = View.GONE
        buttonMasterToggle.visibility = View.VISIBLE
        findViewById<View>(R.id.app_bar_layout).visibility = View.VISIBLE
        // Sincronizar el estado del botón con el estado actual del firewall
        updateMasterButton(prefs.isFirewallEnabled())
        // Mostrar menú superior
        invalidateOptionsMenu()
    }

    private fun showSettingsTab() {
        // Mostrar SettingsFragment
        val firewalContainter = findViewById<View>(R.id.firewall_content_container)
        firewalContainter?.visibility = View.GONE
        recyclerView.visibility = View.GONE
        buttonMasterToggle.visibility = View.GONE
        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<View>(R.id.app_bar_layout).visibility = View.GONE
        val fragment = SettingsFragment()
        replaceFragment(fragment)
        // Ocultar menú superior
        invalidateOptionsMenu()
    }

    private fun showInfoTab() {
        // Mostrar InfoFragment
        val firewalContainter = findViewById<View>(R.id.firewall_content_container)
        firewalContainter?.visibility = View.GONE
        recyclerView.visibility = View.GONE
        buttonMasterToggle.visibility = View.GONE
        findViewById<View>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<View>(R.id.app_bar_layout).visibility = View.GONE
        val fragment = InfoFragment()
        replaceFragment(fragment)
        // Ocultar menú superior
        invalidateOptionsMenu()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun onMasterToggleChanged(isEnabled: Boolean) {
        if (isEnabled) {
            applyAllRules()
        } else {
            removeAllRules()
        }
    }

    private fun applyAllRules() {
        startVpnService()
    }

    private fun removeAllRules() {
        stopVpnService()
    }

    private fun forceVpnRestart() {
        if (!prefs.isFirewallEnabled()) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("MainActivity", "forceVpnRestart: Sending STOP")
            val stopIntent = Intent(this@MainActivity, FirewallVpnService::class.java)
            stopIntent.action = FirewallVpnService.ACTION_STOP
            startForegroundService(stopIntent)

            delay(300)

            Log.d("MainActivity", "forceVpnRestart: Sending START")
            val startIntent = Intent(this@MainActivity, FirewallVpnService::class.java)
            startForegroundService(startIntent)
        }
    }

    private fun startVpnService() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, vpnRequestCode)
        } else {
            onActivityResult(vpnRequestCode, Activity.RESULT_OK, null)
        }
    }

    private fun stopVpnService() {
        FirewallVpnService.stopVpn(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == vpnRequestCode && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, FirewallVpnService::class.java)
            startForegroundService(intent)
        }
    }

    private fun setupShizukuListeners() {
        // Listeners removed - VPN only mode
    }

    private fun checkShizukuPermission(silent: Boolean = false): Boolean {
        // Shizuku removed - always return false
        return false
    }

    private fun checkShizukuAndApplyAll(silent: Boolean = false) {
        // Shizuku removed
    }

    private fun checkShizukuAndRemoveAll() {
        // Shizuku removed
    }

    private fun checkShizukuAndApplyRule(app: AppInfo) {
        // Shizuku removed
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun loadApps() {
        actionMode?.finish()

        loadingContainer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            masterAppList.clear()

            val packageManager = packageManager
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            for (pkgInfo in packages) {
                val app = pkgInfo.applicationInfo
                if (app == null) {
                    continue
                }

                val appName = packageManager.getApplicationLabel(app).toString()
                val appIcon = packageManager.getApplicationIcon(app)
                val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                val hasInternet = pkgInfo.requestedPermissions?.contains("android.permission.INTERNET") == true

                masterAppList.add(AppInfo(
                    appName = appName,
                    packageName = app.packageName,
                    appIcon = appIcon,
                    isSystemApp = isSystemApp,
                    hasInternetPermission = hasInternet,
                    isWifiBlocked = prefs.isWifiBlocked(FirewallMode.VPN, app.packageName),
                    isDataBlocked = prefs.isDataBlocked(FirewallMode.VPN, app.packageName),
                    isSelected = false
                ))
            }

            withContext(Dispatchers.Main) {
                sortAndDisplayApps()
                loadingContainer.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun sortAndDisplayApps() {
        var processedList = masterAppList.toList()

        // Apply search filter
        currentSearchQuery?.let { query ->
            if (query.isNotBlank()) {
                processedList = processedList.filter {
                    it.appName.contains(query, ignoreCase = true)
                }
            }
        }

        // Apply chip filters
        // Determine the type filter (All, User, or System)
        val typeFilter = when {
            selectedFilters.contains("user") -> processedList.filter { !it.isSystemApp }
            selectedFilters.contains("system") -> processedList.filter { it.isSystemApp }
            else -> processedList // "All" or no type filter
        }
        
        // Apply status filter (Allowed/Blocked) - they are mutually exclusive
        processedList = when {
            selectedFilters.contains("allowed") -> {
                // Only allowed: show apps that are NOT blocked
                typeFilter.filter { !(it.isWifiBlocked || it.isDataBlocked) }
            }
            selectedFilters.contains("blocked") -> {
                // Only blocked: show apps that ARE blocked
                typeFilter.filter { it.isWifiBlocked || it.isDataBlocked }
            }
            else -> {
                // None selected: show all
                typeFilter
            }
        }

        val sortedList: List<AppInfo>
        if (isSortBlockedFirst) {
            sortedList = processedList.sortedWith(compareBy(
                { !(it.isWifiBlocked || it.isDataBlocked) }, 
                { it.appName.lowercase() } 
            ))
        } else {
            sortedList = processedList.sortedBy { it.appName.lowercase() }
        }

        appAdapter.updateApps(sortedList)
    }


    private fun onToggleClicked(app: AppInfo, type: String) {
        val newWifiState = if (type == "wifi") !app.isWifiBlocked else app.isWifiBlocked
        val newDataState = if (type == "data") !app.isDataBlocked else app.isDataBlocked

        var targetApps = if (isInSelectionMode) {
            masterAppList.filter { it.isSelected }
        } else {
            listOf(app)
        }

        if (targetApps.isEmpty() && !isInSelectionMode) {
            targetApps = listOf(app)
        }

        val isFirewallEnabled = prefs.isFirewallEnabled()

        for (targetApp in targetApps) {
            prefs.setWifiBlocked(FirewallMode.VPN, targetApp.packageName, newWifiState)
            prefs.setDataBlocked(FirewallMode.VPN, targetApp.packageName, newDataState)

            targetApp.isWifiBlocked = newWifiState
            targetApp.isDataBlocked = newDataState
        }

        if (isFirewallEnabled) {
            forceVpnRestart()
        }

        if (isSortBlockedFirst) {
            sortAndDisplayApps()
        } else {
            val visibleApps = appAdapter.getAppList()
            for (targetApp in targetApps) {
                val index = visibleApps.indexOf(targetApp)
                if (index != -1) {
                    appAdapter.notifyItemChanged(index)
                }
            }
        }

        if (isInSelectionMode) {
            actionMode?.finish()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            isInSelectionMode = true
            mode?.menuInflater?.inflate(R.menu.selection_menu, menu)
            mode?.title = getString(R.string.selection_title_zero)
            findViewById<Toolbar>(R.id.toolbar).visibility = View.GONE
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.menu_select_all -> {
                    selectAllApps()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            isInSelectionMode = false
            actionMode = null
            masterAppList.forEach { it.isSelected = false }
            findViewById<Toolbar>(R.id.toolbar).visibility = View.VISIBLE
            sortAndDisplayApps()
        }
    }

    private fun toggleSelection(app: AppInfo) {
        app.isSelected = !app.isSelected

        val selectedCount = masterAppList.count { it.isSelected }

        if (selectedCount == 0) {
            actionMode?.finish()
        } else {
            actionMode?.title = when (selectedCount) {
                1 -> getString(R.string.selection_title_one)
                else -> getString(R.string.selection_title_many, selectedCount)
            }
        }

        val index = appAdapter.getAppList().indexOf(app)
        if (index != -1) {
            appAdapter.notifyItemChanged(index)
        }
    }

    private fun selectAllApps() {
        val visibleApps = appAdapter.getAppList()
        val allSelected = visibleApps.all { it.isSelected }

        visibleApps.forEach { app ->
            app.isSelected = !allSelected
        }

        val selectedCount = masterAppList.count { it.isSelected }
        actionMode?.title = getString(R.string.selection_title_many, selectedCount)
        appAdapter.updateApps(visibleApps)
    }

    private fun exportSettings(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = prefs.exportAllSettings()
                if (json == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                    out.flush()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Export failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettings(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            var importSuccess = false
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                }

                if (jsonString.isNullOrBlank()) {
                    throw Exception("File is empty or could not be read.")
                }

                importSuccess = prefs.importAllSettings(jsonString)

            } catch (e: Exception) {
                Log.e("MainActivity", "Import failed", e)
                importSuccess = false
            }

            withContext(Dispatchers.Main) {
                if (importSuccess) {
                    Toast.makeText(this@MainActivity, getString(R.string.import_success), Toast.LENGTH_SHORT).show()

                    loadApps()
                    if (prefs.isFirewallEnabled()) {
                        forceVpnRestart()
                    }
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSortDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_sort, null)
        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_filter)
        val checkBox = view.findViewById<CheckBox>(R.id.checkbox_sort_blocked)

        when (currentSortFilterMode) {
            SortFilterMode.NAME -> radioGroup.check(R.id.radio_sort_name)
            SortFilterMode.SYSTEM -> radioGroup.check(R.id.radio_sort_system)
            SortFilterMode.USER -> radioGroup.check(R.id.radio_sort_user)
            SortFilterMode.INTERNET_ONLY -> radioGroup.check(R.id.radio_sort_internet)
        }
        checkBox.isChecked = isSortBlockedFirst
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentSortFilterMode = when (checkedId) {
                R.id.radio_sort_system -> SortFilterMode.SYSTEM
                R.id.radio_sort_user -> SortFilterMode.USER
                R.id.radio_sort_internet -> SortFilterMode.INTERNET_ONLY
                else -> SortFilterMode.NAME
            }
            sortAndDisplayApps()
        }
        
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            isSortBlockedFirst = isChecked
            prefs.setSortBlockedFirst(isSortBlockedFirst)
            sortAndDisplayApps()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort_dialog_title))
            .setView(view)
            
            .show()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Solo mostrar el menú si estamos en la vista de Firewall
        if (currentBottomNavTab != BottomNavTab.FIREWALL) {
            return false
        }
        
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu?.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as? SearchView
        
        searchView?.queryHint = getString(R.string.search_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query
                sortAndDisplayApps()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                sortAndDisplayApps()
                return true
            }
        })
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // Solo preparar el menú si estamos en la vista de Firewall
        if (currentBottomNavTab != BottomNavTab.FIREWALL) {
            return false
        }

        return super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            else -> super.onOptionsItemSelected(item)
        }
    }
}


