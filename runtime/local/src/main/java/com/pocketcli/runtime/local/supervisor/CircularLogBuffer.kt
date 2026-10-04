package com.pocketcli.runtime.local.supervisor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircularLogBuffer(
    val capacity: Int
) {
    @Inject
    constructor() : this(capacity = 1000)
    private val lock = Any()
    private val buffer = ArrayDeque<String>(capacity)
    private val _linesFlow = MutableStateFlow<List<String>>(emptyList())
    val linesFlow: StateFlow<List<String>> = _linesFlow.asStateFlow()

    fun append(line: String) {
        val trimmed = line.trimEnd('\r', '\n')
        if (trimmed.isEmpty()) return
        val sanitized = sanitize(trimmed)
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(sanitized)
            _linesFlow.value = buffer.toList()
        }
    }

    companion object {
        private val SECRET_PATTERNS = listOf(
            Regex("""(?i)(\b[\w.-]*(?:password|token|secret|api_key|apikey)[\w.-]*)=([^\s]+)""") to { m: MatchResult -> "${m.groupValues[1]}=********" },
            Regex("""(?i)Bearer\s+[A-Za-z0-9\-_.~+/]+=*""") to { _: MatchResult -> "Bearer ********" },
            Regex("""(?i)Basic\s+[A-Za-z0-9+/=]+""") to { _: MatchResult -> "Basic ********" },
            Regex("""sk-[A-Za-z0-9_\-]{16,}""") to { _: MatchResult -> "sk-********" },
            Regex("""AIza[0-9A-Za-z_\-]{35}""") to { _: MatchResult -> "AIza********" },
        )

        fun sanitize(input: String): String {
            var result = input
            for ((regex, transform) in SECRET_PATTERNS) {
                result = regex.replace(result, transform)
            }
            return result
        }
    }

    fun getLines(): List<String> {
        synchronized(lock) {
            return buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _linesFlow.value = emptyList()
        }
    }
}
