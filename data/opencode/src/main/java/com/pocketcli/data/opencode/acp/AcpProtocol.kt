package com.pocketcli.data.opencode.acp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class AcpClientInfo(
    val name: String = "PocketCLI",
    val version: String = "1.0.4"
)

@Serializable
data class AcpClientCapabilities(
    val streaming: Boolean = true,
    val permissions: Boolean = true,
    val diff: Boolean = true,
    val plan: Boolean = true
)

@Serializable
data class AcpInitializeParams(
    val clientInfo: AcpClientInfo = AcpClientInfo(),
    val capabilities: AcpClientCapabilities = AcpClientCapabilities()
)

@Serializable
data class AcpSessionNewParams(
    val title: String,
    val directory: String? = null,
    val agent: String? = null,
    val model: String? = null
)

@Serializable
data class AcpSessionNewResult(
    val sessionId: String,
    val title: String
)

@Serializable
data class AcpSessionPromptParams(
    val sessionId: String,
    val prompt: String,
    val model: String? = null
)

@Serializable
data class AcpPermissionReplyParams(
    val requestId: String,
    val optionId: String
)
