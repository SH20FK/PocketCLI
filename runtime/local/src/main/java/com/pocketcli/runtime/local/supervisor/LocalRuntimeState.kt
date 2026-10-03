package com.pocketcli.runtime.local.supervisor

sealed interface LocalRuntimeState {
    object Stopped : LocalRuntimeState
    object Starting : LocalRuntimeState
    data class Running(
        val port: Int,
        val token: String,
        val pid: Long? = null,
        val startedAt: Long = System.currentTimeMillis()
    ) : LocalRuntimeState
    object Stopping : LocalRuntimeState
    data class Failed(
        val error: String,
        val exitCode: Int? = null,
        val canRestart: Boolean = true
    ) : LocalRuntimeState
}
