package cn.xinxitong.clock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SettingRow
import cn.xinxitong.coreui.components.SwitchRow
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.Warning
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.time.DayOfWeek
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                ClockScreen()
            }
        }
    }
}

private enum class ClockTab { CLOCK, ALARMS, STOPWATCH, TIMER }

@Composable
private fun ClockScreen(viewModel: ClockViewModel = viewModel()) {
    var tab by remember { mutableStateOf(ClockTab.CLOCK) }
    Scaffold(
        topBar = { AppTopBar(title = "时钟") },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == ClockTab.CLOCK, onClick = { tab = ClockTab.CLOCK }, text = { Text("时钟") })
                Tab(selected = tab == ClockTab.ALARMS, onClick = { tab = ClockTab.ALARMS }, text = { Text("闹钟") })
                Tab(selected = tab == ClockTab.STOPWATCH, onClick = { tab = ClockTab.STOPWATCH }, text = { Text("秒表") })
                Tab(selected = tab == ClockTab.TIMER, onClick = { tab = ClockTab.TIMER }, text = { Text("计时器") })
            }
            when (tab) {
                ClockTab.CLOCK -> ClockTabContent()
                ClockTab.ALARMS -> AlarmsTabContent(viewModel)
                ClockTab.STOPWATCH -> StopwatchTabContent(viewModel)
                ClockTab.TIMER -> TimerTabContent(viewModel)
            }
        }
    }
}

@Composable
private fun ClockTabContent() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatClockTime(now),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatChineseDate(now),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "显示的是本机系统时间（本地时区），不是网络授时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlarmsTabContent(viewModel: ClockViewModel) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()
    val canScheduleExact by viewModel.canScheduleExact.collectAsState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotificationPermission = granted }

    LaunchedEffect(Unit) { viewModel.refreshExactAlarmPermission() }

    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (!canScheduleExact) {
            Text(
                text = "未授予精确闹钟权限：闹钟仍会安排，但时间可能不精确（受 Doze 影响）。",
                style = MaterialTheme.typography.bodySmall,
                color = Warning,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("去授予精确闹钟权限") }
        }
        if (!hasNotificationPermission) {
            Text(
                text = "未授予通知权限：闹钟响起时可能看不到提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = Warning,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            OutlinedButton(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("授予通知权限") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("闹钟", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                editing = AlarmItem(
                    id = System.currentTimeMillis(),
                    hour = 7,
                    minute = 0,
                    label = "",
                    repeatDays = emptyList(),
                    enabled = true,
                )
                showEditor = true
            }) { Icon(Icons.Filled.Add, contentDescription = "新建闹钟") }
        }
        if (alarms.isEmpty()) {
            EmptyState("暂无闹钟，点右上角 + 新建。")
        } else {
            LazyColumn {
                items(alarms, key = { it.id }) { alarm ->
                    SwitchRow(
                        title = formatAlarmTime(alarm.hour, alarm.minute),
                        subtitle = "${alarm.label.ifBlank { "闹钟" }} · ${repeatDescription(alarm.repeatDays.toSet())}",
                        checked = alarm.enabled,
                        onCheckedChange = { viewModel.setAlarmEnabled(alarm, it) },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        TextButton(onClick = { editing = alarm; showEditor = true }) { Text("编辑") }
                        IconButton(onClick = { viewModel.deleteAlarm(alarm) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除闹钟")
                        }
                    }
                }
            }
        }
    }

    val editingAlarm = editing
    if (showEditor && editingAlarm != null) {
        AlarmEditorDialog(
            alarm = editingAlarm,
            onSave = { saved ->
                viewModel.saveAlarm(saved)
                showEditor = false
                editing = null
            },
            onDismiss = { showEditor = false; editing = null },
        )
    }
}

@Composable
private fun AlarmEditorDialog(
    alarm: AlarmItem,
    onSave: (AlarmItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var hourText by remember { mutableStateOf(alarm.hour.toString()) }
    var minuteText by remember { mutableStateOf("%02d".format(alarm.minute)) }
    var labelText by remember { mutableStateOf(alarm.label) }
    var repeatDays by remember { mutableStateOf(alarm.repeatDays.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("闹钟设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("时 (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("分 (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("标签（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("重复", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (day in DayOfWeek.entries.sortedBy { it.value }) {
                        FilterChip(
                            selected = repeatDays.contains(day.value),
                            onClick = {
                                repeatDays = if (repeatDays.contains(day.value)) {
                                    repeatDays - day.value
                                } else {
                                    repeatDays + day.value
                                }
                            },
                            label = { Text(weekdayShortName(day.value).removePrefix("周")) },
                        )
                    }
                }
                Text("不选星期表示仅响一次。", style = MaterialTheme.typography.bodySmall)
                val errorText = error
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hour = hourText.toIntOrNull()
                val minute = minuteText.toIntOrNull()
                if (hour == null || minute == null || !isValidAlarmTime(hour, minute)) {
                    error = "请输入合法的时间（时 0-23，分 0-59）。"
                    return@TextButton
                }
                onSave(
                    alarm.copy(
                        hour = hour,
                        minute = minute,
                        label = labelText.trim(),
                        repeatDays = repeatDays.sorted(),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StopwatchTabContent(viewModel: ClockViewModel) {
    val elapsed by viewModel.stopwatchElapsed.collectAsState()
    val running by viewModel.stopwatchRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(text = formatStopwatch(elapsed), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (running) viewModel.pauseStopwatch() else viewModel.startStopwatch() }) {
                Text(if (running) "暂停" else "开始")
            }
            OutlinedButton(onClick = { viewModel.lapStopwatch() }, enabled = running) { Text("计次") }
            OutlinedButton(onClick = { viewModel.resetStopwatch() }) { Text("重置") }
        }
        Spacer(Modifier.height(24.dp))
        if (laps.isEmpty()) {
            Text("暂无计次记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(laps) { index, lapElapsed ->
                    val previous = if (index == 0) 0L else laps[index - 1]
                    SettingRow(
                        title = "计次 ${index + 1}",
                        subtitle = "累计 ${formatStopwatch(lapElapsed)} · 本段 ${formatStopwatch(lapElapsed - previous)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerTabContent(viewModel: ClockViewModel) {
    val context = LocalContext.current
    val total by viewModel.timerTotal.collectAsState()
    val remaining by viewModel.timerRemaining.collectAsState()
    val running by viewModel.timerRunning.collectAsState()
    val finished by viewModel.timerFinished.collectAsState()

    var minutesText by remember { mutableStateOf("5") }
    var secondsText by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(text = formatCountdown(remaining), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        if (finished) {
            Text("计时结束", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("秒") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val minutes = minutesText.toIntOrNull()
                val seconds = secondsText.toIntOrNull()
                if (minutes == null || seconds == null) {
                    error = "请输入数字。"
                    return@Button
                }
                val millis = timerInputToMillis(minutes, seconds)
                if (millis <= 0) {
                    error = "计时时长需大于 0。"
                    return@Button
                }
                error = null
                viewModel.setTimerTotal(millis)
                viewModel.startTimer()
            }) { Text("开始计时") }
            val errorText = error
            if (errorText != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (total > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "已设定 ${formatCountdown(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.pauseTimer() }) { Text("暂停") }
                OutlinedButton(onClick = { viewModel.resetTimer() }) { Text("重置") }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "计时结束时会发一条通知（需通知权限）；应用被系统回收后倒计时可能中断，闹钟不受此影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
