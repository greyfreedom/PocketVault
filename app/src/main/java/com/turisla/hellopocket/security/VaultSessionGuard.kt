package com.turisla.hellopocket.security

/**
 * 保险库会话闸门。
 *
 * 解锁会在后台线程执行较慢的 KDF 和文件解密；闸门保证只有“仍在前台且未被锁定”
 * 的同一代会话才能把密钥与明文发布到内存。
 */
class VaultSessionGuard {
    class Token internal constructor(internal val generation: Long)

    private val monitor = Any()
    private var generation: Long = 0L
    private var foreground: Boolean = false

    fun enterForeground() {
        synchronized(monitor) {
            foreground = true
        }
    }

    fun enterBackground() {
        synchronized(monitor) {
            foreground = false
        }
    }

    fun capture(): Token = synchronized(monitor) {
        Token(generation)
    }

    fun isForeground(): Boolean = synchronized(monitor) {
        foreground
    }

    /**
     * 锁库时使所有已发出的令牌立即失效。
     */
    fun invalidate() {
        synchronized(monitor) {
            generation++
        }
    }

    /**
     * 校验和发布共用同一把锁，避免校验通过后、发布前恰好切到后台的竞态。
     */
    fun publishIfValid(token: Token, publish: () -> Unit): Boolean {
        return synchronized(monitor) {
            if (!foreground || token.generation != generation) {
                false
            } else {
                publish()
                true
            }
        }
    }
}
