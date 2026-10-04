package com.pocketcli.core.security

import okhttp3.Dns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resilient DNS resolver for OkHttp.
 *
 * Provides a 3-tier fallback strategy to avoid "Unable to resolve host: No address associated with hostname":
 * 1. Standard system DNS ([Dns.SYSTEM]) via Android OS getaddrinfo.
 * 2. Direct UDP DNS queries to reliable public resolvers (Google 8.8.8.8, Cloudflare 1.1.1.1, Yandex 77.88.8.8).
 * 3. Static fallback to Google Front End (GFE) anycast IPs for critical Google OAuth and AI endpoints.
 */
object ResilientDns : Dns {

    private val PUBLIC_DNS_SERVERS = listOf(
        "8.8.8.8",
        "1.1.1.1",
        "77.88.8.8",
        "8.8.4.4"
    )

    // Curated Google Front End (GFE) anycast IP addresses for Google services
    internal val HARDCODED_FALLBACKS: Map<String, List<String>> = mapOf(
        "oauth2.googleapis.com" to listOf(
            "142.250.147.95",
            "142.250.185.202",
            "172.217.16.202",
            "142.250.74.202",
            "172.217.168.202",
            "216.58.212.170"
        ),
        "accounts.google.com" to listOf(
            "142.251.127.84",
            "142.250.185.174",
            "172.217.16.174"
        ),
        "www.googleapis.com" to listOf(
            "172.217.114.4",
            "172.217.116.4",
            "172.217.113.4",
            "172.217.115.4"
        ),
        "daily-cloudcode-pa.googleapis.com" to listOf(
            "142.250.147.95",
            "142.250.185.202",
            "172.217.16.202"
        ),
        "cloudcode-pa.googleapis.com" to listOf(
            "142.250.147.95",
            "142.250.185.202",
            "172.217.16.202"
        ),
        "generativelanguage.googleapis.com" to listOf(
            "142.250.147.95",
            "142.250.185.202",
            "172.217.16.202"
        )
    )

    override fun lookup(hostname: String): List<InetAddress> {
        // Tier 1: Try system DNS first
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) {
                return addresses
            }
        } catch (_: Exception) {
            // Fall through to public DNS / fallback
        }

        // Tier 2: Try direct UDP query to public DNS resolvers
        for (dnsServer in PUBLIC_DNS_SERVERS) {
            try {
                val resolved = queryUdpDns(hostname, dnsServer)
                if (resolved.isNotEmpty()) {
                    return resolved
                }
            } catch (_: Exception) {
                // Try next DNS server
            }
        }

        // Tier 3: Fallback to hardcoded IPs for known Google services
        val fallbackIps = HARDCODED_FALLBACKS[hostname.lowercase()]
        if (!fallbackIps.isNullOrEmpty()) {
            val addresses = fallbackIps.mapNotNull { ip ->
                try {
                    val parts = ip.split(".").map { it.toInt().toByte() }.toByteArray()
                    InetAddress.getByAddress(hostname, parts)
                } catch (_: Exception) {
                    null
                }
            }
            if (addresses.isNotEmpty()) {
                return addresses
            }
        }

        throw UnknownHostException("Не удалось разрешить адрес хоста: $hostname")
    }

    internal fun queryUdpDns(hostname: String, dnsServerIp: String, timeoutMs: Int = 1500): List<InetAddress> {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val queryBytes = buildDnsQuery(hostname)
            val serverAddress = InetAddress.getByName(dnsServerIp)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, serverAddress, 53)
            socket.send(sendPacket)

            val buffer = ByteArray(512)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            return parseDnsResponse(buffer, receivePacket.length, hostname)
        }
    }

    internal fun buildDnsQuery(host: String): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeShort(0x2026) // Transaction ID
        dos.writeShort(0x0100) // Standard query, recursion desired
        dos.writeShort(1)      // 1 Question
        dos.writeShort(0)      // 0 Answers
        dos.writeShort(0)      // 0 NS
        dos.writeShort(0)      // 0 Additional

        for (part in host.split(".")) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)       // End of domain
        dos.writeShort(1)      // Type A (IPv4)
        dos.writeShort(1)      // Class IN
        return out.toByteArray()
    }

    internal fun parseDnsResponse(data: ByteArray, length: Int, host: String): List<InetAddress> {
        if (length < 12) return emptyList()
        val bais = ByteArrayInputStream(data, 0, length)
        val dis = DataInputStream(bais)
        dis.readShort() // ID
        val flags = dis.readUnsignedShort()
        val rcode = flags and 0x000F
        if (rcode != 0) return emptyList() // Format error or name error
        val qdCount = dis.readUnsignedShort()
        val anCount = dis.readUnsignedShort()
        dis.readUnsignedShort() // nsCount
        dis.readUnsignedShort() // arCount

        // Skip question records
        for (i in 0 until qdCount) {
            skipDomainName(bais)
            dis.readShort() // QTYPE
            dis.readShort() // QCLASS
        }

        val results = mutableListOf<InetAddress>()
        // Parse answer records
        for (i in 0 until anCount) {
            skipDomainName(bais)
            val type = dis.readUnsignedShort()
            dis.readUnsignedShort() // Class
            dis.readInt()           // TTL
            val rdLength = dis.readUnsignedShort()
            if (type == 1 && rdLength == 4) { // Type A (IPv4)
                val ipBytes = ByteArray(4)
                dis.readFully(ipBytes)
                try {
                    results.add(InetAddress.getByAddress(host, ipBytes))
                } catch (_: Exception) {}
            } else {
                dis.skipBytes(rdLength)
            }
        }
        return results
    }

    private fun skipDomainName(bais: ByteArrayInputStream) {
        while (true) {
            val len = bais.read()
            if (len <= 0) break
            if ((len and 0xC0) == 0xC0) {
                // Compression pointer (2 bytes total): 1 byte already read, read second byte
                bais.read()
                break
            }
            bais.skip(len.toLong())
        }
    }
}
