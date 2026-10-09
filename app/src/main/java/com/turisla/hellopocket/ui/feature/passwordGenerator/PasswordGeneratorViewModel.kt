package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.GeneratorRepository
import com.turisla.hellopocket.model.GeneratorMode
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSelection
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.model.GeneratorTemplate
import com.turisla.hellopocket.utils.GeneratorAnalysis
import com.turisla.hellopocket.utils.PasswordGenerator
import com.turisla.hellopocket.utils.RulePasswordGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RandomGeneratorOptions(
    val length: Int = 12,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = false,
    val avoidConfusing: Boolean = false,
)

data class PasswordGeneratorState(
    val mode: GeneratorMode = GeneratorMode.RANDOM,
    val options: RandomGeneratorOptions = RandomGeneratorOptions(),
    val steps: List<GeneratorStep> = emptyList(),
    val templateId: String? = null,
    val templateName: String = "",
    val password: String = "",
    val analysis: GeneratorAnalysis = RulePasswordGenerator.analyze(emptyList()),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val rememberFailed: Boolean = false,
    val locked: Boolean = false,
)

/** 规则配置由仓库加密记忆；生成结果只存在内存，不进入 SavedStateHandle 或磁盘。 */
class PasswordGeneratorViewModel(private val repository: GeneratorRepository) : ViewModel() {
    private val session = repository.session.value
    private val engine = RulePasswordGenerator()
    private val _state = MutableStateFlow(restoredState())
    val state = _state.asStateFlow()
    val library = repository.data

    init {
        if (_state.value.mode == GeneratorMode.RANDOM) generate()
        viewModelScope.launch {
            repository.session.collect { current ->
                if (current == null || current != session) clear()
            }
        }
        viewModelScope.launch {
            repository.rememberFailure.collect { failedSession ->
                if (isSessionActive()) _state.update { it.copy(rememberFailed = failedSession == session) }
            }
        }
    }

    private fun restoredState(): PasswordGeneratorState {
        val active = session ?: return PasswordGeneratorState(locked = true)
        val selection = repository.lastSelection(active)
        val template = repository.data.value.templates.find { it.id == selection.templateId }
        return PasswordGeneratorState(
            mode = selection.mode, steps = selection.steps,
            templateId = template?.id, templateName = template?.name.orEmpty(),
            analysis = RulePasswordGenerator.analyze(selection.steps),
        )
    }

    fun rememberCurrentSelection() {
        if (!isSessionActive()) return
        val current = _state.value
        repository.rememberSelection(requireNotNull(session), GeneratorSelection(current.mode, current.templateId, current.steps))
    }

    fun flushSelection() {
        if (isSessionActive()) repository.flushSelection(requireNotNull(session))
    }

    fun isSessionActive(): Boolean {
        val active = session != null && session == repository.session.value && !_state.value.locked
        if (!active) clear()
        return active
    }

    fun setMode(mode: GeneratorMode) {
        if (!isSessionActive() || _state.value.mode == mode) return
        _state.update { it.copy(mode = mode, password = "") }
        rememberCurrentSelection()
        if (mode == GeneratorMode.RANDOM) generate()
    }

    fun setOptions(options: RandomGeneratorOptions) {
        if (!isSessionActive()) return
        _state.update { it.copy(options = options, password = "") }
        generate()
    }

    fun generate() {
        if (!isSessionActive()) return
        val current = _state.value
        val password = if (current.mode == GeneratorMode.RULES) {
            if (current.analysis.issue != null) "" else engine.generate(current.steps)
        } else {
            val options = current.options
            if (!options.uppercase && !options.lowercase && !options.digits && !options.symbols) ""
            else PasswordGenerator.generatePassword(
                length = options.length,
                includeUppercase = options.uppercase,
                includeLowercase = options.lowercase,
                includeNumbers = options.digits,
                includeSymbols = options.symbols,
                avoidConfusing = options.avoidConfusing,
            )
        }
        if (isSessionActive()) _state.update { it.copy(password = password) }
    }

    fun currentPassword(): String = if (isSessionActive()) _state.value.password else ""

    private fun changeSteps(steps: List<GeneratorStep>) {
        if (!isSessionActive()) return
        _state.update { it.copy(steps = steps, password = "", analysis = RulePasswordGenerator.analyze(steps)) }
        rememberCurrentSelection()
    }

    fun addRules(rules: List<GeneratorRule>) {
        changeSteps(_state.value.steps + rules.map { GeneratorStep(UUID.randomUUID().toString(), it.name, it.segment) })
    }

    fun updateStep(id: String, rule: GeneratorRule) {
        changeSteps(_state.value.steps.map { if (it.id == id) it.copy(name = rule.name, segment = rule.segment) else it })
    }

    fun duplicateStep(id: String) {
        val steps = _state.value.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == id }
        if (index < 0) return
        steps.add(index + 1, steps[index].copy(id = UUID.randomUUID().toString()))
        changeSteps(steps)
    }

    fun removeStep(id: String) = changeSteps(_state.value.steps.filterNot { it.id == id })

    fun moveStep(id: String, targetIndex: Int) {
        val steps = _state.value.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == id }
        if (index < 0 || targetIndex !in steps.indices || index == targetIndex) return
        steps.add(targetIndex, steps.removeAt(index))
        changeSteps(steps)
    }

    fun useTemplate(template: GeneratorTemplate) {
        if (!isSessionActive()) return
        _state.update { it.copy(mode = GeneratorMode.RULES, templateId = template.id, templateName = template.name) }
        changeSteps(template.steps.map { it.copy(id = UUID.randomUUID().toString()) })
    }

    fun clearRules() {
        if (!isSessionActive()) return
        _state.update { it.copy(templateId = null, templateName = "") }
        changeSteps(emptyList())
    }

    fun saveRule(rule: GeneratorRule, onSaved: () -> Unit = {}) = persist(onSaved) {
        repository.saveRule(requireNotNull(session), rule)
    }

    fun deleteRule(id: String) = persist { repository.deleteRule(requireNotNull(session), id) }

    fun saveTemplate(name: String, replace: Boolean, onSaved: () -> Unit) {
        val current = _state.value
        if (current.analysis.issue != null) return
        val template = GeneratorTemplate(
            id = current.templateId.takeIf { replace } ?: UUID.randomUUID().toString(),
            name = name.trim(), steps = current.steps.toList(),
        )
        persist(onSaved = {
            _state.update { it.copy(templateId = template.id, templateName = template.name) }
            rememberCurrentSelection()
            onSaved()
        }) { repository.saveTemplate(requireNotNull(session), template) }
    }

    fun deleteTemplate(id: String) = persist(onSaved = {
        if (_state.value.templateId == id) _state.update { it.copy(templateId = null, templateName = "") }
        rememberCurrentSelection()
    }) { repository.deleteTemplate(requireNotNull(session), id) }

    private fun persist(onSaved: () -> Unit = {}, operation: suspend () -> Unit) {
        if (!isSessionActive() || _state.value.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                operation()
                if (isSessionActive()) onSaved()
            } catch (error: CancellationException) {
                if (repository.session.value != session) clear()
                throw error
            } catch (_: Exception) {
                // 不记录异常参数，以免规则内容被第三方序列化错误带入日志。
                if (isSessionActive()) _state.update { it.copy(saveFailed = true) }
            } finally {
                if (isSessionActive()) _state.update { it.copy(saving = false) }
            }
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveFailed = false) }

    private fun clear() { _state.value = PasswordGeneratorState(locked = true) }

    override fun onCleared() {
        flushSelection()
        clear()
        super.onCleared()
    }
}
