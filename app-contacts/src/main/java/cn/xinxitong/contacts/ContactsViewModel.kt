package cn.xinxitong.contacts

import android.app.Application
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 读系统联系人：先按联系人读姓名与照片，再分别聚合号码与邮箱。 */
    fun loadContacts() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val contacts = linkedMapOf<Long, MutableContactBuilder>()
                    resolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(
                            ContactsContract.Contacts._ID,
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                        ),
                        null,
                        null,
                        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                        val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                        while (cursor.moveToNext()) {
                            val id = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                            val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else ""
                            val photo = if (photoIndex >= 0) cursor.getString(photoIndex)?.let(Uri::parse) else null
                            contacts[id] = MutableContactBuilder(id, name, photo)
                        }
                    }
                    // 号码
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (cursor.moveToNext()) {
                            val id = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                            val number = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                            if (number.isNotBlank()) contacts[id]?.numbers?.add(number)
                        }
                    }
                    // 邮箱
                    resolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Email.ADDRESS,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                        val addressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        while (cursor.moveToNext()) {
                            val id = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                            val address = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else ""
                            if (address.isNotBlank()) contacts[id]?.emails?.add(address)
                        }
                    }
                    contacts.values.map { builder ->
                        Contact(
                            id = builder.id,
                            displayName = builder.name.ifBlank { "未命名" },
                            photoUri = builder.photoUri,
                            numbers = builder.numbers.distinct(),
                            emails = builder.emails.distinct(),
                        )
                    }.sortedBy { it.displayName }
                }
            }.onSuccess { _contacts.value = it }
                .onFailure { _error.value = "读取联系人失败：${it.message}" }
            _loading.value = false
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private class MutableContactBuilder(
        val id: Long,
        val name: String,
        val photoUri: Uri?,
        val numbers: MutableList<String> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf(),
    )
}
