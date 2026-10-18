package com.sakarrobotics.c40agent.domain.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogRedactorTest {

    @Test
    fun `plain messages are left untouched`() {
        val message = "Link state: CONNECTED after 2 attempts"
        assertEquals(message, LogRedactor.redact(message))
    }

    @Test
    fun `key value secrets are masked but the key stays`() {
        val redacted = LogRedactor.redact("login password=hunter2 pin: 4821 token=\"abc.def\"")
        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("4821"))
        assertFalse(redacted.contains("abc.def"))
        assertTrue(redacted.contains("password=" + LogRedactor.MASK))
        assertTrue(redacted.contains("pin: " + LogRedactor.MASK))
    }

    @Test
    fun `json style secrets are masked`() {
        val redacted = LogRedactor.redact("{\"api_key\":\"sk-12345\",\"user\":\"alice\"}")
        assertFalse(redacted.contains("sk-12345"))
        assertTrue(redacted.contains("alice"))
    }

    @Test
    fun `bearer tokens and jwts are masked`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.c2lnbmF0dXJl"
        val redacted = LogRedactor.redact("Authorization: Bearer $jwt and again $jwt")
        assertFalse(redacted.contains("eyJhbGci"))
    }

    @Test
    fun `url credentials are masked`() {
        val redacted = LogRedactor.redact("connecting to mqtt://robot:s3cret@broker.local:1883/topic")
        assertFalse(redacted.contains("s3cret"))
        assertTrue(redacted.contains("broker.local"))
    }

    @Test
    fun `private key blocks are masked`() {
        val pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAK\nabc\n-----END RSA PRIVATE KEY-----"
        val redacted = LogRedactor.redact("key follows $pem done")
        assertFalse(redacted.contains("MIIBOg"))
        assertTrue(redacted.contains("done"))
    }

    @Test
    fun `long hex secrets are masked`() {
        val hex = "a".repeat(64)
        assertFalse(LogRedactor.redact("hash $hex end").contains(hex))
    }

    @Test
    fun `words that merely contain a secret keyword are not mangled`() {
        val message = "pinned map and tokenizer ready; spinning brush"
        assertEquals(message, LogRedactor.redact(message))
    }
}
