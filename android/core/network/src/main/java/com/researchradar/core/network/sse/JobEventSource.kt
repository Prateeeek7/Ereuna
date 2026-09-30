package com.researchradar.core.network.sse

import com.researchradar.core.model.DoneEventData
import com.researchradar.core.model.ErrorEventData
import com.researchradar.core.model.JobEvent
import com.researchradar.core.model.LogEventData
import com.researchradar.core.model.PaperSelectedEventData
import com.researchradar.core.model.StageEventData
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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Listens to Server-Sent Events from the Ereuna job status stream.
 */
@Singleton
class JobEventSource @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
    @Named("apiBaseUrl") private val baseUrl: String,
    private val tokens: com.researchradar.core.network.AuthTokenStore,
) {
    // A long-lived stream: no read timeout (the server sends keep-alive pings)
    // and no interceptors that could buffer the body.
    private val sseClient: OkHttpClient = okHttpClient.newBuilder()
        .apply { interceptors().clear() }
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun listen(jobId: String): Flow<JobEvent> = callbackFlow {
        val request = Request.Builder()
            .url("${baseUrl}v1/jobs/$jobId/events")
            .header("Accept", "text/event-stream")
            .apply { tokens.currentToken()?.let { header("Authorization", "Bearer $it") } }
            .build()

        val factory = EventSources.createFactory(sseClient)
        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val event: JobEvent? = when (type) {
                        "stage" -> JobEvent.Stage(json.decodeFromString<StageEventData>(data))
                        "log" -> JobEvent.Log(json.decodeFromString<LogEventData>(data))
                        "paper_selected" -> JobEvent.PaperSelected(json.decodeFromString<PaperSelectedEventData>(data))
                        "done" -> JobEvent.Done(json.decodeFromString<DoneEventData>(data))
                        "error" -> JobEvent.Error(json.decodeFromString<ErrorEventData>(data))
                        else -> null
                    }
                    if (event != null) {
                        trySend(event)
                    }
                } catch (e: Exception) {
                    // Ignore decode errors on non-standard frames
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }
}
