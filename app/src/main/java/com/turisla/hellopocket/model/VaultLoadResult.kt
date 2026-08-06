package com.turisla.hellopocket.model

/**
 * 保险库加载结果
 * 用于细分不同类型的加载失败原因，提供更精确的错误提示
 */
sealed class VaultLoadResult {
    /**
     * 加载成功
     */
    data object Success : VaultLoadResult()
    
    /**
     * 主密码错误
     * 无法解密 Keyset，通常是用户输入的密码不正确
     */
    data object WrongPassword : VaultLoadResult()
    
    /**
     * 文件损坏
     * 数据文件格式错误或无法解析
     */
    data object FileCorrupted : VaultLoadResult()
    
    /**
     * 完整性校验失败
     * 数据文件的 SHA-256 哈希值与配置中记录的不一致
     */
    data object IntegrityCheckFailed : VaultLoadResult()

    /**
     * 早期 V2 已通过主密码认证，但自动备份或安全升级未能完成；原保险库保持不变。
     */
    data object UpgradeFailed : VaultLoadResult()

    /**
     * 早期 V2 必须用主密码重新包装 Keyset，不能只依赖旧的生物识别快捷解锁。
     */
    data object UpgradeRequiresMasterPassword : VaultLoadResult()
    
    /**
     * 不支持的版本
     * @param version 数据文件的 schema 版本号
     */
    data class UnsupportedVersion(val version: Int) : VaultLoadResult()

    /**
     * 解锁期间应用已进入后台或保险库已被锁定，丢弃本次解密结果。
     */
    data object SessionInvalidated : VaultLoadResult()
}
