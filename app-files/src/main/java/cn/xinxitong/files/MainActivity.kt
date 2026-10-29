package cn.xinxitong.files

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.ConfirmDialog
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.KeyValueRow
import cn.xinxitong.coreui.components.SearchField
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, systemDark)) {
                FilesScreen()
            }
        }
    }
}

private fun mimeTypeFor(file: File): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

@Composable
private fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDir.collectAsState()
    val rootDir by viewModel.rootDir.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val message by viewModel.message.collectAsState()
    val hasAllFilesAccess by viewModel.hasAllFilesAccess.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = searchResults != null || viewModel.canNavigateUp()) {
        if (searchResults != null) {
            viewModel.clearSearch()
        } else {
            viewModel.navigateUp()
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message!!)
            viewModel.consumeMessage()
        }
    }

    // 从授权设置页回来时刷新授权状态。
    LaunchedEffect(Unit) { viewModel.refreshAccessState() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "文件管理",
                onBack = if (viewModel.canNavigateUp()) {
                    { viewModel.navigateUp() }
                } else {
                    null
                },
                backIcon = Icons.Filled.ArrowBack,
                actions = {
                    if (clipboard != null) {
                        IconButton(onClick = { viewModel.paste() }) {
                            Icon(Icons.Filled.Paste, contentDescription = "粘贴")
                        }
                    }
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("按名称排序") },
                            onClick = { viewModel.setSortMode(SortMode.NAME); showSortMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("按日期排序") },
                            onClick = { viewModel.setSortMode(SortMode.DATE); showSortMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("按大小排序") },
                            onClick = { viewModel.setSortMode(SortMode.SIZE); showSortMenu = false },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!hasAllFilesAccess) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("未授予“所有文件访问权限”", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "当前只能浏览应用专属目录。去系统设置里授予权限后，才能浏览内部存储的全部文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                        ) { Text("去授予权限") }
                    }
                }
            }

            Text(
                text = currentDir?.absolutePath ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val app = context.applicationContext as android.app.Application
                items(viewModel.quickRoots(app)) { (label, dir) ->
                    AssistChip(
                        onClick = { viewModel.navigateTo(dir) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "按名称搜索（在根目录下递归查找）",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (sortMode) {
                    SortMode.NAME -> "排序：名称"
                    SortMode.DATE -> "排序：日期"
                    SortMode.SIZE -> "排序：大小"
                } + if (clipboard != null) " · 已${if (clipboard!!.mode == ClipboardMode.COPY) "复制" else "剪切"} ${clipboard!!.sources.size} 项" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            val shown = searchResults ?: entries
            if (shown.isEmpty()) {
                EmptyState(
                    if (searchResults != null) "没有匹配“$searchQuery”的文件"
                    else "这个文件夹是空的",
                )
            } else {
                LazyColumn {
                    items(shown, key = { it.file.absolutePath }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (entry.isDirectory) {
                                            viewModel.navigateTo(entry.file)
                                        } else {
                                            selected = entry
                                        }
                                    },
                                    onLongClick = { selected = entry },
                                )
                                .padding(vertical = 10.dp),
                        ) {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (entry.isDirectory) {
                                        formatDate(entry.lastModified)
                                    } else {
                                        "${formatSize(entry.size)} · ${formatDate(entry.lastModified)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 文件信息 / 操作对话框
    val infoTarget = selected
    if (infoTarget != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(infoTarget.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValueRow("类型", if (infoTarget.isDirectory) "文件夹" else mimeTypeFor(infoTarget.file))
                    if (!infoTarget.isDirectory) KeyValueRow("大小", formatSize(infoTarget.size))
                    KeyValueRow("修改时间", formatDate(infoTarget.lastModified))
                    KeyValueRow("路径", infoTarget.file.absolutePath)
                }
            },
            confirmButton = {
                if (!infoTarget.isDirectory) {
                    TextButton(
                        onClick = {
                            selected = null
                            openFile(context, infoTarget.file)
                        },
                    ) { Text("打开") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selected = null; renameTarget = infoTarget }) { Text("重命名") }
                    TextButton(
                        onClick = {
                            selected = null
                            viewModel.setClipboard(listOf(infoTarget.file), ClipboardMode.COPY)
                        },
                    ) { Text("复制") }
                    TextButton(
                        onClick = {
                            selected = null
                            viewModel.setClipboard(listOf(infoTarget.file), ClipboardMode.MOVE)
                        },
                    ) { Text("剪切") }
                    TextButton(
                        onClick = { selected = null; deleteTarget = infoTarget },
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    if (showCreateFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showCreateFolder = false; viewModel.createFolder(name) },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("取消") } },
        )
    }

    val renameEntry = renameTarget
    if (renameEntry != null) {
        var name by remember(renameEntry) { mutableStateOf(renameEntry.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { renameTarget = null; viewModel.rename(renameEntry, name) },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    val deleteEntry = deleteTarget
    if (deleteEntry != null) {
        ConfirmDialog(
            title = "删除",
            message = "确定要删除“${deleteEntry.name}”吗？文件夹会连同里面的内容一起删除，此操作无法撤销。",
            confirmText = "删除",
            onConfirm = { deleteTarget = null; viewModel.delete(deleteEntry) },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun openFile(context: android.content.Context, file: File) {
    runCatching {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        // 没有能打开该类型的应用时，系统会抛异常；这里不做假成功提示，
        // 调用方通过 Snackbar 之外的途径无法得知——因此用 Toast 直观提示。
        android.widget.Toast.makeText(context, "没有能打开此文件的应用", android.widget.Toast.LENGTH_SHORT).show()
    }
}
