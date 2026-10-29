package cn.xinxitong.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NoteStore(File(application.filesDir, "notes.json"))

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { withContext(Dispatchers.IO) { store.loadAll() } }
                .onSuccess { _notes.value = it }
                .onFailure { _error.value = "读取备忘录失败：${it.message}" }
            _loading.value = false
        }
    }

    /** 保存新建或编辑后的笔记（空正文不保存）。 */
    fun saveNote(existing: Note?, body: String) {
        if (body.isBlank()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (existing == null) {
                        store.upsert(createNote(body, now))
                    } else {
                        store.upsert(updateNoteBody(existing, body, now))
                    }
                    store.loadAll()
                }
            }.onSuccess { _notes.value = it }
                .onFailure { _error.value = "保存备忘录失败：${it.message}" }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.delete(note.id)
                    store.loadAll()
                }
            }.onSuccess { _notes.value = it }
                .onFailure { _error.value = "删除备忘录失败：${it.message}" }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.togglePin(note.id, System.currentTimeMillis())
                    store.loadAll()
                }
            }.onSuccess { _notes.value = it }
                .onFailure { _error.value = "操作失败：${it.message}" }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
