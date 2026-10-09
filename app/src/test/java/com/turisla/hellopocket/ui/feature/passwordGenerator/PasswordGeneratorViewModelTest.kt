package com.turisla.hellopocket.ui.feature.passwordGenerator

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.turisla.hellopocket.data.GeneratorRepository
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.model.GeneratorMode
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorSelection
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.model.GeneratorTemplate
import com.turisla.hellopocket.security.TinkCryptoManager
import com.turisla.hellopocket.security.VaultSessionGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class PasswordGeneratorViewModelTest {
    private val store = ViewModelStore()
    private val repositoryJobs = mutableListOf<Job>()

    private fun repository(vault: PasswordRepository): GeneratorRepository {
        val job = SupervisorJob().also(repositoryJobs::add)
        return GeneratorRepository(vault, CoroutineScope(job + Dispatchers.IO))
    }

    private fun viewModel(repository: GeneratorRepository): PasswordGeneratorViewModel {
        val viewModel = PasswordGeneratorViewModel(repository)
        store.put("generator", viewModel)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    @Before
    fun prepare() { clearFiles() }

    @After
    fun finish() = runBlocking {
        store.clear()
        repositoryJobs.forEach { it.cancelAndJoin() }
        clearFiles()
    }

    private fun clearFiles() {
        RuntimeEnvironment.getApplication().filesDir.listFiles()?.forEach(File::deleteRecursively)
        RuntimeEnvironment.getApplication().cacheDir.listFiles()?.forEach(File::deleteRecursively)
    }

    @Test
    fun compositionEditsInvalidateResultsAndLocksReleaseSensitiveDrafts() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("generator-viewmodel-password")
        val viewModel = viewModel(repository(vault))
        assertTrue(viewModel.currentPassword().isNotEmpty())
        viewModel.setMode(GeneratorMode.RULES)
        val word = GeneratorRule("word", "Word", GeneratorSegment.Text("Maple@"))
        val digits = GeneratorRule("digits", "Digits", GeneratorSegment.Digits(6))
        viewModel.addRules(listOf(word, digits))
        viewModel.generate()
        assertTrue(viewModel.currentPassword().startsWith("Maple@"))
        val digitId = viewModel.state.value.steps.last().id
        viewModel.moveStep(digitId, 0)
        assertEquals("", viewModel.currentPassword())
        viewModel.generate()
        assertTrue(viewModel.currentPassword().endsWith("Maple@"))
        viewModel.duplicateStep(digitId)
        assertEquals("", viewModel.currentPassword())
        assertEquals(3, viewModel.state.value.steps.map { it.id }.toSet().size)
        viewModel.generate()
        vault.lock()
        // 即使 UI 尚未消费锁定通知，系统菜单的晚到复制回调也必须立即被拒绝。
        assertFalse(viewModel.isSessionActive())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.state.value.locked)
        assertEquals("", viewModel.state.value.password)
        assertTrue(viewModel.state.value.steps.isEmpty())
        vault.loadAndDecryptData("generator-viewmodel-password")
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.addRules(listOf(word, digits))
        viewModel.generate()
        assertEquals("", viewModel.currentPassword())
        assertTrue(viewModel.state.value.steps.isEmpty())
    }

    @Test
    fun userCanAddMoreThanTwentyFragmentsAndOnlyLengthBlocksGeneration() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("generator-count-password")
        val repository = repository(vault)
        val viewModel = viewModel(repository)
        viewModel.setMode(GeneratorMode.RULES)
        val digit = GeneratorRule("digit", "Digit", GeneratorSegment.Digits(1))
        viewModel.addRules(List(100) { digit })
        viewModel.generate()
        assertEquals(100, viewModel.state.value.steps.size)
        assertEquals(100, viewModel.currentPassword().length)
        viewModel.addRules(List(100) { digit })
        assertEquals(200, viewModel.state.value.steps.size)
        assertEquals(200, viewModel.state.value.analysis.length)
        assertFalse(viewModel.state.value.analysis.issue == null)
        viewModel.generate()
        assertEquals("", viewModel.currentPassword())
        repository.flushSelection(requireNotNull(vault.unlockedSession.value)).join()
        assertNull(repository.rememberFailure.value)
        vault.lock()
        vault.loadAndDecryptData("generator-count-password")
        assertEquals(200, viewModel(repository(vault)).state.value.steps.size)
    }

    @Test
    fun unsavedOrderSurvivesPageExitAndColdUnlockWithoutRestoringPassword() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("remember-composition-password")
        val repository = repository(vault)
        val viewModel = viewModel(repository)
        viewModel.setMode(GeneratorMode.RULES)
        viewModel.addRules(listOf(
            GeneratorRule("word", "Word", GeneratorSegment.Text("PrivatePrefixMaple")),
            GeneratorRule("symbol", "Separator", GeneratorSegment.Text("@")),
            GeneratorRule("digits", "Digits", GeneratorSegment.Digits(6)),
        ))
        val digitId = viewModel.state.value.steps.last().id
        viewModel.moveStep(digitId, 0)
        viewModel.duplicateStep(digitId)
        viewModel.generate()
        assertTrue(viewModel.currentPassword().isNotEmpty())
        val expected = viewModel.state.value.steps
        // 防抖还未落盘时，再次打开也应取得最新草稿；销毁页面不能取消保存。
        val reopened = viewModel(repository)
        assertEquals(expected, reopened.state.value.steps)
        assertEquals("", reopened.currentPassword())
        store.clear()
        repository.flushSelection(requireNotNull(vault.unlockedSession.value)).join()
        assertNull(repository.rememberFailure.value)
        val encryptedRules = File(RuntimeEnvironment.getApplication().filesDir, "hellopocket_vault/generator_rules.dat")
        assertTrue(encryptedRules.isFile)
        assertFalse(encryptedRules.readBytes().toString(Charsets.UTF_8).contains("PrivatePrefixMaple"))
        vault.lock()
        vault.loadAndDecryptData("remember-composition-password")
        val restored = viewModel(repository(vault)).state.value
        assertEquals(GeneratorMode.RULES, restored.mode)
        assertEquals(expected, restored.steps)
        assertNull(restored.templateId)
        assertEquals("", restored.password)
    }

    @Test
    fun rememberedRandomModeRetainsTheLastRuleComposition() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("remember-mode-password")
        val repository = repository(vault)
        val viewModel = viewModel(repository)
        viewModel.setMode(GeneratorMode.RULES)
        viewModel.addRules(listOf(GeneratorRule("digits", "Digits", GeneratorSegment.Digits(8))))
        val expected = viewModel.state.value.steps
        viewModel.setMode(GeneratorMode.RANDOM)
        repository.flushSelection(requireNotNull(vault.unlockedSession.value)).join()
        vault.lock()
        vault.loadAndDecryptData("remember-mode-password")
        val restored = viewModel(repository(vault))
        assertEquals(GeneratorMode.RANDOM, restored.state.value.mode)
        assertTrue(restored.currentPassword().isNotEmpty())
        restored.setMode(GeneratorMode.RULES)
        assertEquals(expected, restored.state.value.steps)
        assertEquals("", restored.currentPassword())
        restored.generate()
        assertTrue(restored.currentPassword().matches(Regex("[0-9]{8}")))
    }

    @Test
    fun editedTemplateSnapshotIsRememberedAndClearRemovesItsAssociation() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("remember-template-password")
        val repository = repository(vault)
        val session = requireNotNull(vault.unlockedSession.value)
        val template = GeneratorTemplate("template", "My template", listOf(
            GeneratorStep("word", "Word", GeneratorSegment.Text("Maple")),
            GeneratorStep("digits", "Digits", GeneratorSegment.Digits(6)),
        ))
        repository.saveTemplate(session, template)
        val viewModel = viewModel(repository)
        viewModel.useTemplate(template)
        viewModel.updateStep(viewModel.state.value.steps.first().id, GeneratorRule("word", "Edited word", GeneratorSegment.Text("Birch@")))
        val expected = viewModel.state.value.steps
        repository.flushSelection(session).join()
        vault.lock()
        vault.loadAndDecryptData("remember-template-password")
        val coldRepository = repository(vault)
        val restored = viewModel(coldRepository)
        assertEquals(template.id, restored.state.value.templateId)
        assertEquals(template.name, restored.state.value.templateName)
        assertEquals(expected, restored.state.value.steps)
        assertEquals(template, coldRepository.data.value.templates.single())
        restored.clearRules()
        coldRepository.flushSelection(requireNotNull(vault.unlockedSession.value)).join()
        vault.lock()
        vault.loadAndDecryptData("remember-template-password")
        val cleared = viewModel(repository(vault)).state.value
        assertEquals(GeneratorMode.RULES, cleared.mode)
        assertTrue(cleared.steps.isEmpty())
        assertNull(cleared.templateId)
        assertEquals("", cleared.templateName)
        assertEquals(template, vault.generatorData.value.templates.single())
    }

    @Test
    fun deletingSelectedTemplatePreservesRememberedStepsWithoutDanglingReference() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("delete-template-password")
        val repository = repository(vault)
        val session = requireNotNull(vault.unlockedSession.value)
        val template = GeneratorTemplate("template", "My template", listOf(GeneratorStep("digits", "Digits", GeneratorSegment.Digits(6))))
        repository.saveTemplate(session, template)
        val viewModel = viewModel(repository)
        viewModel.useTemplate(template)
        val expected = viewModel.state.value.steps
        // 删除时草稿可能仍在等待写入，保存端也必须移除失效的模板 ID。
        repository.deleteTemplate(session, template.id)
        repository.flushSelection(session).join()
        assertNull(repository.rememberFailure.value)
        assertNull(viewModel(repository).state.value.templateId)
        vault.lock()
        vault.loadAndDecryptData("delete-template-password")
        val restored = viewModel(repository(vault)).state.value
        assertNull(restored.templateId)
        assertEquals(expected, restored.steps)
    }

    @Test
    fun oldSessionCannotReplaceTheNewSelectionAfterUnlock() = runBlocking {
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(RuntimeEnvironment.getApplication(), TinkCryptoManager(), guard)
        vault.setupNewVault("selection-session-password")
        val repository = repository(vault)
        val oldSession = requireNotNull(vault.unlockedSession.value)
        vault.lock()
        vault.loadAndDecryptData("selection-session-password")
        val newSession = requireNotNull(vault.unlockedSession.value)
        val latest = GeneratorSelection(GeneratorMode.RULES, steps = listOf(GeneratorStep("new", "New", GeneratorSegment.Text("NewDraft"))))
        repository.rememberSelection(newSession, latest)
        repository.rememberSelection(oldSession, GeneratorSelection())
        repository.flushSelection(oldSession).join()
        repository.flushSelection(newSession).join()
        assertNull(repository.rememberFailure.value)
        assertEquals(latest, repository.lastSelection(newSession))
        vault.lock()
        vault.loadAndDecryptData("selection-session-password")
        assertEquals(latest, vault.generatorData.value.lastSelection)
    }
}
