package com.turisla.hellopocket.ui.feature.totp

import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.ui.feature.common.viewmodel.RepositoryMutationViewModel
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 生成结果
 */
data class TotpCode(
    val code: String,        // 6位动态码
    val remainingSeconds: Int,  // 剩余秒数
    val remainingMillis: Int,   // 剩余毫秒（用于精确倒计时）
    val period: Int          // 周期
)

/**
 * TOTP 页面 ViewModel
 */
class TotpViewModel(
    private val totpRepository: TotpRepository,
    private val clipboardHelper: ClipboardManagerHelper
) : RepositoryMutationViewModel() {

    private val codeCache = mutableMapOf<String, CachedTotpCode>()

    val totpEntries: StateFlow<List<com.turisla.hellopocket.model.TotpEntry>> = totpRepository.totpEntries

    private val _expandedIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    init {
        viewModelScope.launch {
            totpEntries.collectLatest { entries ->
                val activeIds = entries.mapTo(mutableSetOf()) { it.id }
                codeCache.keys.retainAll(activeIds)
            }
        }
    }

    /**
     * 生成 TOTP 动态码
     */
    fun generateCode(entry: com.turisla.hellopocket.model.TotpEntry): TotpCode {
        return try {
            require(entry.period in 15..120) { "Invalid TOTP period" }
            require(entry.digits in setOf(6, 8)) { "Invalid TOTP digit count" }
            require(entry.algorithm in setOf("SHA1", "SHA256", "SHA512")) { "Invalid TOTP algorithm" }
            val secretText = entry.secret.toStringUtf8()
            require(com.turisla.hellopocket.security.OtpAuthParser.isValidBase32(secretText)) {
                "Invalid TOTP secret"
            }
            val currentTimeMillis = System.currentTimeMillis()
            val (remainingSeconds, remainingMillis) = getRemainingTime(entry.period, currentTimeMillis)
            val timeWindow = currentTimeMillis / (entry.period.toLong() * 1000L)
            val cached = codeCache[entry.id]
            val code = if (
                cached != null &&
                cached.timeWindow == timeWindow &&
                cached.algorithm == entry.algorithm &&
                cached.digits == entry.digits &&
                cached.secret == entry.secret
            ) {
                cached.code
            } else {
                val config = TimeBasedOneTimePasswordConfig(
                    codeDigits = entry.digits,
                    hmacAlgorithm = when (entry.algorithm) {
                        "SHA256" -> HmacAlgorithm.SHA256
                        "SHA512" -> HmacAlgorithm.SHA512
                        else -> HmacAlgorithm.SHA1
                    },
                    timeStep = entry.period.toLong(),
                    timeStepUnit = TimeUnit.SECONDS
                )
                val generator = TimeBasedOneTimePasswordGenerator(decodeBase32(secretText), config)
                generator.generate(currentTimeMillis).also { generated ->
                    codeCache[entry.id] = CachedTotpCode(
                        timeWindow = timeWindow,
                        algorithm = entry.algorithm,
                        digits = entry.digits,
                        secret = entry.secret,
                        code = generated
                    )
                }
            }
            TotpCode(
                code = formatCode(code),
                remainingSeconds = remainingSeconds,
                remainingMillis = remainingMillis,
                period = entry.period
            )
        } catch (_: Exception) {
            TotpCode(
                code = "------",
                remainingSeconds = 0,
                remainingMillis = 0,
                period = entry.period.coerceIn(15, 120)
            )
        }
    }

    /**
     * 解码 Base32 字符串为字节数组
     */
    private fun decodeBase32(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val result = mutableListOf<Byte>()

        for (char in base32.uppercase()) {
            if (char == '=') break
            val value = alphabet.indexOf(char)
            if (value == -1) continue

            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                result.add((buffer shr (bitsLeft - 8)).toByte())
                bitsLeft -= 8
            }
        }

        return result.toByteArray()
    }

    /**
     * 格式化动态码（每3位加空格）
     */
    private fun formatCode(code: String): String {
        return code.chunked(3).joinToString(" ")
    }

    /**
     * 获取当前周期的剩余时间（秒和毫秒）
     */
    fun getRemainingTime(period: Int = 30): Pair<Int, Int> {
        return getRemainingTime(period, System.currentTimeMillis())
    }

    private fun getRemainingTime(period: Int, currentTimeMillis: Long): Pair<Int, Int> {
        require(period in 15..120) { "TOTP period must be between 15 and 120 seconds" }
        val periodMillis = period.toLong() * 1000L
        val elapsedInPeriod = currentTimeMillis % periodMillis
        val remainingMillis = (periodMillis - elapsedInPeriod).toInt()
        val remainingSeconds = (remainingMillis / 1000) + if (remainingMillis % 1000 > 0) 1 else 0
        return Pair(remainingSeconds, remainingMillis)
    }

    /**
     * 获取当前周期的剩余秒数（旧方法，保留兼容）
     */
    fun getRemainingSeconds(period: Int = 30): Int {
        return getRemainingTime(period).first
    }

    /**
     * 切换展开/收起状态
     */
    fun toggleExpanded(entryId: String) {
        _expandedIds.value = if (_expandedIds.value.contains(entryId)) {
            _expandedIds.value - entryId
        } else {
            _expandedIds.value + entryId
        }
    }

    /**
     * 复制动态码到剪贴板
     */
    fun copyCode(code: String) {
        // 移除空格后复制
        val plainCode = code.replace(" ", "")
        clipboardHelper.copyTextToClipboard(R.string.tab_totp, plainCode)
    }

    /**
     * 删除 TOTP 条目
     */
    fun deleteTotpEntry(entryId: String) {
        launchRepositoryMutation {
            totpRepository.deleteTotpEntry(entryId)
        }
    }

    /**
     * 获取当前 TOTP 条目数量
     */
    fun getCount(): Int = totpRepository.getCount()

    private data class CachedTotpCode(
        val timeWindow: Long,
        val algorithm: String,
        val digits: Int,
        val secret: com.google.protobuf.ByteString,
        val code: String
    )
}
