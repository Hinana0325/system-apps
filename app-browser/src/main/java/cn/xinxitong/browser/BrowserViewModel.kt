package cn.xinxitong.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val store = BrowserStore(application)

    val history: StateFlow<List<HistoryEntry>> = store.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = store.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordHistory(entry: HistoryEntry) {
        viewModelScope.launch { store.recordHistory(entry) }
    }

    fun clearHistory() {
        viewModelScope.launch { store.clearHistory() }
    }

    fun addBookmark(bookmark: Bookmark) {
        viewModelScope.launch { store.addBookmark(bookmark) }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { store.removeBookmark(url) }
    }
}
