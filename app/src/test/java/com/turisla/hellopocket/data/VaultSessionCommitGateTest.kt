package com.turisla.hellopocket.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionCommitGateTest {

    @Test
    fun invalidatedSessionCannotCommit() {
        val gate = VaultSessionCommitGate()
        var isCurrent = true
        var didCommit = false

        gate.withLock { isCurrent = false }
        val committed = gate.commitIf(
            isSessionCurrent = { isCurrent },
            commit = { didCommit = true },
        )

        assertFalse(committed)
        assertFalse(didCommit)
    }

    @Test
    fun lockWaitsUntilAnAlreadyLinearizedCommitFinishes() {
        val gate = VaultSessionCommitGate()
        val commitEntered = CountDownLatch(1)
        val allowCommitToFinish = CountDownLatch(1)
        val lockFinished = AtomicBoolean(false)
        var isCurrent = true

        val commitThread = thread {
            gate.commitIf(
                isSessionCurrent = { isCurrent },
                commit = {
                    commitEntered.countDown()
                    check(allowCommitToFinish.await(5, TimeUnit.SECONDS))
                },
            )
        }
        assertTrue(commitEntered.await(5, TimeUnit.SECONDS))

        val lockThread = thread {
            gate.withLock {
                isCurrent = false
                lockFinished.set(true)
            }
        }

        assertFalse(lockFinished.get())
        allowCommitToFinish.countDown()
        commitThread.join(5_000)
        lockThread.join(5_000)

        assertTrue(lockFinished.get())
        assertFalse(commitThread.isAlive)
        assertFalse(lockThread.isAlive)
    }
}
