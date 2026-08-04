package com.turisla.hellopocket.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import com.turisla.hellopocket.BuildConfig
import com.turisla.hellopocket.MainActivity

object AppUtils {
    fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        (context as? Activity)?.finish()
        context.startActivity(intent)
    }

    fun goBiometricOrSecurity(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // 如果特定 intent 失败，尝试通用安全设置
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
        } else {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    fun isGooglePlay() = BuildConfig.FLAVOR == AppConstants.FLAVOR_GOOGLE_PLAY

    fun isApkPure() =  BuildConfig.FLAVOR == AppConstants.FLAVOR_APK_PURE

    fun isHuaWei() =  BuildConfig.FLAVOR == AppConstants.FLAVOR_HUAWEI

    fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val version = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        return "$manufacturer $model, Android $version (API $sdk)"
    }
}