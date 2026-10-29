package cn.xinxitong.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SettingRow
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                CalendarScreen()
            }
        }
    }
}

@Composable
private fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val context = LocalContext.current
    val month by viewModel.month.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val error by viewModel.error.collectAsState()

    val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
    var hasPermission by remember {
        mutableStateOf(
            calendarPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasPermission = granted.values.all { it }
        if (hasPermission) viewModel.loadEvents()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadEvents()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }

    val today = LocalDate.now()
    val grid = monthGrid(month.year, month.monthValue)
    val eventDays = eventDaysInMonth(events, month)
    val dayEvents = eventsForDay(events, selectedDate)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "日历",
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            val begin = selectedDate.atTime(9, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            val end = selectedDate.atTime(10, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "没有能新建日程的应用", Toast.LENGTH_SHORT).show()
                            }
                    }) { Icon(Icons.Filled.Add, contentDescription = "新建日程") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
            if (!hasPermission) {
                EmptyState("需要日历权限才能显示系统日历中的日程。")
                Button(
                    onClick = { permissionLauncher.launch(calendarPermissions) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("授予权限") }
                return@Column
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                }
                Text(
                    text = formatMonthTitle(month),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.goToToday() },
                    modifier = Modifier.weight(1f),
                ) { Text("回到今天") }
            }
            Row(Modifier.fillMaxWidth()) {
                for (label in listOf("一", "二", "三", "四", "五", "六", "日")) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            for (rowIndex in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (colIndex in 0 until 7) {
                        val date = grid[rowIndex * 7 + colIndex]
                        val isCurrentMonth = date.month == month.month
                        val isSelected = isSameDay(date, selectedDate)
                        val isToday = isSameDay(date, today)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .background(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = CircleShape,
                                )
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { viewModel.selectDate(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrentMonth) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (eventDays.contains(date)) {
                                    Box(
                                        Modifier.size(5.dp).background(
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape,
                                        ),
                                    )
                                } else {
                                    Spacer(Modifier.height(5.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatChineseDate(selectedDate),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            if (dayEvents.isEmpty()) {
                EmptyState("这一天没有日程")
            } else {
                LazyColumn {
                    items(dayEvents, key = { it.id }) { event ->
                        SettingRow(
                            title = event.title.ifBlank { "无标题" },
                            subtitle = "${formatEventTime(event)}" +
                                if (event.calendarName.isNotBlank()) " · ${event.calendarName}" else "",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = viewModel.eventUri(event)
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        Toast.makeText(context, "没有能查看此日程的应用", Toast.LENGTH_SHORT).show()
                                    }
                            },
                        )
                    }
                }
            }
        }
    }
}
