package com.remind.widget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remind.widget.core.AlarmScheduler
import com.remind.widget.core.UpdateChecker
import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Event
import com.remind.widget.data.EventType
import com.remind.widget.data.Group
import com.remind.widget.data.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 整个 App 只有一个 ViewModel。
 * 状态全部来自 Room 的 Flow，写操作走 Repository（它内部会顺手重排闹钟 + 刷小组件）。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ReminderRepository.get(app)

    val groups: StateFlow<List<Group>> = repo.groupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<Event>> = repo.eventsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneToday: StateFlow<List<DoneRecord>> = repo.doneTodayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 栏目 ----------
    fun createGroup(name: String, color: Long, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.createGroup(name.ifBlank { "新栏目" }, color)
            onCreated(id)
        }
    }

    fun updateGroup(group: Group) = viewModelScope.launch { repo.updateGroup(group) }

    fun deleteGroup(groupId: Long) = viewModelScope.launch { repo.deleteGroup(groupId) }

    // ---------- 事件 ----------
    fun createEvent(event: Event) = viewModelScope.launch { repo.createEvent(event) }

    fun updateEvent(event: Event) = viewModelScope.launch { repo.updateEvent(event) }

    fun deleteEvent(eventId: Long) = viewModelScope.launch { repo.deleteEvent(eventId) }

    // ---------- 完成 ----------
    fun complete(eventId: Long) = viewModelScope.launch { repo.completeEvent(eventId) }

    fun undo(recordId: Long) = viewModelScope.launch { repo.undoComplete(recordId) }

    /** 立刻重建闹钟（权限刚被授予时手动触发一次） */
    fun resyncAlarms() = viewModelScope.launch {
        AlarmScheduler.resync(getApplication())
    }

    /** 测试提醒：10 秒后真的响一次，走完整链路 */
    fun testAlarm(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repo.scheduleTestAlarm())
    }

    // ---------- 更新 ----------
    private val _updateState = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    fun checkUpdate() {
        if (_updateState.value is UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            _updateState.value = when (val r = UpdateChecker.checkLatest()) {
                is UpdateChecker.CheckResult.Available -> UpdateState.Available(r.info)
                UpdateChecker.CheckResult.UpToDate -> UpdateState.UpToDate
                is UpdateChecker.CheckResult.Failed -> UpdateState.Failed(r.message)
            }
        }
    }

    fun downloadUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0)
            runCatching {
                UpdateChecker.download(getApplication(), info) { p ->
                    _updateState.value = UpdateState.Downloading(p)
                }
            }.onSuccess { file ->
                _updateState.value = UpdateState.Downloaded(file)
                if (UpdateChecker.hasInstallPermission(getApplication())) {
                    UpdateChecker.install(getApplication(), file)
                }
            }.onFailure {
                _updateState.value = UpdateState.Failed(it.message ?: "下载失败")
            }
        }
    }

    fun installDownloaded() {
        val f = (_updateState.value as? UpdateState.Downloaded)?.file ?: return
        UpdateChecker.install(getApplication(), f)
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    /** 编辑页的时间线预览：把某栏目下的事件按"下次触发"排个序 */
    fun timelineOf(groupEvents: List<Event>): List<Event> = groupEvents.sortedBy {
        com.remind.widget.core.Schedule.nextTrigger(it) ?: java.time.LocalDateTime.MAX
    }

    companion object {
        /** 新建事件时的默认值：每天 09:00 */
        fun defaultEvent(groupId: Long, sortOrder: Int) = Event(
            groupId = groupId,
            name = "",
            type = EventType.DAILY,
            hour = 9,
            minute = 0,
            anchorDate = LocalDate.now().toString(),
            onceDate = LocalDate.now().toString(),
            sortOrder = sortOrder
        )
    }
}

/** 更新检查的界面状态 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateChecker.ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Downloaded(val file: java.io.File) : UpdateState
    data class Failed(val message: String) : UpdateState
}
