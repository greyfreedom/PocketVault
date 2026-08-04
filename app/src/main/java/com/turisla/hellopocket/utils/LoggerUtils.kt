package com.turisla.hellopocket.utils

import android.util.Log

const val TAG = "wpeng"

fun loggerI(msg: String) {
    Log.i(TAG, msg)
}

fun loggerE(throwable: Throwable, msg: String? = null) {
    // 密码库日志只记录异常类型，不输出堆栈、路径、URI 或可能包含用户数据的异常消息。
    Log.e(TAG, msg ?: throwable::class.java.simpleName)
}
