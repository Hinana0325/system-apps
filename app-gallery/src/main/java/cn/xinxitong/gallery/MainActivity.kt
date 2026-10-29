package cn.xinxitong.gallery

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                GalleryScreen()
            }
        }
    }
}

@Composable
private fun GalleryScreen(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }

    val mediaPermissions = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var hasPermission by remember {
        mutableStateOf(
            mediaPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        hasPermission = granted.values.all { it }
        if (hasPermission) viewModel.loadMedia()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadMedia()
    }

    // 系统删除确认（API 30+ 可恢复安全异常时弹出，删除后刷新列表）。
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadMedia()
        } else {
            Toast.makeText(context, "已取消删除", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDelete(item: MediaItem) {
        runCatching {
            if (viewModel.deleteItem(item)) {
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { throwable ->
            val recoverable = throwable as? RecoverableSecurityException
            if (recoverable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intentSender = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(item.uri),
                ).intentSender
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                Toast.makeText(context, "删除失败：${throwable.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                title = if (selected != null) "查看" else "相册",
                onBack = if (selected != null) ({ selected = null }) else null,
                backIcon = Icons.Filled.ArrowBack,
                actions = {
                    val item = selected
                    if (item != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType.ifBlank { if (item.isVideo) "video/*" else "image/*" }
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享"))
                        }) { Icon(Icons.Filled.Share, contentDescription = "分享") }
                        IconButton(onClick = { deleteTarget = item }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = selected
        if (current != null) {
            DetailScreen(item = current)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                EmptyState("需要读取媒体权限才能显示照片和视频。")
                Button(
                    onClick = { permissionLauncher.launch(mediaPermissions) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("授予权限") }
                return@Column
            }
            val albums = groupByAlbum(items).keys.sorted()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { selectedAlbum = null },
                        label = { Text("全部 (${items.size})") },
                    )
                }
                items(albums) { album ->
                    AssistChip(
                        onClick = { selectedAlbum = album },
                        label = { Text("$album (${groupByAlbum(items)[album]?.size ?: 0})") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "搜索文件名或相册")
            Spacer(Modifier.height(8.dp))
            val shown = filterMedia(items, selectedAlbum, query)
            if (shown.isEmpty()) {
                EmptyState(if (query.isBlank()) "暂无照片或视频" else "没有匹配的媒体")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.uri.toString() }) { item ->
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clickable {
                                if (item.isVideo) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(item.uri, item.mimeType.ifBlank { "video/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            Toast.makeText(context, "没有能播放此视频的应用", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    selected = item
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = "删除",
            message = "确定要删除“${target.displayName}”吗？此操作无法撤销。",
            confirmText = "删除",
            onConfirm = {
                deleteTarget = null
                selected = null
                requestDelete(target)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DetailScreen(item: MediaItem) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(12.dp))
        Text(item.displayName, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        KeyValueRow("类型", if (item.isVideo) "视频" else "图片")
        KeyValueRow("相册", item.bucket.ifBlank { "未命名相册" })
        KeyValueRow("大小", formatMediaSize(item.sizeBytes))
        KeyValueRow("时间", formatMediaDate(item.dateMillis))
        if (item.width > 0 && item.height > 0) {
            KeyValueRow("尺寸", "${item.width} × ${item.height}")
        }
        if (item.mimeType.isNotBlank()) KeyValueRow("格式", item.mimeType)
        if (item.relativePath.isNotBlank()) KeyValueRow("位置", item.relativePath)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, item.mimeType.ifBlank { "image/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(context, "没有能打开此文件的应用", Toast.LENGTH_SHORT).show()
                    }
            }) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null)
                Text("用系统应用打开")
            }
        }
    }
}
