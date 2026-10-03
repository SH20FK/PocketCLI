package com.pocketcli.runtime.local.supervisor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircularLogBuffer @Inject constructor(
    val capacity: Int = 1000
) {
    private val lock = Any()
    private val buffer = ArrayDeque<String>(capacity)
    private val _linesFlow = MutableStateFlow<List<String>>(emptyList())
    val linesFlow: StateFlow<List<String>> = _linesFlow.asStateFlow()

    fun append(line: String) {
        val trimmed = line.trimEnd('\r', '\n')
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(trimmed)
            _linesFlow.value = buffer.toList()
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
