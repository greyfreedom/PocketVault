package com.turisla.hellopocket.utils

import java.security.SecureRandom

/**
 * Password generator utility class
 */
object PasswordGenerator {

    private val secureRandom = SecureRandom()
    
    private const val UPPERCASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    
    // 易混淆字符定义
    private const val CONFUSING_UPPERCASE = "O"  // 与数字0混淆
    private const val CONFUSING_LOWERCASE = "l"  // 与数字1和大写I混淆
    private const val CONFUSING_NUMBERS = "01"   // 0与O混淆，1与l和I混淆
    
    // 过滤后的字符集
    private val CLEAR_UPPERCASE_LETTERS = UPPERCASE_LETTERS.filterNot { it in CONFUSING_UPPERCASE }
    private val CLEAR_LOWERCASE_LETTERS = LOWERCASE_LETTERS.filterNot { it in CONFUSING_LOWERCASE }
    private val CLEAR_NUMBERS = NUMBERS.filterNot { it in CONFUSING_NUMBERS }
    
    /**
     * Generate a password based on the specified criteria
     */
    fun generatePassword(
        length: Int = 12,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = false,
        avoidConfusing: Boolean = false
    ): String {
        require(length >= 6) { "Password length must be at least 6" }
        require(includeUppercase || includeLowercase || includeNumbers || includeSymbols) {
            "At least one character type must be included"
        }
        
        val password = mutableListOf<Char>()
        val allSets = mutableListOf<String>()
        
        // 根据是否避免易混淆字符选择字符集
        val uppercaseSet = if (avoidConfusing) CLEAR_UPPERCASE_LETTERS else UPPERCASE_LETTERS
        val lowercaseSet = if (avoidConfusing) CLEAR_LOWERCASE_LETTERS else LOWERCASE_LETTERS
        val numberSet = if (avoidConfusing) CLEAR_NUMBERS else NUMBERS
        
        // First, add at least one character from each selected type to guarantee inclusion
        if (includeUppercase) {
            password.add(uppercaseSet.randomSecure())
            allSets.add(uppercaseSet)
        }
        if (includeLowercase) {
            password.add(lowercaseSet.randomSecure())
            allSets.add(lowercaseSet)
        }
        if (includeNumbers) {
            password.add(numberSet.randomSecure())
            allSets.add(numberSet)
        }
        if (includeSymbols) {
            password.add(SYMBOLS.randomSecure())
            allSets.add(SYMBOLS)
        }
        
        // Build the complete character set for remaining positions
        val allCharacters = allSets.joinToString("")
        
        // Fill remaining positions with random characters from all selected sets
        repeat(length - password.size) {
            password.add(allCharacters.randomSecure())
        }
        
        // 使用密码学安全的 Fisher-Yates 洗牌，避免字符类型位置可预测
        for (index in password.lastIndex downTo 1) {
            val swapIndex = secureRandom.nextInt(index + 1)
            val current = password[index]
            password[index] = password[swapIndex]
            password[swapIndex] = current
        }
        
        return password.joinToString("")
    }

    private fun String.randomSecure(): Char = this[secureRandom.nextInt(length)]
}
