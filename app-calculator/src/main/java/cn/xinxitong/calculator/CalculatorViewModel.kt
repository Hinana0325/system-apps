package cn.xinxitong.calculator

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 一条计算历史：表达式、结果文本、计算时间。 */
@Serializable
data class CalculationHistory(
    val expression: String,
    val result: String,
    val timeMillis: Long,
)

private val calculatorJson = Json { ignoreUnknownKeys = true }

private val Context.calculatorDataStore: DataStore<Preferences> by preferencesDataStore(name = "calculator_prefs")
private val HISTORY_KEY = stringPreferencesKey("history_json")

const val MAX_CALCULATOR_HISTORY: Int = 50

/** 历史去重：保留最新一条相同表达式，最多保留上限。纯函数。 */
fun dedupCalculatorHistory(
    entries: List<CalculationHistory>,
    maxHistory: Int = MAX_CALCULATOR_HISTORY,
): List<CalculationHistory> {
    val seen = LinkedHashSet<String>()
    val result = ArrayList<CalculationHistory>()
    for (entry in entries.sortedByDescending { it.timeMillis }) {
        if (seen.add(entry.expression)) result.add(entry)
        if (result.size >= maxHistory) break
    }
    return result
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    /** 实时预览结果：仅当表达式完整且合法时显示。 */
    val preview: StateFlow<String> = _expression.map { expr ->
        if (!isCompleteExpression(expr)) "" else when (val r = evaluateExpression(expr)) {
            is EvalResult.Success -> r.formatted
            is EvalResult.Failure -> ""
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _justEvaluated = MutableStateFlow(false)
    val justEvaluated: StateFlow<Boolean> = _justEvaluated.asStateFlow()

    val history: StateFlow<List<CalculationHistory>> = context.calculatorDataStore.data
        .map { prefs ->
            val raw = prefs[HISTORY_KEY].orEmpty()
            if (raw.isBlank()) emptyList() else runCatching {
                calculatorJson.decodeFromString<List<CalculationHistory>>(raw)
            }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onKey(key: String) {
        if (_justEvaluated.value && (key.firstOrNull()?.isDigit() == true || key == "." || key == "(")) {
            _expression.value = ""
            _justEvaluated.value = false
        }
        if (key == "C") {
            _expression.value = ""
            _error.value = null
            _justEvaluated.value = false
            return
        }
        if (key == "⌫") {
            _expression.value = backspace(_expression.value)
            _error.value = null
            _justEvaluated.value = false
            return
        }
        _expression.value = appendKey(_expression.value, key)
        _error.value = null
        _justEvaluated.value = false
    }

    fun onEquals() {
        val expr = _expression.value
        when (val result = evaluateExpression(expr)) {
            is EvalResult.Success -> {
                _error.value = null
                _expression.value = result.formatted
                _justEvaluated.value = true
                viewModelScope.launch {
                    val current = history.value
                    val updated = dedupCalculatorHistory(
                        listOf(
                            CalculationHistory(
                                expression = expr,
                                result = result.formatted,
                                timeMillis = System.currentTimeMillis(),
                            ),
                        ) + current,
                    )
                    context.calculatorDataStore.edit { prefs ->
                        prefs[HISTORY_KEY] = calculatorJson.encodeToString(updated)
                    }
                }
            }
            is EvalResult.Failure -> {
                _error.value = result.message
            }
        }
    }

    fun reuseHistory(entry: CalculationHistory) {
        _expression.value = entry.expression
        _error.value = null
        _justEvaluated.value = false
    }

    fun clearHistory() {
        viewModelScope.launch {
            context.calculatorDataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
