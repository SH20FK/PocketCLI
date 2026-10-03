package com.pocketcli.runtime.remote

import com.pocketcli.core.model.HealthInfo
import com.pocketcli.core.model.Transport
import com.pocketcli.data.opencode.api.OpenCodeApiClient
import javax.inject.Inject

class RemoteTransport @Inject constructor(
    override val baseUrl: String,
    private val apiClient: OpenCodeApiClient
) : Transport {

    private var _isConnected: Boolean = false
    override val isConnected: Boolean get() = _isConnected

    override suspend fun checkHealth(): Result<HealthInfo> {
        val result = apiClient.getHealth()
        return result.map { dto ->
            _isConnected = dto.healthy
            HealthInfo(
                healthy = dto.healthy,
                version = dto.version
            )
        }.onFailure {
            _isConnected = false
        }
    }
}
