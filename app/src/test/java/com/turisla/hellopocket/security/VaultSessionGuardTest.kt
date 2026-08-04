package com.turisla.hellopocket.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionGuardTest {
    @Test
    fun `foreground token can publish`() {
        val guard = VaultSessionGuard()
        guard.enterForeground()
        val token = guard.capture()
        var published = false

        val accepted = guard.publishIfValid(token) { published = true }

        assertTrue(accepted)
        assertTrue(published)
    }

    @Test
    fun `background transition rejects in-flight authentication result`() {
        val guard = VaultSessionGuard()
        guard.enterForeground()
        val token = guard.capture()
        guard.enterBackground()
        var published = false

        val accepted = guard.publishIfValid(token) { published = true }

        assertFalse(accepted)
        assertFalse(published)
    }

    @Test
    fun `lock invalidates every previously issued token`() {
        val guard = VaultSessionGuard()
        guard.enterForeground()
        val token = guard.capture()
        guard.invalidate()
        var published = false

        val accepted = guard.publishIfValid(token) { published = true }

        assertFalse(accepted)
        assertFalse(published)
    }
}
