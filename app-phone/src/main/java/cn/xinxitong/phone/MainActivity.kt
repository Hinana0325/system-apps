package cn.xinxitong.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SearchField
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                PhoneScreen()
            }
        }
    }
}

@Composable
private fun PhoneScreen(viewModel: PhoneViewModel = viewModel()) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var hasCallLogPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCallLogPermission = granted
        if (granted) viewModel.loadCallLog()
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCallPermission = granted }

    LaunchedEffect(hasCallLogPermission) {
        if (hasCallLogPermission) viewModel.loadCallLog()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }

    fun placeCall(rawNumber: String) {
        val number = normalizeDialNumber(rawNumber)
        if (number.isBlank()) {
            Toast.makeText(context, "请先输入号码", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.fromParts("tel", number, null)
        if (hasCallPermission) {
            context.startActivity(Intent(Intent.ACTION_CALL, uri))
        } else {
            // 未授予直接拨打权限时退回系统拨号盘，由用户在系统界面确认拨打。
            Toast.makeText(context, "未授予直接拨打权限，已打开系统拨号盘", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "电话") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("拨号") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("通话记录") })
            }
            if (tab == 0) {
                DialerScreen(
                    hasCallPermission = hasCallPermission,
                    onRequestCallPermission = {
                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    },
                    onCall = ::placeCall,
                )
            } else {
                CallLogScreen(
                    entries = entries,
                    hasPermission = hasCallLogPermission,
                    onRequestPermission = {
                        callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    },
                    onCallBack = ::placeCall,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialerScreen(
    hasCallPermission: Boolean,
    onRequestCallPermission: () -> Unit,
    onCall: (String) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = number.ifEmpty { "输入号码" },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        keys.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowKeys.forEach { key ->
                    DialKey(
                        label = key,
                        onClick = { number += key },
                        onLongClick = if (key == "0") {
                            { number = longPressZero(number) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (number.isNotEmpty()) {
                IconButton(onClick = { number = number.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除一位")
                }
            }
            Button(onClick = { onCall(number) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.padding(start = 8.dp))
                Text("拨打")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "长按 0 可输入 +。\n" + if (hasCallPermission) {
                "已授予直接拨打权限，点“拨打”会直接呼出。"
            } else {
                "未授予直接拨打权限，点“拨打”会先打开系统拨号盘。点下面按钮可申请权限。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasCallPermission) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestCallPermission) { Text("申请直接拨打权限") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogScreen(
    entries: List<CallLogEntry>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCallBack: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        if (!hasPermission) {
            EmptyState("需要“读取通话记录”权限才能显示通话记录。")
            Button(onClick = onRequestPermission, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("授予权限")
            }
            return
        }
        SearchField(query = query, onQueryChange = { query = it }, placeholder = "搜索号码或姓名")
        Spacer(Modifier.height(8.dp))
        val shown = filterCallLog(entries, query)
        if (shown.isEmpty()) {
            EmptyState(if (query.isBlank()) "暂无通话记录" else "没有匹配的通话记录")
        } else {
            LazyColumn {
                items(shown, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onCallBack(entry.number) },
                        ).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (entry.type) {
                                CallTypes.INCOMING -> Icons.Filled.CallReceived
                                CallTypes.OUTGOING -> Icons.AutoMirrored.Filled.CallMissedOutgoing
                                CallTypes.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                                else -> Icons.Filled.Call
                            },
                            contentDescription = callTypeLabel(entry.type),
                            tint = if (entry.type == CallTypes.MISSED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                entry.displayName ?: entry.number.ifEmpty { "未知号码" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${callTypeLabel(entry.type)} · ${formatCallDate(entry.dateMillis)} · ${formatDuration(entry.durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!entry.displayName.isNullOrBlank() && entry.number.isNotBlank()) {
                                Text(
                                    entry.number,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onCallBack(entry.number) }) {
                            Icon(Icons.Filled.Call, contentDescription = "回拨")
                        }
                    }
                }
            }
        }
    }
}

/** 拨号盘按键：支持点按输入；0 键长按输入 +（空输入时）或 0。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.aspectRatio(1.6f).padding(vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
