package com.turisla.hellopocket.utils

import com.turisla.hellopocket.model.GeneratorLetterCase
import com.turisla.hellopocket.model.GeneratorMode
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorSelection
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.model.GeneratorTemplate
import com.turisla.hellopocket.model.GeneratorVaultData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import kotlin.math.log2

class RulePasswordGeneratorTest {
    private fun step(id: String, segment: GeneratorSegment) = GeneratorStep(id, id, segment)

    @Test
    fun fixedPartsStayInOrderAndDuplicateRandomRulesDrawIndependently() {
        val random = object : SecureRandom() {
            var draw = 0
            override fun nextInt(bound: Int): Int = (draw++).mod(bound)
        }
        val engine = RulePasswordGenerator(random)
        val steps = listOf(
            step("word", GeneratorSegment.Text("Maple")),
            step("separator", GeneratorSegment.Text("@")),
            step("first", GeneratorSegment.Digits(3)),
            step("second", GeneratorSegment.Digits(3)),
        )
        assertEquals("Maple@012345", engine.generate(steps))
        assertEquals("Maple@678901", engine.generate(steps))
        assertEquals("234567@Maple", engine.generate(steps.reversed()))
    }

    @Test
    fun strengthCountsOnlyEffectiveRandomChoices() {
        val steps = listOf(step("word", GeneratorSegment.Text("Maple@")), step("digits", GeneratorSegment.Digits(6)))
        assertEquals(6 * log2(10.0), RulePasswordGenerator.analyze(steps).entropyBits, 0.00001)
        assertEquals(12, RulePasswordGenerator.analyze(steps).length)
        val symbols = listOf(step("symbols", GeneratorSegment.Symbols(2, "!!!@")))
        assertEquals(2.0, RulePasswordGenerator.analyze(symbols).entropyBits, 0.00001)
        assertEquals(GeneratorIssue.NO_RANDOMNESS, RulePasswordGenerator.analyze(listOf(step("same", GeneratorSegment.Symbols(20, "@@@")))).issue)
    }

    @Test
    fun confusingCharactersAreExcludedAndSymbolsComeOnlyFromAllowedSet() {
        val letters = GeneratorSegment.Letters(80, GeneratorLetterCase.MIXED, avoidConfusing = true)
        val digits = GeneratorSegment.Digits(20, avoidConfusing = true)
        val symbols = GeneratorSegment.Symbols(20, "!@#")
        val engine = RulePasswordGenerator()
        repeat(30) {
            val output = engine.generate(listOf(step("a", letters), step("b", digits), step("c", symbols)))
            assertEquals(120, output.length)
            assertFalse(output.take(100).any { it in "O0oIl1|" })
            assertTrue(output.take(80).all(Char::isLetter))
            assertTrue(output.substring(80, 100).all(Char::isDigit))
            assertTrue(output.takeLast(20).all { it in "!@#" })
        }
    }

    @Test
    fun fragmentCountHasNoArtificialCapAndOutputLengthIsValidatedSeparately() {
        val steps = List(100) { step("part-$it", GeneratorSegment.Digits(1)) }
        assertNull(RulePasswordGenerator.analyze(steps).issue)
        assertEquals(100, RulePasswordGenerator().generate(steps).length)
        val longSteps = List(1000) { step("part-$it", GeneratorSegment.Digits(1)) }
        assertEquals(1000, RulePasswordGenerator.analyze(longSteps).length)
        assertEquals(GeneratorIssue.TOO_LONG, RulePasswordGenerator.analyze(longSteps).issue)
        assertThrows(IllegalArgumentException::class.java) { RulePasswordGenerator().generate(longSteps) }
    }

    @Test
    fun invalidAndDeterministicCompositionsAreRejected() {
        assertEquals(GeneratorIssue.EMPTY_COMPOSITION, RulePasswordGenerator.analyze(emptyList()).issue)
        assertEquals(GeneratorIssue.NO_RANDOMNESS, RulePasswordGenerator.analyze(listOf(step("fixed", GeneratorSegment.Text("LongButFixed!123")))).issue)
        assertEquals(GeneratorIssue.INVALID_LENGTH, RulePasswordGenerator.validateSegment(GeneratorSegment.Digits(Int.MAX_VALUE)))
        assertEquals(GeneratorIssue.EMPTY_ALPHABET, RulePasswordGenerator.validateSegment(GeneratorSegment.Symbols(1, "")))
        assertEquals(GeneratorIssue.INVALID_SYMBOLS, RulePasswordGenerator.validateSegment(GeneratorSegment.Symbols(1, "!a ")))
        assertEquals(GeneratorIssue.UNSUPPORTED_TEXT, RulePasswordGenerator.validateSegment(GeneratorSegment.Text("word\n")))
        assertEquals(GeneratorIssue.UNSUPPORTED_TEXT, RulePasswordGenerator.validateSegment(GeneratorSegment.Text("\uD800")))
        val unicode = listOf(step("text", GeneratorSegment.Text("你好😀")), step("digit", GeneratorSegment.Digits(1)))
        assertEquals(4, RulePasswordGenerator.analyze(unicode).length)
        assertTrue(RulePasswordGenerator().generate(unicode).startsWith("你好😀"))
    }

    @Test
    fun storedSnapshotsRoundTripWithOrderAndRejectDuplicateIds() {
        val rule = GeneratorRule("rule", "Digits", GeneratorSegment.Digits(6))
        val template = GeneratorTemplate("template", "Example", listOf(step("1", GeneratorSegment.Text("Maple@")), step("2", rule.segment)))
        val data = GeneratorVaultData(rules = listOf(rule), templates = listOf(template))
        val decoded = Json.decodeFromString<GeneratorVaultData>(Json.encodeToString(data))
        RulePasswordGenerator.validateVaultData(decoded)
        assertEquals(data, decoded)
        assertThrows(IllegalArgumentException::class.java) { RulePasswordGenerator.validateVaultData(data.copy(rules = listOf(rule, rule))) }
        assertThrows(IllegalArgumentException::class.java) {
            RulePasswordGenerator.validateVaultData(data.copy(templates = listOf(template.copy(steps = listOf(template.steps[1], template.steps[1])))))
        }
    }

    @Test
    fun oldGeneratorDataDefaultsToRandomAndDraftsMayBeIncomplete() {
        val oldData = Json.decodeFromString<GeneratorVaultData>("""{"formatVersion":1,"rules":[],"templates":[]}""")
        assertEquals(GeneratorSelection(), oldData.lastSelection)
        val drafts = listOf(
            emptyList(),
            listOf(step("fixed", GeneratorSegment.Text("Maple"))),
            List(200) { step("part-$it", GeneratorSegment.Digits(1)) },
        )
        drafts.forEach { steps ->
            val data = oldData.copy(lastSelection = GeneratorSelection(GeneratorMode.RULES, steps = steps))
            val decoded = Json.decodeFromString<GeneratorVaultData>(Json.encodeToString(data))
            RulePasswordGenerator.validateVaultData(decoded)
            assertEquals(data, decoded)
        }
    }

    @Test
    fun rememberedDraftsStillRejectInvalidRulesAndMissingTemplates() {
        val duplicate = step("same", GeneratorSegment.Digits(1))
        val invalidSelections = listOf(
            GeneratorSelection(templateId = "missing"),
            GeneratorSelection(steps = listOf(duplicate, duplicate)),
            GeneratorSelection(steps = listOf(step("invalid", GeneratorSegment.Digits(-1)))),
            GeneratorSelection(steps = listOf(step("", GeneratorSegment.Text("Maple")))),
        )
        invalidSelections.forEach { selection ->
            assertThrows(IllegalArgumentException::class.java) {
                RulePasswordGenerator.validateVaultData(GeneratorVaultData(lastSelection = selection))
            }
        }
    }
}
