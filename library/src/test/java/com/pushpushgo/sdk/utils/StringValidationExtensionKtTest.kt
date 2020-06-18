package com.pushpushgo.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringValidationExtensionKtTest {

    @Test
    fun `check valid api key`() {
        val result = runCatching { validateApiKey("57118b49-eb83-4de4-aea9-872144b443fc") }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check invalid api key`() {
        val result = runCatching { validateApiKey("empty1") }
        assertTrue(result.isFailure)
        assertEquals("Invalid API key! Current API key: `empty1`", result.exceptionOrNull()?.message)
    }

    @Test
    fun `check valid project id`() {
        val result = kotlin.runCatching { validateProjectId("5d411352784425000bd02a15") }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `check invalid project id`() {
        val result = runCatching { validateProjectId("empty2") }
        assertTrue(result.isFailure)
        assertEquals("Invalid project ID! Current project ID: `empty2`", result.exceptionOrNull()?.message)
    }
}
