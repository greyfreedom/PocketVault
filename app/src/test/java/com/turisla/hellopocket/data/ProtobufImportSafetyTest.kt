package com.turisla.hellopocket.data

import com.google.protobuf.InvalidProtocolBufferException
import com.turisla.hellopocket.model.PasswordEntries
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtobufImportSafetyTest {

    @Test
    fun deeplyNestedUnknownGroupsAreRejectedWithoutOverflowingTheStack() {
        val depth = 1_000
        val startGroupTag = ((15 shl 3) or 3).toByte()
        val endGroupTag = ((15 shl 3) or 4).toByte()
        val maliciousPayload = ByteArray(depth * 2) { index ->
            if (index < depth) startGroupTag else endGroupTag
        }

        // CVE-2024-7254 的触发形态：未知 group 深度嵌套必须被解析器拒绝，而不是栈溢出。
        assertThrows(InvalidProtocolBufferException::class.java) {
            PasswordEntries.parseFrom(maliciousPayload)
        }
    }
}
