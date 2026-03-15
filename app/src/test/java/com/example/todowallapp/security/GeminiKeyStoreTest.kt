package com.example.todowallapp.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiKeyStoreTest {

    @Test
    fun `set get and clear api key`() {
        val fakeStore = FakeKeyValueStore()
        val keyStore = GeminiKeyStore(fakeStore)

        keyStore.setApiKey("  abc123  ")
        assertEquals("abc123", keyStore.getApiKey())
        assertTrue(keyStore.hasApiKey())

        keyStore.clearApiKey()
        assertNull(keyStore.getApiKey())
    }

    private class FakeKeyValueStore : GeminiKeyStore.KeyValueStore {
        private val map = mutableMapOf<String, String>()

        override fun getString(key: String): String? = map[key]

        override fun putString(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }
}
