package cn.xinxitong.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.ConfirmDialog
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SettingRow
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                CalculatorScreen()
            }
        }
    }
}

private val KEY_ROWS: List<List<String>> = listOf(
    listOf("C", "(", ")", "%"),
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "-"),
    listOf("0", ".", "⌫", "+"),
)

@Composable
private fun CalculatorScreen(viewModel: CalculatorViewModel = viewModel()) {
    val expression by viewModel.expression.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val error by viewModel.error.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "计算器",
                actions = {
                    IconButton(onClick = { confirmClearHistory = true }, enabled = history.isNotEmpty()) {
                        androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "清除历史记录")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("历史记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "收起" else "展开")
                }
            }
            if (showHistory) {
                if (history.isEmpty()) {
                    EmptyState("暂无计算历史")
                } else {
                    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(history, key = { it.timeMillis }) { entry ->
                            SettingRow(
                                title = "${entry.expression} = ${entry.result}",
                                subtitle = formatter.format(Date(entry.timeMillis)),
                                onClick = { viewModel.reuseHistory(entry) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = expression.ifBlank { "0" },
                style = MaterialTheme.typography.displaySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (preview.isNotBlank()) "= $preview" else "",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
            Spacer(Modifier.height(16.dp))
            for (row in KEY_ROWS) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (key in row) {
                        val isOperatorKey = key in setOf("÷", "×", "-", "+", "%")
                        val isClearKey = key == "C"
                        if (isOperatorKey || isClearKey) {
                            OutlinedButton(
                                onClick = { viewModel.onKey(key) },
                                modifier = Modifier.weight(1f).aspectRatio(1.4f),
                            ) { Text(key, fontSize = 22.sp) }
                        } else {
                            Button(
                                onClick = { viewModel.onKey(key) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.weight(1f).aspectRatio(1.4f),
                            ) { Text(key, fontSize = 22.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { viewModel.onEquals() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("=", fontSize = 24.sp) }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "百分号按“除以 100”计算；除数为零会报错，不会给出错误数字。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "清除历史记录",
            message = "确定要清除全部计算历史吗？",
            confirmText = "清除",
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}
