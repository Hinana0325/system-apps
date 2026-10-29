package cn.xinxitong.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.components.SearchField
import cn.xinxitong.coreui.components.SectionHeader
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
                ContactsScreen()
            }
        }
    }
}

@Composable
private fun ContactsScreen(viewModel: ContactsViewModel = viewModel()) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val error by viewModel.error.collectAsState()
    var selected by remember { mutableStateOf<Contact?>(null) }
    var query by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadContacts()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadContacts()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }

    fun openAddContact() {
        // 通过系统新建联系人界面写入，不直接写联系人数据库。
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (selected != null) "联系人详情" else "联系人",
                onBack = if (selected != null) ({ selected = null }) else null,
                backIcon = Icons.Filled.ArrowBack,
                actions = {
                    IconButton(onClick = ::openAddContact) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "新建联系人")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = selected
        if (current != null) {
            ContactDetail(contact = current, onBack = { selected = null })
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                EmptyState("需要“读取联系人”权限才能显示联系人。")
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("授予权限") }
                return@Column
            }
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "搜索姓名、号码或邮箱")
            Spacer(Modifier.height(8.dp))
            val shown = filterContacts(contacts, query)
            if (shown.isEmpty()) {
                EmptyState(if (query.isBlank()) "暂无联系人" else "没有匹配的联系人")
            } else {
                val grouped = groupContacts(shown)
                LazyColumn {
                    grouped.forEach { (key, group) ->
                        item { SectionHeader(key) }
                        items(group, key = { it.id }) { contact ->
                            ContactRow(contact = contact, onClick = { selected = contact })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(contact = contact, modifier = Modifier.size(44.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(contact.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.numbers.firstOrNull() ?: "无号码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContactAvatar(contact: Contact, modifier: Modifier = Modifier) {
    // 首字始终画在底层：照片加载失败时仍有可识别的兜底，不会出现空白圆。
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fallbackInitial(contact.displayName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val photoUri = contact.photoUri
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = "联系人照片",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun ContactDetail(contact: Contact, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(contact = contact, modifier = Modifier.size(64.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${contact.numbers.size} 个号码 · ${contact.emails.size} 个邮箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionHeader("电话")
        if (contact.numbers.isEmpty()) {
            Text("无号码", style = MaterialTheme.typography.bodyMedium)
        }
        contact.numbers.forEach { number ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(number, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                }) { Icon(Icons.Filled.Call, contentDescription = "拨打") }
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
                }) { Icon(Icons.Filled.Message, contentDescription = "发信息") }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("邮箱")
        if (contact.emails.isEmpty()) {
            Text("无邮箱", style = MaterialTheme.typography.bodyMedium)
        }
        contact.emails.forEach { email ->
            Text(email, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回列表") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                // 在系统联系人应用里打开该联系人进行编辑/删除，由系统处理权限与写入。
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.id.toString(),
                    )
                }
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("在系统联系人中打开") }
    }
}
