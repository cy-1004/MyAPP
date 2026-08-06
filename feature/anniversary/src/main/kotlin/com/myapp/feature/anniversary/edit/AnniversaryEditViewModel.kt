package com.myapp.feature.anniversary.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.common.time.AppTime
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.anniversary.data.AnniversaryDraft
import com.myapp.feature.anniversary.data.AnniversaryRepeat
import com.myapp.feature.anniversary.data.AnniversaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class EditResult { Saved, Deleted }

@HiltViewModel
class AnniversaryEditViewModel @Inject constructor(
    private val repository: AnniversaryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** id 为 0 表示新建，与待办同一套约定。 */
    private val itemId: Long = savedStateHandle.toRoute<Route.AnniversaryDetail>().id

    private val _draft = MutableStateFlow(AnniversaryDraft(id = itemId))
    val draft: StateFlow<AnniversaryDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(itemId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<EditResult>(Channel.BUFFERED)
    val results: Flow<EditResult> = _results.receiveAsFlow()

    init {
        if (itemId != 0L) {
            viewModelScope.launch {
                _draft.value = repository.loadDraft(itemId)
                _loaded.value = true
            }
        }
    }

    fun updateTitle(value: String) = update { it.copy(title = value) }
    fun updateNote(value: String) = update { it.copy(note = value) }
    fun updateDate(value: LocalDate) = update { it.copy(date = value) }
    fun updateLunar(value: Boolean) = update { it.copy(isLunar = value) }
    fun updateRemindDays(value: Int) = update { it.copy(remindDaysBefore = value) }
    fun updatePinned(value: Boolean) = update { it.copy(pinned = value) }

    /**
     * 切换重复类型时顺带纠正日期：累计型记的是「从哪天开始」，
     * 选一个未来的日期没有意义，直接夹到今天。
     */
    fun updateRepeatType(value: String) = update { draft ->
        val today = AppTime.today()
        val date = if (value == AnniversaryRepeat.CUMULATIVE && draft.date.isAfter(today)) {
            today
        } else {
            draft.date
        }
        draft.copy(repeatType = value, date = date)
    }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(current)
            _results.send(EditResult.Saved)
        }
    }

    fun delete() {
        val id = _draft.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _results.send(EditResult.Deleted)
        }
    }

    private fun update(block: (AnniversaryDraft) -> AnniversaryDraft) {
        _draft.value = block(_draft.value)
    }
}
