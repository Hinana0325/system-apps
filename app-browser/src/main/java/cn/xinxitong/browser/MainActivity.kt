package cn.xinxitong.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.ConfirmDialog
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SettingRow
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.Warning
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BrowserScreen { WEB, HISTORY, BOOKMARKS }

private const val HOME_URL = "https://www.bing.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                BrowserScreen()
            }
        }
    }
}

@Composable
private fun BrowserScreen(viewModel: BrowserViewModel = viewModel()) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var screen by remember { mutableStateOf(BrowserScreen.WEB) }
    var addressInput by remember { mutableStateOf(HOME_URL) }
    var displayedUrl by remember { mutableStateOf(HOME_URL) }
    var pageTitle by remember { mutableStateOf("浏览器") }
    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun navigate(url: String) {
        if (url.isBlank()) return
        displayedUrl = url
        addressInput = url
        loadError = null
        val webView = webViewRef.value
        if (webView != null) {
            if (webView.url != url) webView.loadUrl(url)
        }
    }

    val isBookmarked = bookmarks.any { it.url == displayedUrl }

    BackHandler(enabled = screen != BrowserScreen.WEB) {
        screen = BrowserScreen.WEB
    }
    BackHandler(enabled = screen == BrowserScreen.WEB && canGoBack) {
        webViewRef.value?.goBack()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "浏览器",
                actions = {
                    IconButton(
                        onClick = { webViewRef.value?.goBack() },
                        enabled = canGoBack,
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = "后退") }
                    IconButton(
                        onClick = { webViewRef.value?.goForward() },
                        enabled = canGoForward,
                    ) { Icon(Icons.Filled.ArrowForward, contentDescription = "前进") }
                    IconButton(onClick = { webViewRef.value?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("历史记录") },
                            onClick = { menuExpanded = false; screen = BrowserScreen.HISTORY },
                        )
                        DropdownMenuItem(
                            text = { Text("书签") },
                            onClick = { menuExpanded = false; screen = BrowserScreen.BOOKMARKS },
                        )
                        DropdownMenuItem(
                            text = { Text("在外部浏览器中打开") },
                            onClick = {
                                menuExpanded = false
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(displayedUrl)),
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "没有能打开此链接的应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                BrowserScreen.HISTORY -> HistoryScreen(
                    history = history,
                    onOpen = { navigate(it); screen = BrowserScreen.WEB },
                    onClear = { confirmClearHistory = true },
                    onBack = { screen = BrowserScreen.WEB },
                )
                BrowserScreen.BOOKMARKS -> BookmarksScreen(
                    bookmarks = bookmarks,
                    onOpen = { navigate(it); screen = BrowserScreen.WEB },
                    onRemove = { viewModel.removeBookmark(it) },
                    onBack = { screen = BrowserScreen.WEB },
                )
                BrowserScreen.WEB -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            placeholder = { Text("输入网址或搜索内容") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { navigate(resolveInput(addressInput)) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { navigate(resolveInput(addressInput)) }) {
                            Icon(Icons.Filled.Search, contentDescription = "前往")
                        }
                        IconButton(onClick = {
                            if (isBookmarked) {
                                viewModel.removeBookmark(displayedUrl)
                            } else {
                                viewModel.addBookmark(
                                    Bookmark(
                                        title = pageTitle.ifBlank { displayedUrl },
                                        url = displayedUrl,
                                    ),
                                )
                            }
                        }) {
                            Icon(
                                if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isBookmarked) "取消书签" else "加入书签",
                            )
                        }
                    }
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (progress < 100) {
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val error = loadError
                    if (error != null) {
                        Text(
                            text = "页面加载失败：$error",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean = false

                                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                        displayedUrl = url
                                        addressInput = url
                                        loadError = null
                                        canGoBack = view.canGoBack()
                                        canGoForward = view.canGoForward()
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        displayedUrl = url
                                        addressInput = url
                                        canGoBack = view.canGoBack()
                                        canGoForward = view.canGoForward()
                                        val title = view.title.orEmpty()
                                        if (title.isNotBlank()) pageTitle = title
                                        viewModel.recordHistory(
                                            HistoryEntry(
                                                title = title.ifBlank { url },
                                                url = url,
                                                timeMillis = System.currentTimeMillis(),
                                            ),
                                        )
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        if (request.isForMainFrame) {
                                            loadError = error.description?.toString().orEmpty()
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        progress = newProgress
                                    }

                                    override fun onReceivedTitle(view: WebView, title: String?) {
                                        if (!title.isNullOrBlank()) pageTitle = title
                                    }
                                }
                                webViewRef.value = this
                                loadUrl(HOME_URL)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "清除历史记录",
            message = "确定要清除全部浏览历史吗？书签不会被清除。",
            confirmText = "清除",
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

@Composable
private fun HistoryScreen(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("历史记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, contentDescription = "清除历史记录") }
        }
        if (history.isEmpty()) {
            EmptyState("暂无浏览历史")
        } else {
            val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
            LazyColumn {
                items(history, key = { it.url + it.timeMillis }) { entry ->
                    SettingRow(
                        title = entry.title,
                        subtitle = "${entry.url}\n${formatter.format(Date(entry.timeMillis))}",
                        onClick = { onOpen(entry.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("书签", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (bookmarks.isEmpty()) {
            EmptyState("暂无书签，在浏览页点右上角的书签图标添加。")
        } else {
            LazyColumn {
                items(bookmarks, key = { it.url }) { bookmark ->
                    SettingRow(
                        title = bookmark.title,
                        subtitle = bookmark.url,
                        onClick = { onOpen(bookmark.url) },
                        trailing = {
                            IconButton(onClick = { onRemove(bookmark.url) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除书签")
                            }
                        },
                    )
                }
            }
        }
    }
}
