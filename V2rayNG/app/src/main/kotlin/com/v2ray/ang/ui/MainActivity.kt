package com.v2ray.ang.ui

import android.Manifest
import android.app.ActionBar
import android.app.AlertDialog
import android.app.FragmentTransaction
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import me.drakeet.support.toast.ToastCompat
import android.view.View

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            pendingInternetChecks?.let { guids ->
                pendingInternetChecks = null
                runInternetChecks(guids)
            } ?: startV2Ray()
        } else {
            pendingInternetChecks = null
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private var selectedGroupId: String? = null
    private var pendingInternetChecks: List<String>? = null
    private var internetCheckJob: Job? = null
    private var isCheckingProfiles = false
    val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        title = getString(R.string.app_name)
        actionBar?.subtitle = "v${BuildConfig.VERSION_NAME}"
        selectedGroupId = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_GROUP)

        binding.connectButton.setOnClickListener {
            if (isCheckingProfiles) return@setOnClickListener
            if (mainViewModel.isRunning.value == true) {
                binding.connectButton.setText(R.string.connection_test_testing)
                mainViewModel.testCurrentServerRealPing()
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
        binding.connectButton.setOnLongClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
                true
            } else {
                false
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)


        setupViewModelObserver()
        setupMainTabs()
        migrateLegacy()
    }

    private fun setupViewModelObserver() {
        mainViewModel.importResultAction.observe(this) { count ->
            if (count == null) return@observe
            mainViewModel.importResultAction.value = null
            toast(if (count > 0) R.string.toast_success else R.string.toast_failure)
            if (count > 0) setupMainTabs()
        }
        mainViewModel.updateListAction.observe(this) {
            val index = it ?: return@observe
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) {
            if (mainViewModel.isRunning.value == true) {
                binding.connectButton.text = it
            }
        }
        mainViewModel.isRunning.observe(this) {
            val isRunning = it ?: return@observe
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.connectButton.setText(R.string.connection_connected_hold)
            } else {
                binding.connectButton.setText(R.string.action_connect)
            }
            hideCircle()
        }
        mainViewModel.startListenBroadcast()
    }

    private fun migrateLegacy() {
        GlobalScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    private fun setupMainTabs() {
        actionBar?.let { bar ->
            val subscriptions = MmkvManager.decodeSubscriptions()
            bar.removeAllTabs()
            if (subscriptions.isEmpty()) {
                selectedGroupId = null
                mainStorage?.remove(MmkvManager.KEY_SELECTED_GROUP)
                bar.navigationMode = ActionBar.NAVIGATION_MODE_STANDARD
                mainViewModel.setServerFilter(null)
                return@let
            }

            bar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
            val listener = object : ActionBar.TabListener {
                override fun onTabSelected(tab: ActionBar.Tab, transaction: FragmentTransaction?) {
                    selectedGroupId = tab.tag as String
                    mainStorage?.encode(MmkvManager.KEY_SELECTED_GROUP, selectedGroupId)
                    mainViewModel.setServerFilter(selectedGroupId)
                    invalidateOptionsMenu()
                }
                override fun onTabUnselected(tab: ActionBar.Tab, transaction: FragmentTransaction?) = Unit
                override fun onTabReselected(tab: ActionBar.Tab, transaction: FragmentTransaction?) = Unit
            }
            bar.addTab(bar.newTab()
                    .setText(R.string.title_tab_configs)
                    .setTag("")
                    .setTabListener(listener), false)
            subscriptions.forEach { (id, subscription) ->
                val groupName = Utils.subscriptionGroupName(subscription.url)
                        .ifEmpty { subscription.remarks }
                bar.addTab(bar.newTab()
                        .setText(groupName)
                        .setTag(id)
                        .setTabListener(listener), false)
            }
            val selectedIndex = (0 until bar.tabCount)
                    .firstOrNull { bar.getTabAt(it)?.tag == selectedGroupId } ?: 0
            selectedGroupId = bar.getTabAt(selectedIndex)?.tag as? String
            bar.setSelectedNavigationItem(selectedIndex)
        }
    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    public override fun onResume() {
        super.onResume()
        setupMainTabs()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.sub_update)?.isVisible = !selectedGroupId.isNullOrEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_manually_vmess -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.VMESS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_manually_ss -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.SHADOWSOCKS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_manually_socks -> {
            startActivity(Intent().putExtra("createConfigType", EConfigType.SOCKS.value).
            setClass(this, ServerActivity::class.java))
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            selectedGroupId?.takeIf { it.isNotEmpty() }?.let { importConfigViaSub(it) }
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, mainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_profiles -> {
            mainViewModel.testAllPing()
            true
        }

        R.id.check_profiles -> {
            checkProfiles(mainViewModel.serverList.toList())
            true
        }

        R.id.sub_setting -> {
            startActivity(Intent(this, SubSettingActivity::class.java))
            true
        }

        R.id.settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)
                    .putExtra("isRunning", mainViewModel.isRunning.value == true))
            true
        }
        R.id.licenses -> {
            val notices = resources.openRawResource(R.raw.legal_notices)
                    .bufferedReader()
                    .use { it.readText() }
            AlertDialog.Builder(this)
                    .setTitle(R.string.title_licenses)
                    .setMessage(notices)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            true
        }
        R.id.logcat -> {
            startActivity(Intent(this, LogcatActivity::class.java))
            true
        }

//        R.id.settings -> {
//            startActivity<SettingsActivity>("isRunning" to isRunning)
//            true
//        }
//        R.id.logcat -> {
//            startActivity<LogcatActivity>()
//            true
//        }
        else -> super.onOptionsItemSelected(item)
    }

    fun showProfileTestMenu(guid: String) {
        AlertDialog.Builder(this)
                .setItems(arrayOf(
                        getString(R.string.title_ping_profile),
                        getString(R.string.title_check_profile))) { _, which ->
                    if (which == 0) {
                        mainViewModel.testPing(guid)
                    } else {
                        checkProfiles(listOf(guid))
                    }
                }
                .show()
    }

    private fun checkProfiles(guids: List<String>) {
        if (guids.isEmpty()) {
            toast(R.string.toast_none_data)
            return
        }
        if (internetCheckJob?.isActive == true) {
            toast(R.string.connection_test_testing)
            return
        }
        val vpnMode = (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN") == "VPN"
        val permissionIntent = if (vpnMode) VpnService.prepare(this) else null
        if (permissionIntent != null) {
            pendingInternetChecks = guids
            requestVpnPermission.launch(permissionIntent)
        } else {
            runInternetChecks(guids)
        }
    }

    private fun runInternetChecks(guids: List<String>) {
        internetCheckJob = lifecycleScope.launch {
            val previousSelected = mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).orEmpty()
            val wasRunning = mainViewModel.isRunning.value == true
            var successful = 0
            isCheckingProfiles = true
            try {
                guids.forEachIndexed { index, guid ->
                    binding.connectButton.text = getString(
                            R.string.checking_profiles_progress, index + 1, guids.size)
                    // Stop unconditionally: a profile may start its service process but
                    // fail before the running-state broadcast reaches the activity.
                    Utils.stopVService(this@MainActivity)
                    waitForServiceState(false)
                    mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, guid)
                    mainViewModel.updateListAction.value = -1
                    V2RayServiceManager.startV2Ray(this@MainActivity)
                    val started = waitForServiceState(true)
                    val result = if (started) {
                        delay(750)
                        withContext(Dispatchers.IO) {
                            try {
                                Libv2ray.measureURLThroughSocks(
                                        "https://myip.wtf", "127.0.0.1:10808")
                            } catch (_: Exception) {
                                -1L
                            }
                        }
                    } else {
                        -1L
                    }
                    if (result >= 0L) successful++
                    MmkvManager.encodeServerTestDelayMillis(guid, result)
                    mainViewModel.updateListAction.value = mainViewModel.serverList.indexOf(guid)
                }
            } finally {
                withContext(NonCancellable) {
                    Utils.stopVService(this@MainActivity)
                    waitForServiceState(false)
                    if (previousSelected.isNotEmpty()) {
                        mainStorage?.encode(MmkvManager.KEY_SELECTED_SERVER, previousSelected)
                    }
                    if (wasRunning && previousSelected.isNotEmpty()) {
                        V2RayServiceManager.startV2Ray(this@MainActivity)
                        waitForServiceState(true)
                    }
                    mainViewModel.updateListAction.value = -1
                    isCheckingProfiles = false
                    internetCheckJob = null
                    if (!isFinishing) {
                        binding.connectButton.setText(if (mainViewModel.isRunning.value == true) {
                            R.string.connection_connected_hold
                        } else {
                            R.string.action_connect
                        })
                        toast(getString(R.string.profiles_checked_result, successful, guids.size))
                    }
                }
            }
        }
    }

    private suspend fun waitForServiceState(running: Boolean, timeoutMillis: Long = 15000L): Boolean {
        val attempts = (timeoutMillis / 200L).toInt()
        repeat(attempts) {
            if ((mainViewModel.isRunning.value == true) == running) return true
            delay(200L)
        }
        return (mainViewModel.isRunning.value == true) == running
    }


    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        if (forConfig)
                            scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                        else
                            scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        toast(R.string.msg_dialog_progress)
        mainViewModel.importBatchConfig(server, subid)
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            GlobalScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub(subscriptionId: String)
            : Boolean {
        try {
            val subscription = MmkvManager.decodeSubscriptions()
                    .firstOrNull { it.first == subscriptionId }
                    ?: return false
            toast(R.string.title_sub_update)
            val url = subscription.second.url
            if (!Utils.isValidUrl(url)) return false
            lifecycleScope.launch {
                val configText = try {
                    withContext(Dispatchers.IO) { Utils.getUrlContentWithCustomUserAgent(url) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val groupName = Utils.subscriptionGroupName(url)
                            .ifEmpty { subscription.second.remarks }
                    toast("\"$groupName\" " + getString(R.string.toast_failure))
                    return@launch
                }
                // The ViewModel parses and saves both plain and Base64 responses
                // on IO, and retains the import across activity recreation.
                importBatchConfig(configText, subscription.first)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use { input ->
                                importCustomizeConfig(input?.bufferedReader()?.readText())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            toast(R.string.toast_success)
            adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        binding.connectButton.isEnabled = false
        binding.connectButton.setText(R.string.connection_connecting)
    }

    fun hideCircle() {
        binding.connectButton.isEnabled = true
    }
}
