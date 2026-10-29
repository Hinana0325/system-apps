package cn.xinxitong.settings

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

data class VolumeState(val streamType: Int, val label: String, val current: Int, val max: Int)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _wifiEnabled = MutableStateFlow<Boolean?>(null)
    val wifiEnabled: StateFlow<Boolean?> = _wifiEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow<Boolean?>(null)
    val bluetoothEnabled: StateFlow<Boolean?> = _bluetoothEnabled.asStateFlow()

    private val _bluetoothPermissionGranted = MutableStateFlow(false)
    val bluetoothPermissionGranted: StateFlow<Boolean> = _bluetoothPermissionGranted.asStateFlow()

    private val _brightness = MutableStateFlow<Int?>(null)
    val brightness: StateFlow<Int?> = _brightness.asStateFlow()

    private val _canWriteSettings = MutableStateFlow(false)
    val canWriteSettings: StateFlow<Boolean> = _canWriteSettings.asStateFlow()

    private val _volumes = MutableStateFlow<List<VolumeState>>(emptyList())
    val volumes: StateFlow<List<VolumeState>> = _volumes.asStateFlow()

    private val _storageTotal = MutableStateFlow<Long?>(null)
    val storageTotal: StateFlow<Long?> = _storageTotal.asStateFlow()

    private val _storageFree = MutableStateFlow<Long?>(null)
    val storageFree: StateFlow<Long?> = _storageFree.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _about = MutableStateFlow<AboutInfo?>(null)
    val about: StateFlow<AboutInfo?> = _about.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch { refreshNetwork() }
        viewModelScope.launch { refreshDisplay() }
        viewModelScope.launch { refreshSound() }
        viewModelScope.launch { refreshStorage() }
        viewModelScope.launch { refreshApps() }
        viewModelScope.launch { refreshAbout() }
    }

    /** Wi-Fi / 蓝牙只读状态。普通应用不能直接开关：界面提供跳转系统设置的入口。 */
    fun refreshNetwork() {
        val context = getApplication<Application>()
        @Suppress("DEPRECATION")
        _wifiEnabled.value = runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        }.getOrNull()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _bluetoothPermissionGranted.value = granted
        _bluetoothEnabled.value = if (!granted) {
            null
        } else {
            runCatching {
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                manager.adapter?.isEnabled
            }.getOrNull()
        }
    }

    fun refreshDisplay() {
        val context = getApplication<Application>()
        _canWriteSettings.value = Settings.System.canWrite(context)
        _brightness.value = runCatching {
            val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            if (value < 0) null else value.coerceIn(0, 255)
        }.getOrNull()
    }

    /** 设置亮度：仅在获得 WRITE_SETTINGS 授权时写入，否则调用方应先引导用户去系统授权页。 */
    fun setBrightness(value: Int) {
        val context = getApplication<Application>()
        if (!Settings.System.canWrite(context)) return
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255))
        }
        _brightness.value = value.coerceIn(0, 255)
    }

    fun refreshSound() {
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val specs = listOf(
            AudioManager.STREAM_MUSIC to "媒体音量",
            AudioManager.STREAM_RING to "铃声音量",
            AudioManager.STREAM_ALARM to "闹钟音量",
        )
        _volumes.value = specs.map { (stream, label) ->
            VolumeState(
                streamType = stream,
                label = label,
                current = runCatching { audioManager.getStreamVolume(stream) }.getOrDefault(0),
                max = runCatching { audioManager.getStreamMaxVolume(stream) }.getOrDefault(0),
            )
        }
    }

    fun setVolume(streamType: Int, value: Int) {
        val context = getApplication<Application>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching { audioManager.setStreamVolume(streamType, value, 0) }
        refreshSound()
    }

    fun refreshStorage() {
        val stats = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        if (stats == null) {
            _storageTotal.value = null
            _storageFree.value = null
        } else {
            _storageTotal.value = stats.blockCountLong * stats.blockSizeLong
            _storageFree.value = stats.availableBytes
        }
    }

    suspend fun refreshApps() {
        val context = getApplication<Application>()
        _apps.value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val infos = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
            infos.map { info ->
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
                InstalledApp(info.packageName, label)
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    suspend fun refreshAbout() {
        _about.value = withContext(Dispatchers.IO) {
            val kernel = readFirstLineOrNull(File("/proc/version")) ?: "不可用"
            val meminfo = runCatching { File("/proc/meminfo").readText() }.getOrNull()
            val totalKb = meminfo?.let { parseMemTotalKb(it) }
            AboutInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                kernelVersion = kernel,
                totalRam = if (totalKb != null) formatBytes(totalKb * 1024) else "不可用",
            )
        }
    }
}
