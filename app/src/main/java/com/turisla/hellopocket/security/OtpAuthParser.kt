package com.turisla.hellopocket.security

import java.net.URI
import java.net.URLDecoder

/**
 * 解析后的 TOTP 数据
 */
data class OtpAuthData(
    val issuer: String,
    val account: String,
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30
)

/**
 * otpauth:// URI 解析器
 *
 * 支持解析 Google Authenticator 格式的二维码链接
 * 格式: otpauth://totp/ISSUER:ACCOUNT?secret=SECRET&algorithm=SHA1&digits=6&period=30
 *
 * 示例:
 * - otpauth://totp/Google:user@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google
 * - otpauth://totp/GitHub:username?secret=ABCDEFGHIJ1234567890&algorithm=SHA256&digits=8
 */
object OtpAuthParser {

    private const val SCHEME = "otpauth"
    private const val TYPE_TOTP = "totp"

    /**
     * 解析 otpauth:// URI
     * @return 解析结果，失败返回 null
     */
    fun parse(uriString: String): OtpAuthData? {
        return try {
            if (uriString.length > 4096) return null
            val uri = URI(uriString)

            // 验证协议类型
            if (uri.scheme != SCHEME) {
                return null
            }

            // 验证类型（目前只支持 TOTP）
            if (uri.host != TYPE_TOTP) {
                return null
            }

            // 解析路径部分获取 issuer 和 account
            // 格式: /ISSUER:ACCOUNT 或 /ACCOUNT
            val path = decodeComponent(uri.rawPath?.removePrefix("/") ?: "")
            val (issuerFromPath, account) = parsePath(path)
            val parameters = parseQuery(uri.rawQuery ?: "") ?: return null

            // 获取参数
            val secret = parameters["secret"]
            if (secret.isNullOrBlank()) {
                return null
            }

            // issuer 参数优先于路径中的 issuer
            val issuer = parameters["issuer"] ?: issuerFromPath
            if (issuer.isBlank()) {
                return null
            }

            if (account.isBlank()) {
                return null
            }

            // 可选参数
            val algorithm = (parameters["algorithm"] ?: "SHA1").uppercase()
            val digits = parameters["digits"]?.toIntOrNull() ?: 6
            val period = parameters["period"]?.toIntOrNull() ?: 30

            if (algorithm !in setOf("SHA1", "SHA256", "SHA512")) {
                return null
            }
            if (digits !in setOf(6, 8)) {
                return null
            }
            if (period !in 15..120) {
                return null
            }
            if (secret.length > 256 || !isValidBase32(secret)) {
                return null
            }
            if (issuer.length > 256 || account.length > 256) {
                return null
            }

            OtpAuthData(
                issuer = issuer,
                account = account,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                period = period
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析路径部分
     * 格式: "ISSUER:ACCOUNT" 或 "ACCOUNT"
     * @return Pair(issuer, account)
     */
    private fun parsePath(path: String): Pair<String, String> {
        // 查找冒号分隔符
        val colonIndex = path.indexOf(':')

        return if (colonIndex > 0) {
            // 有 issuer:account 格式
            val issuer = path.substring(0, colonIndex).trim()
            val account = path.substring(colonIndex + 1).trim()
            Pair(issuer, account)
        } else {
            // 只有 account，issuer 为空
            Pair("", path.trim())
        }
    }

    private fun parseQuery(rawQuery: String): Map<String, String>? {
        if (rawQuery.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            val separator = pair.indexOf('=')
            val rawKey = if (separator >= 0) pair.substring(0, separator) else pair
            val rawValue = if (separator >= 0) pair.substring(separator + 1) else ""
            val key = decodeComponent(rawKey)
            if (key.isBlank() || key in result) return null
            result[key] = decodeComponent(rawValue)
        }
        return result
    }

    private fun decodeComponent(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())

    /**
     * 验证 Secret 是否为有效的 Base32 格式
     */
    fun isValidBase32(secret: String): Boolean {
        if (secret.isBlank() || secret.length > 256) return false
        val base32Pattern = Regex("^[A-Z2-7]+=*$", RegexOption.IGNORE_CASE)
        return base32Pattern.matches(secret)
    }
}
