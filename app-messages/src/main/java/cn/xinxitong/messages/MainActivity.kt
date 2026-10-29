package cn.xinxitong.messages

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                MessagesScreen()
            }
        }
    }
}

@Composable
private fun MessagesScreen(viewModel: MessagesViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val error by viewModel.error.collectAsState()
    val sendStatus by viewModel.sendStatus.collectAsState()
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var hasReadPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasSendPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasReadPermission = granted
        if (granted) viewModel.loadMessages()
    }
    val sendPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasSendPermission = granted }

    LaunchedEffect(hasReadPermission) {
        if (hasReadPermission) viewModel.loadMessages()
    }

    // 短信发送/送达回执接收器：仅在界面存活期间注册，结果如实显示。
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    MessagesViewModel.ACTION_SMS_SENT -> viewModel.onSmsSent(resultCode)
                    MessagesViewModel.ACTION_SMS_DELIVERED -> viewModel.onSmsDelivered(resultCode)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(MessagesViewModel.ACTION_SMS_SENT)
            addAction(MessagesViewModel.ACTION_SMS_DELIVERED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(sendStatus) {
        if (sendStatus != null) {
            snackbarHostState.showSnackbar(sendStatus!!)
            viewModel.consumeSendStatus()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val number = readContactNumber(context, uri)
        if (number.isNullOrBlank()) {
            Toast.makeText(context, "该联系人没有号码", Toast.LENGTH_SHORT).show()
        } else {
            selectedAddress = number
            composing = false
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasContactsPermission = granted
        if (granted) contactPickerLauncher.launch()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when {
                    selectedAddress != null -> selectedAddress!!
                    composing -> "新建信息"
                    else -> "信息"
                },
                onBack = if (selectedAddress != null || composing) {
                    { selectedAddress = null; composing = false }
                } else {
                    null
                },
                backIcon = Icons.Filled.ArrowBack,
                actions = {
                    if (selectedAddress == null && !composing) {
                        IconButton(onClick = { composing = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "新建信息")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            selectedAddress != null -> ThreadScreen(
                modifier = Modifier.padding(padding),
                address = selectedAddress!!,
                threadMessages = messagesForAddress(messages, selectedAddress!!),
                hasSendPermission = hasSendPermission,
                onRequestSendPermission = {
                    sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                },
                onSend = { body -> viewModel.sendSms(context, selectedAddress!!, body) },
            )
            composing -> ComposeScreen(
                modifier = Modifier.padding(padding),
                onPickContact = {
                    if (hasContactsPermission) {
                        contactPickerLauncher.launch()
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                hasSendPermission = hasSendPermission,
                onRequestSendPermission = {
                    sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                },
                onSend = { address, body ->
                    viewModel.sendSms(context, address, body)
                    selectedAddress = address
                    composing = false
                },
            )
            else -> ConversationListScreen(
                conversations = groupConversations(messages),
                hasPermission = hasReadPermission,
                query = query,
                onQueryChange = { query = it },
                onRequestPermission = { readPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                onOpen = { selectedAddress = it.address },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private fun readContactNumber(context: Context, contactUri: Uri): String? {
    return runCatching {
        var contactId: String? = null
        context.contentResolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) contactId = cursor.getString(0)
        }
        if (contactId == null) return null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}

@Composable
private fun ConversationListScreen(
    conversations: List<Conversation>,
    hasPermission: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpen: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        if (!hasPermission) {
            EmptyState("需要“读取短信”权限才能显示会话。在没有电话功能的设备上，会话列表可能为空。")
            Button(onClick = onRequestPermission, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("授予权限")
            }
            return
        }
        SearchField(query = query, onQueryChange = onQueryChange, placeholder = "搜索号码或内容")
        Spacer(Modifier.height(8.dp))
        val shown = filterConversations(conversations, query)
        if (shown.isEmpty()) {
            EmptyState(if (query.isBlank()) "暂无短信会话" else "没有匹配的会话")
        } else {
            LazyColumn {
                items(shown, key = { it.address }) { conversation ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(conversation) }
                            .padding(vertical = 10.dp),
                    ) {
                        Row {
                            Text(
                                conversation.displayAddress,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatMessageDate(conversation.dateMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            conversation.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${conversation.messageCount} 条" +
                                if (conversation.unreadCount > 0) " · ${conversation.unreadCount} 条未读" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadScreen(
    modifier: Modifier = Modifier,
    address: String,
    threadMessages: List<SmsMessage>,
    hasSendPermission: Boolean,
    onRequestSendPermission: () -> Unit,
    onSend: (String) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (threadMessages.isEmpty()) {
            EmptyState("该会话暂无短信")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
            ) {
                items(threadMessages.reversed(), key = { it.id }) { message ->
                    val outgoing = message.type == 2
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (outgoing) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(message.body)
                                Text(
                                    formatMessageDate(message.dateMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("输入短信内容") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    if (!hasSendPermission) {
                        onRequestSendPermission()
                    } else if (body.isNotBlank()) {
                        onSend(body)
                        body = ""
                    }
                },
            ) { Icon(Icons.Filled.Send, contentDescription = "发送") }
        }
        if (!hasSendPermission) {
            Text(
                "发送短信需要“发送短信”权限；点发送按钮时会先申请权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "送达报告取决于运营商与设备；本应用未声明为默认短信应用，相关行为以系统实际处理为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ComposeScreen(
    modifier: Modifier = Modifier,
    onPickContact: () -> Unit,
    hasSendPermission: Boolean,
    onRequestSendPermission: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("收件人号码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onPickContact, modifier = Modifier.fillMaxWidth()) {
            Text("从联系人中选择")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("短信内容") },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (!hasSendPermission) {
                    onRequestSendPermission()
                } else if (address.isNotBlank() && body.isNotBlank()) {
                    onSend(address, body)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("发送") }
    }
}
