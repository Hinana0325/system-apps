package cn.xinxitong.files

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClipboardMode { COPY, MOVE }

data class Clipboard(val sources: List<File>, val mode: ClipboardMode)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentDir = MutableStateFlow<File?>(null)
    val currentDir: StateFlow<File?> = _currentDir.asStateFlow()

    private val _rootDir = MutableStateFlow<File?>(null)
    val rootDir: StateFlow<File?> = _rootDir.asStateFlow()

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FileEntry>?>(null)
    val searchResults: StateFlow<List<FileEntry>?> = _searchResults.asStateFlow()

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _hasAllFilesAccess = MutableStateFlow(false)
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    /** 浏览根目录：有“所有文件访问权限”时用共享存储根目录，否则降级到应用专属目录。 */
    fun storageRoot(context: Application): File {
        return if (Environment.isExternalStorageManager()) {
            Environment.getExternalStorageDirectory()
        } else {
            context.filesDir
        }
    }

    fun quickRoots(context: Application): List<Pair<String, File>> {
        val roots = mutableListOf<Pair<String, File>>()
        if (Environment.isExternalStorageManager()) {
            roots += "内部存储" to Environment.getExternalStorageDirectory()
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists()) roots += "下载" to downloads
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (pictures.exists()) roots += "图片" to pictures
        }
        roots += "应用专属目录" to context.filesDir
        return roots.distinctBy { it.second.absolutePath }
    }

    init {
        refreshAccessState()
        val root = storageRoot(application)
        _rootDir.value = root
        navigateTo(root)
    }

    fun refreshAccessState() {
        _hasAllFilesAccess.value = Environment.isExternalStorageManager()
        val app = getApplication<Application>()
        // 授权状态变化后根目录可能改变：只在当前目录超出新根目录范围时回到根目录。
        val newRoot = storageRoot(app)
        if (_rootDir.value?.absolutePath != newRoot.absolutePath) {
            _rootDir.value = newRoot
            navigateTo(newRoot)
        }
    }

    fun navigateTo(dir: File) {
        if (!dir.isDirectory) return
        _currentDir.value = dir
        _searchQuery.value = ""
        _searchResults.value = null
        refreshEntries()
    }

    fun navigateUp(): Boolean {
        val current = _currentDir.value ?: return false
        val root = _rootDir.value
        if (root != null && current.absolutePath == root.absolutePath) return false
        val parent = current.parentFile ?: return false
        navigateTo(parent)
        return true
    }

    fun canNavigateUp(): Boolean {
        val current = _currentDir.value ?: return false
        val root = _rootDir.value ?: return true
        return current.absolutePath != root.absolutePath && current.parentFile != null
    }

    fun refreshEntries() {
        val dir = _currentDir.value ?: return
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { listDirectory(dir) }
            _entries.value = sortEntries(entries, _sortMode.value)
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        _entries.value = sortEntries(_entries.value, mode)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = null
            return
        }
        val root = _rootDir.value ?: return
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { searchFiles(root, query.trim()) }
            _searchResults.value = results
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
    }

    fun createFolder(name: String) {
        val dir = _currentDir.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains(File.separator)) {
            _message.value = "文件夹名称无效"
            return
        }
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                runCatching { File(dir, trimmed).mkdir() }.getOrDefault(false)
            }
            _message.value = if (created) "已创建文件夹“$trimmed”" else "创建失败：同名项已存在或无权限"
            refreshEntries()
        }
    }

    fun rename(entry: FileEntry, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains(File.separator)) {
            _message.value = "名称无效"
            return
        }
        val target = File(entry.file.parentFile, trimmed)
        if (target.exists()) {
            _message.value = "同名文件已存在"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { entry.file.renameTo(target) }.getOrDefault(false)
            }
            _message.value = if (ok) "已重命名为“$trimmed”" else "重命名失败：无权限或文件正在使用"
            refreshEntries()
        }
    }

    fun delete(entry: FileEntry) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { deleteRecursively(entry.file) }
            _message.value = result.fold(
                onSuccess = { "已删除“${entry.name}”" },
                onFailure = { "删除失败：${it.message}" },
            )
            refreshEntries()
        }
    }

    fun setClipboard(sources: List<File>, mode: ClipboardMode) {
        _clipboard.value = Clipboard(sources, mode)
        _message.value = if (mode == ClipboardMode.COPY) "已复制 ${sources.size} 项，到目标目录点“粘贴”" else "已剪切 ${sources.size} 项，到目标目录点“粘贴”"
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun paste() {
        val clip = _clipboard.value ?: return
        val dir = _currentDir.value ?: return
        viewModelScope.launch {
            var failures = 0
            withContext(Dispatchers.IO) {
                for (source in clip.sources) {
                    val target = File(dir, source.name)
                    if (target.absolutePath == source.absolutePath) {
                        failures++
                        continue
                    }
                    val result = when (clip.mode) {
                        ClipboardMode.COPY -> copyRecursively(source, target)
                        ClipboardMode.MOVE -> moveRecursively(source, target)
                    }
                    if (result.isFailure) failures++
                }
            }
            _message.value = if (failures == 0) {
                if (clip.mode == ClipboardMode.COPY) "粘贴完成" else "移动完成"
            } else {
                "粘贴完成，但有 $failures 项失败（可能同名已存在或无权限）"
            }
            if (clip.mode == ClipboardMode.MOVE) _clipboard.value = null
            refreshEntries()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
