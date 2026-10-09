package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSelection
import com.turisla.hellopocket.model.GeneratorTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** 所有落盘操作交给保险库事务层，规则库不会产生另一份明文偏好文件。 */
class GeneratorRepository(
    private val vault: PasswordRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AutoCloseable {
    val data = vault.generatorData
    val session = vault.unlockedSession

    private data class SelectionWrite(val session: Long, val revision: Long, val selection: GeneratorSelection)
    private val revision = AtomicLong()
    private val remembered = MutableStateFlow<SelectionWrite?>(null)
    private val pending = MutableStateFlow<SelectionWrite?>(null)
    private val selectionMutex = Mutex()
    private val _rememberFailure = MutableStateFlow<Long?>(null)
    val rememberFailure = _rememberFailure.asStateFlow()

    // 写入任务归仓库所有，页面关闭不会取消保存；锁库则取消旧会话的任务并释放草稿。
    @OptIn(FlowPreview::class)
    private val selectionWriter = scope.launch(start = CoroutineStart.LAZY) {
        session.collectLatest { active ->
            remembered.update { it?.takeIf { request -> request.session == session.value } }
            pending.update { it?.takeIf { request -> request.session == session.value } }
            _rememberFailure.value = null
            if (active != null && session.value == active) {
                pending.filterNotNull().debounce(250).collect { request ->
                    if (request.session == active) persistPendingSelection(active)
                }
            }
        }
    }

    fun lastSelection(expectedSession: Long): GeneratorSelection {
        if (session.value != expectedSession) return GeneratorSelection()
        val selection = remembered.value?.takeIf { it.session == expectedSession }?.selection
            ?: data.value.lastSelection
        return selection.copy(templateId = selection.templateId?.takeIf { id -> data.value.templates.any { it.id == id } })
    }

    fun rememberSelection(expectedSession: Long, selection: GeneratorSelection) {
        if (session.value != expectedSession) return
        val request = SelectionWrite(expectedSession, revision.incrementAndGet(), selection.copy(steps = selection.steps.toList()))
        remembered.value = request
        pending.value = request
        _rememberFailure.value = null
        selectionWriter.start()
    }

    /** 退出页面或退到后台时跳过防抖，仍在仓库作用域内完成加密事务。 */
    fun flushSelection(expectedSession: Long) = scope.launch { persistPendingSelection(expectedSession) }

    private suspend fun persistPendingSelection(expectedSession: Long) = selectionMutex.withLock {
        val request = pending.value?.takeIf { it.session == expectedSession } ?: return@withLock
        if (session.value != expectedSession) return@withLock
        try {
            vault.updateGeneratorData(expectedSession) { current ->
                current.copy(lastSelection = request.selection.copy(
                    templateId = request.selection.templateId?.takeIf { id -> current.templates.any { it.id == id } },
                ))
            }
            pending.compareAndSet(request, null)
            if (session.value == expectedSession) _rememberFailure.value = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 不记录包含固定片段的异常；保留最新草稿，允许页面提示重试。
            if (session.value == expectedSession) _rememberFailure.value = expectedSession
        }
    }

    suspend fun saveRule(session: Long, rule: GeneratorRule) = vault.updateGeneratorData(session) { data ->
        val exists = data.rules.any { it.id == rule.id }
        data.copy(rules = if (exists) data.rules.map { if (it.id == rule.id) rule else it } else data.rules + rule)
    }

    suspend fun deleteRule(session: Long, id: String) = vault.updateGeneratorData(session) {
        it.copy(rules = it.rules.filterNot { rule -> rule.id == id })
    }

    suspend fun saveTemplate(session: Long, template: GeneratorTemplate) = vault.updateGeneratorData(session) { data ->
        val snapshot = template.copy(steps = template.steps.toList())
        val exists = data.templates.any { it.id == template.id }
        data.copy(templates = if (exists) {
            data.templates.map { if (it.id == template.id) snapshot else it }
        } else data.templates + snapshot)
    }

    suspend fun deleteTemplate(session: Long, id: String) = vault.updateGeneratorData(session) {
        it.copy(
            templates = it.templates.filterNot { template -> template.id == id },
            lastSelection = if (it.lastSelection.templateId == id) it.lastSelection.copy(templateId = null) else it.lastSelection,
        )
    }

    override fun close() {
        scope.cancel()
        remembered.value = null
        pending.value = null
        _rememberFailure.value = null
    }
}
