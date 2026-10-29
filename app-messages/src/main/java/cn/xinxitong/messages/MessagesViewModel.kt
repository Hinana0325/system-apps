package cn.xinxitong.messages

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val ACTION_SMS_SENT = "cn.xinxitong.messages.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "cn.xinxitong.messages.SMS_DELIVERED"
    }

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sendStatus = MutableStateFlow<String?>(null)
    val sendStatus: StateFlow<String?> = _sendStatus.asStateFlow()

    /** 从系统短信数据库读取全部短信（收/发）。在没有电话功能的设备上查询结果为空。 */
    fun loadMessages() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val projection = arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.THREAD_ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ,
                    )
                    val result = mutableListOf<SmsMessage>()
                    resolver.query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC LIMIT 1000",
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                        val threadIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                        val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                        val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                        val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                        val readIndex = cursor.getColumnIndex(Telephony.Sms.READ)
                        while (cursor.moveToNext()) {
                            result.add(
                                SmsMessage(
                                    id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                                    threadId = if (threadIndex >= 0) cursor.getLong(threadIndex) else 0L,
                                    address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else "",
                                    body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else "",
                                    dateMillis = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L,
                                    type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 0,
                                    read = if (readIndex >= 0) cursor.getInt(readIndex) == 1 else true,
                                ),
                            )
                        }
                    }
                    result
                }
            }.onSuccess { _messages.value = it }
                .onFailure { _error.value = "读取短信失败：${it.message}" }
            _loading.value = false
        }
    }

    /**
     * 用 SmsManager 发送短信，长短信自动分段。
     * 注意：是否真正发出、送达报告是否可用，取决于设备是否有电话功能以及本应用
     * 是否为默认短信应用；这里只如实报告 SmsManager 的调用结果与系统回执，
     * 不宣称默认短信应用行为。
     */
    fun sendSms(context: Context, address: String, body: String) {
        val number = address.trim()
        if (number.isBlank() || body.isBlank()) {
            _sendStatus.value = "号码和内容不能为空"
            return
        }
        runCatching {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: error("此设备没有可用的短信服务")
            val sentIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_SMS_SENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val deliveredIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_SMS_DELIVERED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val parts = smsManager.divideMessage(body)
            if (parts.size <= 1) {
                smsManager.sendTextMessage(number, null, body, sentIntent, deliveredIntent)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.map { sentIntent })
                val deliveredIntents = ArrayList<PendingIntent>(parts.map { deliveredIntent })
                smsManager.sendMultipartTextMessage(number, null, parts, sentIntents, deliveredIntents)
            }
            _sendStatus.value = "已提交发送（${parts.size} 条），等待系统回执"
        }.onFailure {
            _sendStatus.value = "发送失败：${it.message}"
        }
    }

    fun onSmsSent(resultCode: Int) {
        _sendStatus.value = when (resultCode) {
            android.app.Activity.RESULT_OK -> "短信已发送到运营商"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "发送失败：通用错误"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "发送失败：无服务"
            SmsManager.RESULT_ERROR_NULL_PDU -> "发送失败：空数据"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "发送失败：无线电已关闭"
            else -> "发送回执：未知结果（$resultCode）"
        }
        loadMessages()
    }

    fun onSmsDelivered(resultCode: Int) {
        if (resultCode == android.app.Activity.RESULT_OK) {
            _sendStatus.value = "短信已送达对方设备"
        }
    }

    fun consumeError() {
        _error.value = null
    }

    fun consumeSendStatus() {
        _sendStatus.value = null
    }
}
