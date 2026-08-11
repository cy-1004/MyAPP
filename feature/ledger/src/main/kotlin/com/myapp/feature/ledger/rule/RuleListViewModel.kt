package com.myapp.feature.ledger.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.ledger.data.RuleRepository
import com.myapp.feature.ledger.notification.CustomRule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹可撤销提示。与纪念日列表同一套约定。 */
data class RuleUndoDeleteEvent(val rule: CustomRule)

@HiltViewModel
class RuleListViewModel @Inject constructor(
    private val repository: RuleRepository,
) : ViewModel() {

    val customRules: StateFlow<List<CustomRule>> = repository.customRules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val disabledBuiltinIds: StateFlow<Set<String>> = repository.disabledBuiltinIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

    private val _undoEvents = Channel<RuleUndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<RuleUndoDeleteEvent> = _undoEvents.receiveAsFlow()

    fun delete(rule: CustomRule) {
        viewModelScope.launch {
            repository.delete(rule.id)
            _undoEvents.send(RuleUndoDeleteEvent(rule))
        }
    }

    fun undoDelete(rule: CustomRule) {
        viewModelScope.launch { repository.restore(rule) }
    }

    fun toggleBuiltin(id: String, on: Boolean) {
        viewModelScope.launch { repository.toggleBuiltin(id, on) }
    }
}
