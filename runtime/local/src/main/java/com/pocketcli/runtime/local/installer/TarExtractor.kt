package com.pocketcli.runtime.local.installer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

object TarExtractor {

    fun extractTarGz(
        inputStream: InputStream,
        targetDir: File,
        onProgress: (extractedFiles: Int, totalBytesExtracted: Long) -> Unit = { _, _ -> }
    ) {
        val gzipStream = GZIPInputStream(inputStream)
        extractTar(gzipStream, targetDir, onProgress)
    }

    fun extractTar(
        inputStream: InputStream,
        targetDir: File,
        onProgress: (extractedFiles: Int, totalBytesExtracted: Long) -> Unit = { _, _ -> }
    ) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val headerBuffer = ByteArray(512)
        var nextLongName: String? = null
        var fileCount = 0
        var totalBytes = 0L

        while (true) {
            val bytesRead = readFully(inputStream, headerBuffer)
            if (bytesRead < 512 || isAllZeros(headerBuffer)) {
                break
            }

            val rawName = nextLongName ?: parseString(headerBuffer, 0, 100)
            nextLongName = null

            val typeFlag = headerBuffer[156].toInt().toChar()
            val size = parseOctal(headerBuffer, 124, 12)
            val mode = parseOctal(headerBuffer, 100, 8).toInt()

            // Handle GNU long filename
            if (typeFlag == 'L') {
                val longNameBytes = ByteArray(size.toInt())
                readFully(inputStream, longNameBytes)
                val padding = (512 - (size % 512).toInt()) % 512
                if (padding > 0) skipFully(inputStream, padding.toLong())
                nextLongName = String(longNameBytes, Charsets.UTF_8).trimEnd('\u0000')
                continue
            }

            // Clean path
            val cleanName = when {
                rawName.startsWith("./") -> rawName.substring(2)
                rawName.startsWith("/") -> rawName.substring(1)
                else -> rawName
            }.replace('\\', '/')

            if (cleanName.isBlank() || cleanName == ".") {
                val padding = (512 - (size % 512).toInt()) % 512
                if (size > 0) skipFully(inputStream, size)
                if (padding > 0) skipFully(inputStream, padding.toLong())
                continue
            }

            val destinationFile = File(targetDir, cleanName)

            // Prevent Zip Slip / Path Traversal
            val targetCanonical = targetDir.canonicalFile.toPath()
            val destCanonical = destinationFile.canonicalFile.toPath()
            if (!destCanonical.startsWith(targetCanonical)) {
                throw SecurityException("Path traversal attempt in tar archive: $cleanName")
            }

            when (typeFlag) {
                '5' -> {
                    // Directory
                    destinationFile.mkdirs()
                }
                '2' -> {
                    // Symlink
                    val linkTarget = parseString(headerBuffer, 157, 100)
                    destinationFile.parentFile?.mkdirs()
                    try {
                        val linkPath = destinationFile.toPath()
                        Files.deleteIfExists(linkPath)
                        Files.createSymbolicLink(linkPath, Paths.get(linkTarget))
                    } catch (_: Exception) {
                        destinationFile.writeText(linkTarget)
                    }
                }
                else -> {
                    // Regular file
                    destinationFile.parentFile?.mkdirs()
                    destinationFile.outputStream().use { out ->
                        copyBytes(inputStream, out, size)
                    }
                    val isExecutable = (mode and 0b001_001_001) != 0
                    if (isExecutable) {
                        destinationFile.setExecutable(true, false)
                    }
                    totalBytes += size
                    fileCount++
                    onProgress(fileCount, totalBytes)

                    val padding = (512 - (size % 512).toInt()) % 512
                    if (padding > 0) {
                        skipFully(inputStream, padding.toLong())
                    }
                }
            }
        }
    }

    private fun parseString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && buffer[end] != 0.toByte()) {
            end++
        }
        return String(buffer, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in offset until (offset + length)) {
            val b = buffer[i]
            if (b in '0'.code.toByte()..'7'.code.toByte()) {
                result = (result shl 3) + (b - '0'.code.toByte())
            } else if (b == 0.toByte() || b == ' '.code.toByte()) {
                if (result > 0) break
            }
        }
        return result
    }

    private fun isAllZeros(buffer: ByteArray): Boolean {
        for (b in buffer) {
            if (b != 0.toByte()) return false
        }
        return true
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = inputStream.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun skipFully(inputStream: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = inputStream.read(buffer, 0, toRead)
            if (read <= 0) break
            remaining -= read
        }
    }

    private fun copyBytes(inputStream: InputStream, out: OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = inputStream.read(buffer, 0, toRead)
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
    }
}
