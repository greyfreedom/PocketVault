package com.turisla.hellopocket.data

/**
 * 将“会话是否仍有效”的判断与最终提交放进同一个临界区。
 *
 * 锁库也必须使用 [withLock] 修改会话状态。这样锁库和磁盘提交具有明确的先后顺序：
 * 锁库先获得锁时，旧提交会被拒绝；提交先获得锁时，锁库会等待提交完成后再清除会话。
 */
internal class VaultSessionCommitGate(
    private val monitor: Any = Any(),
) {
    fun <T> withLock(block: () -> T): T = synchronized(monitor, block)

    fun commitIf(
        isSessionCurrent: () -> Boolean,
        commit: () -> Unit,
    ): Boolean = synchronized(monitor) {
        if (!isSessionCurrent()) {
            false
        } else {
            commit()
            true
        }
    }
}
