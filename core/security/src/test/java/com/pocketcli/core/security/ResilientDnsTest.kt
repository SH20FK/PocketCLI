package com.pocketcli.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class ResilientDnsTest {

    @Test
    fun testHardcodedFallbacksContainEssentialGoogleHosts() {
        val fallbacks = ResilientDns.HARDCODED_FALLBACKS
        assertTrue(fallbacks.containsKey("oauth2.googleapis.com"))
        assertTrue(fallbacks.containsKey("accounts.google.com"))
        assertTrue(fallbacks.containsKey("daily-cloudcode-pa.googleapis.com"))
        assertTrue(fallbacks.containsKey("generativelanguage.googleapis.com"))

        for ((host, ips) in fallbacks) {
            assertTrue("Host $host should have at least 1 fallback IP", ips.isNotEmpty())
        }
    }

    @Test
    fun testLookupResolvesGoogleOAuth() {
        val addresses = ResilientDns.lookup("oauth2.googleapis.com")
        assertNotNull(addresses)
        assertTrue("Should return at least one IP address for oauth2.googleapis.com", addresses.isNotEmpty())
        assertEquals("oauth2.googleapis.com", addresses.first().hostName)
    }

    @Test
    fun testBuildDnsQueryWireFormat() {
        val query = ResilientDns.buildDnsQuery("oauth2.googleapis.com")
        assertTrue(query.size > 12)
        // Check transaction ID 0x2026
        assertEquals(0x20.toByte(), query[0])
        assertEquals(0x26.toByte(), query[1])
        // Check standard query flags (RD=1)
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
    }

    @Test(expected = UnknownHostException::class)
    fun testLookupNonExistentDomainThrows() {
        ResilientDns.lookup("this-domain-does-not-exist-at-all-123456789.invalid")
    }
}
