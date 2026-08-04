package com.turisla.hellopocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedPasswordRequestTest {

    @Test
    fun generatedPasswordIsDeliveredOnlyToTheCurrentCaller() {
        val request = GeneratedPasswordRequest()
        var editedPassword: String? = null
        var addedPassword: String? = null

        request.begin { editedPassword = it }
        request.deliver("password-for-edit")

        // 已交付的请求必须立即失效，后续结果不能再次写入旧页面。
        request.deliver("stale-password")
        assertEquals("password-for-edit", editedPassword)
        assertNull(addedPassword)

        request.begin { addedPassword = it }
        request.clear()
        request.deliver("password-after-cancel")
        assertNull(addedPassword)
    }
}
