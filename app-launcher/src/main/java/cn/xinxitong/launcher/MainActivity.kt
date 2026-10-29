package cn.xinxitong.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SearchField
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, systemDark)) {
                LauncherScreen()
            }
        }
    }
}

@Composable
fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val query by viewModel.query.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var longPressed by remember { mutableStateOf<AppEntry?>(null) }

    // 每次进入前台附近刷新一次列表：用 LaunchedEffect + 生命周期事件太重，
    // 这里在组合首次进入时刷新，卸载后用户回到桌面再点进时会重新组合。
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            ClockHeader()
            Spacer(Modifier.height(16.dp))
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = "搜索应用",
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.filteredApps().isEmpty() -> {
                    EmptyState(if (query.isBlank()) "没有可启动的应用" else "没有匹配“$query”的应用")
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(viewModel.filteredApps(), key = { it.packageName + "/" + it.className }) { entry ->
                            AppGridItem(
                                entry = entry,
                                onClick = { viewModel.launchApp(entry) },
                                onLongClick = { longPressed = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    val target = longPressed
    if (target != null) {
        AlertDialog(
            onDismissRequest = { longPressed = null },
            title = { Text(target.label) },
            text = { Text(target.packageName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        longPressed = null
                        viewModel.openAppInfo(target)
                    },
                ) { Text("应用信息") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        longPressed = null
                        viewModel.uninstallApp(target)
                    },
                ) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val date = Date(now)
    val timeText = remember(now) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(date)
    }
    val dateText = remember(now) {
        SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(date)
    }
    Column {
        Text(timeText, style = MaterialTheme.typography.displaySmall)
        Text(dateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(entry: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
    ) {
        if (entry.iconBitmap != null) {
            Image(
                bitmap = entry.iconBitmap,
                contentDescription = entry.label,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // 图标加载失败的兜底：灰底 + 名称首字。
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
