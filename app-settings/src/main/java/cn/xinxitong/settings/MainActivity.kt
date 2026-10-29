package cn.xinxitong.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.InfoCard
import cn.xinxitong.coreui.components.KeyValueRow
import cn.xinxitong.coreui.components.SearchField
import cn.xinxitong.coreui.components.SectionHeader
import cn.xinxitong.coreui.components.SettingRow
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, systemDark)) {
                SettingsRoot(themePreference = themePreference)
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    themePreference: ThemePreference,
    viewModel: SettingsViewModel = viewModel(),
) {
    var showApps by remember { mutableStateOf(false) }
    if (showApps) {
        AppsScreen(viewModel = viewModel, onBack = { showApps = false })
    } else {
        SettingsScreen(
            themePreference = themePreference,
            viewModel = viewModel,
            onOpenApps = { showApps = true },
        )
    }
}

@Composable
private fun SettingsScreen(
    themePreference: ThemePreference,
    viewModel: SettingsViewModel,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current
    val wifiEnabled by viewModel.wifiEnabled.collectAsState()
    val bluetoothEnabled by viewModel.bluetoothEnabled.collectAsState()
    val bluetoothGranted by viewModel.bluetoothPermissionGranted.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val canWriteSettings by viewModel.canWriteSettings.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val storageTotal by viewModel.storageTotal.collectAsState()
    val storageFree by viewModel.storageFree.collectAsState()
    val themeMode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNetwork() }

    Scaffold(
        topBar = { AppTopBar(title = "设置") },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("网络")
            SettingRow(
                title = "Wi-Fi",
                subtitle = when (wifiEnabled) {
                    true -> "已开启（只读显示，普通应用无法直接开关）"
                    false -> "已关闭（只读显示，普通应用无法直接开关）"
                    null -> "状态不可用"
                },
                icon = Icons.Filled.Wifi,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
            )
            SettingRow(
                title = "蓝牙",
                subtitle = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !bluetoothGranted ->
                        "需要蓝牙权限才能读取状态"
                    bluetoothEnabled == true -> "已开启（只读显示，普通应用无法直接开关）"
                    bluetoothEnabled == false -> "已关闭（只读显示，普通应用无法直接开关）"
                    else -> "状态不可用"
                },
                icon = Icons.Filled.Bluetooth,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !bluetoothGranted) {
                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                },
            )

            SectionHeader("显示")
            Text(
                "主题",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ThemeOptionRow("跟随系统", ThemeMode.SYSTEM, themeMode) {
                CoroutineScope(Dispatchers.Main).launch { themePreference.setThemeMode(it) }
            }
            ThemeOptionRow("浅色", ThemeMode.LIGHT, themeMode) {
                CoroutineScope(Dispatchers.Main).launch { themePreference.setThemeMode(it) }
            }
            ThemeOptionRow("深色", ThemeMode.DARK, themeMode) {
                CoroutineScope(Dispatchers.Main).launch { themePreference.setThemeMode(it) }
            }
            SettingRow(
                title = "亮度",
                subtitle = if (canWriteSettings) {
                    "当前亮度：${brightness ?: "不可用"} / 255"
                } else {
                    "需要“修改系统设置”授权后才能调节亮度"
                },
                icon = Icons.Filled.BrightnessMedium,
            )
            if (brightness != null) {
                Slider(
                    value = brightness!!.toFloat(),
                    onValueChange = { viewModel.setBrightness(it.toInt()) },
                    valueRange = 0f..255f,
                    enabled = canWriteSettings,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (!canWriteSettings) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text("去授权修改系统设置") }
            }

            SectionHeader("声音")
            if (volumes.isEmpty()) {
                Text("音量信息不可用", modifier = Modifier.padding(horizontal = 16.dp))
            }
            volumes.forEach { volume ->
                Text(
                    "${volume.label}：${volume.current} / ${volume.max}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Slider(
                    value = volume.current.toFloat(),
                    onValueChange = { viewModel.setVolume(volume.streamType, it.toInt()) },
                    valueRange = 0f..volume.max.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionHeader("存储")
            if (storageTotal != null && storageFree != null) {
                val used = storageTotal!! - storageFree!!
                InfoCard("内部存储") {
                    KeyValueRow("总容量", formatBytes(storageTotal!!))
                    KeyValueRow("已用", formatBytes(used))
                    KeyValueRow("可用", formatBytes(storageFree!!))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { used.toFloat() / storageTotal!!.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text("存储信息不可用", modifier = Modifier.padding(horizontal = 16.dp))
            }

            SectionHeader("应用")
            SettingRow(
                title = "应用列表",
                subtitle = "查看已安装应用，点按打开系统应用信息页",
                icon = Icons.Filled.Apps,
                onClick = onOpenApps,
            )

            SectionHeader("关于")
            val about by viewModel.about.collectAsState()
            if (about != null) {
                InfoCard("本机") {
                    KeyValueRow("制造商", about!!.manufacturer)
                    KeyValueRow("型号", about!!.model)
                    KeyValueRow("Android 版本", about!!.androidRelease)
                    KeyValueRow("API 级别", about!!.sdkInt.toString())
                    KeyValueRow("内核版本", about!!.kernelVersion)
                    KeyValueRow("总内存", about!!.totalRam)
                }
            } else {
                Text("设备信息读取中……", modifier = Modifier.padding(horizontal = 16.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    SettingRow(
        title = label,
        icon = Icons.Filled.Palette,
        onClick = { onSelect(mode) },
        trailing = { RadioButton(selected = current == mode, onClick = { onSelect(mode) }) },
    )
}

@Composable
private fun AppsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }
    Scaffold(
        topBar = {
            AppTopBar(title = "应用", onBack = onBack, backIcon = Icons.Filled.ArrowBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "搜索应用")
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState(if (query.isBlank()) "没有已安装应用信息" else "没有匹配“$query”的应用")
            } else {
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        SettingRow(
                            title = app.label,
                            subtitle = app.packageName,
                            icon = Icons.Filled.Info,
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                        )
                    }
                }
            }
        }
    }
}
