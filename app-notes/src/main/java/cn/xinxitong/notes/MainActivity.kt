package cn.xinxitong.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.ConfirmDialog
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SearchField
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
                NotesScreen()
            }
        }
    }
}

@Composable
private fun NotesScreen(viewModel: NotesViewModel = viewModel()) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()

    // editing: null = 列表；非 null 的 Note = 编辑已有；特殊标记 newNote=true = 新建。
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }

    if (creating || editing != null) {
        EditorScreen(
            note = editing,
            onSave = { body ->
                viewModel.saveNote(editing, body)
                creating = false
                editing = null
            },
            onBack = {
                creating = false
                editing = null
            },
            onDelete = { note ->
                deleteTarget = note
            },
            onTogglePin = { note -> viewModel.togglePin(note) },
        )
    } else {
        Scaffold(
            topBar = { AppTopBar(title = "备忘录") },
            floatingActionButton = {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建备忘录")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                SearchField(
                    query = query,
                    onQueryChange = { viewModel.setQuery(it) },
                    placeholder = "搜索标题或正文",
                )
                Spacer(Modifier.height(8.dp))
                val shown = searchNotes(notes, query)
                if (shown.isEmpty()) {
                    EmptyState(if (query.isBlank()) "暂无备忘录，点右下角 + 新建。" else "没有匹配的备忘录")
                } else {
                    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                    LazyColumn {
                        items(shown, key = { it.id }) { note ->
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = note }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.pinned) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = "已置顶",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                        }
                                        Text(
                                            note.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        note.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatter.format(Date(note.updatedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.togglePin(note) }) {
                                    Icon(
                                        if (note.pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (note.pinned) "取消置顶" else "置顶",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = "删除备忘录",
            message = "确定要删除“${target.title}”吗？此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                deleteTarget = null
                viewModel.deleteNote(target)
                creating = false
                editing = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EditorScreen(
    note: Note?,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    onDelete: (Note) -> Unit,
    onTogglePin: (Note) -> Unit,
) {
    val context = LocalContext.current
    var body by remember(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    val isNew = note == null

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isNew) "新建备忘录" else "编辑备忘录",
                onBack = onBack,
                backIcon = Icons.Filled.ArrowBack,
                actions = {
                    val current = note
                    if (current != null) {
                        IconButton(onClick = { onTogglePin(current) }) {
                            Icon(
                                if (current.pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (current.pinned) "取消置顶" else "置顶",
                            )
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, current.body)
                                putExtra(Intent.EXTRA_SUBJECT, current.title)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享备忘录"))
                        }) { Icon(Icons.Filled.Share, contentDescription = "分享") }
                        IconButton(onClick = { onDelete(current) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("写点什么……") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${countCharacters(body)} 字 · ${countLines(body)} 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text("取消") }
                TextButton(
                    onClick = { onSave(body) },
                    enabled = body.isNotBlank(),
                ) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
