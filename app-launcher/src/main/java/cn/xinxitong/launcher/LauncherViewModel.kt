package cn.xinxitong.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = loadApps(getApplication())
            _loading.value = false
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun filteredApps(): List<AppEntry> {
        val q = _query.value.trim()
        if (q.isEmpty()) return _apps.value
        return _apps.value.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }

    /** 点按启动：优先用系统给的启动 Intent，拿不到时用显式组件 Intent。 */
    fun launchApp(entry: AppEntry) {
        val context = getApplication<Application>()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(entry.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                setClassName(entry.packageName, entry.className)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launchIntent) }
    }

    /** 打开系统“应用信息”页。 */
    fun openAppInfo(entry: AppEntry) {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** 卸载：交给系统 ACTION_DELETE 流程，由用户在系统界面确认。 */
    fun uninstallApp(entry: AppEntry) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
