package com.pocketcli.data.opencode.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class OpenCodeHealthDto(
    val healthy: Boolean,
    val version: String
)

@Serializable
data class OpenCodeTimeDto(
    val created: Long = 0L,
    val updated: Long = 0L,
    val completed: Long = 0L
)

@Serializable
data class OpenCodeSessionDto(
    val id: String,
    val title: String = "",
    val time: OpenCodeTimeDto = OpenCodeTimeDto(),
    val slug: String? = null,
    val directory: String? = null
)

@Serializable
data class OpenCodeEventDto(
    val directory: String? = null,
    val payload: OpenCodePayloadDto
)

@Serializable
data class OpenCodePayloadDto(
    val type: String,
    val properties: JsonElement = JsonObject(emptyMap())
)

@Serializable
data class OpenCodeSessionCreatedProperties(
    val info: OpenCodeSessionDto
)

@Serializable
data class OpenCodeStatusDto(
    val type: String
)

@Serializable
data class OpenCodeSessionStatusProperties(
    val sessionID: String,
    val status: OpenCodeStatusDto
)

@Serializable
data class OpenCodeMessageInfoDto(
    val id: String,
    val sessionID: String,
    val role: String,
    val time: OpenCodeTimeDto? = null
)

@Serializable
data class OpenCodeMessageUpdatedProperties(
    val info: OpenCodeMessageInfoDto
)

@Serializable
data class OpenCodePartDeltaProperties(
    val sessionID: String,
    val messageID: String,
    val partID: String,
    val field: String,
    val delta: String
)

@Serializable
data class OpenCodeToolStateDto(
    val status: String,
    val input: JsonElement? = null,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class OpenCodePartDto(
    val id: String,
    val sessionID: String? = null,
    val messageID: String? = null,
    val type: String,
    val callID: String? = null,
    val tool: String? = null,
    val state: OpenCodeToolStateDto? = null,
    val text: String? = null
)

@Serializable
data class OpenCodePartUpdatedProperties(
    val part: OpenCodePartDto
)

@Serializable
data class OpenCodeToolRefDto(
    val messageID: String,
    val callID: String
)

@Serializable
data class OpenCodePermissionAskedProperties(
    val id: String,
    val sessionID: String,
    val permission: String,
    val patterns: List<String> = emptyList(),
    val always: List<String> = emptyList(),
    val tool: OpenCodeToolRefDto? = null
)

@Serializable
data class OpenCodeCreateSessionRequest(
    val title: String
)

@Serializable
data class OpenCodeTextPartInput(
    val type: String = "text",
    val text: String
)

@Serializable
data class OpenCodeModelInput(
    val providerID: String,
    val modelID: String
)

@Serializable
data class OpenCodeSendMessageRequest(
    val parts: List<OpenCodeTextPartInput>,
    val model: OpenCodeModelInput? = null
)

@Serializable
data class OpenCodePermissionReplyRequest(
    val reply: String,
    val message: String? = null
)

@Serializable
data class OpenCodeReconcileMessageDto(
    val info: OpenCodeMessageInfoDto,
    val parts: List<OpenCodePartDto> = emptyList()
)

@Serializable
data class OpenCodeModelDetailDto(
    val id: String,
    val name: String? = null,
    val providerID: String? = null
)

@Serializable
data class OpenCodeProviderDto(
    val id: String,
    val name: String? = null,
    val models: Map<String, OpenCodeModelDetailDto> = emptyMap()
)

@Serializable
data class OpenCodeProvidersResponseDto(
    val all: List<OpenCodeProviderDto> = emptyList(),
    val connected: List<String> = emptyList(),
    val default: Map<String, String> = emptyMap()
)
