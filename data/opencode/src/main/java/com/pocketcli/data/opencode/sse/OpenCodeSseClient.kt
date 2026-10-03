package com.pocketcli.data.opencode.sse

import com.pocketcli.data.opencode.api.OpenCodeEventDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class OpenCodeSseClient(
    baseOkHttpClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
) {
    // Dedicated OkHttpClient with readTimeout(0) required for Server-Sent Events
    private val sseHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val eventSourceFactory: EventSource.Factory = EventSources.createFactory(sseHttpClient)

    fun events(): Flow<OpenCodeEventDto> = callbackFlow {
        val base = baseUrlProvider().trimEnd('/')
        val request = Request.Builder()
            .url("$base/global/event")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Connected to SSE stream
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                try {
                    val event = json.decodeFromString<OpenCodeEventDto>(data)
                    trySend(event)
                } catch (e: Exception) {
                    // Tolerant parsing: ignore unknown / malformed payload chunks gracefully
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // Error on stream, will trigger reconnect in flow if needed
                if (t != null && !isClosedForSend) {
                    close(t)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!isClosedForSend) {
                    close()
                }
            }
        }

        val eventSource = eventSourceFactory.newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }
}
