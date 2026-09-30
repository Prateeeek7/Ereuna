package com.researchradar.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.researchradar.core.model.CreateMapResponse
import com.researchradar.core.network.RadarApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A PDF the user picked, not yet uploaded. */
data class PickedPdf(val uri: String, val name: String, val sizeBytes: Long)

class UploadException(message: String) : Exception(message)

/** Sends the user's own PDFs to build a private map ("Use my papers"). */
@Singleton
class PaperUploadRepository @Inject constructor(
    private val api: RadarApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
) {

    /** File name and size of a picked document. */
    suspend fun describe(uri: Uri): PickedPdf = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "paper.pdf"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        PickedPdf(uri = uri.toString(), name = name, sizeBytes = size)
    }

    suspend fun createMap(topic: String, pdfs: List<PickedPdf>, includeRelated: Boolean): CreateMapResponse {
        val text = "text/plain".toMediaType()
        val parts = pdfs.map { pdf ->
            MultipartBody.Part.createFormData("files", pdf.name, ContentUriBody(Uri.parse(pdf.uri), pdf.sizeBytes))
        }
        return try {
            api.createMapFromUploads(topic.toRequestBody(text), includeRelated.toString().toRequestBody(text), parts)
        } catch (e: HttpException) {
            val detail = runCatching {
                json.parseToJsonElement(e.response()?.errorBody()?.string().orEmpty()).jsonObject["detail"]?.jsonPrimitive?.content
            }.getOrNull()
            throw UploadException(
                detail ?: when (e.code()) {
                    413 -> "The PDFs are too large to upload."
                    429 -> "Daily limit reached. Try again tomorrow."
                    else -> "The server returned an error (${e.code()})."
                },
            )
        } catch (e: IOException) {
            throw UploadException("Couldn't upload the PDFs. Check your connection and try again.")
        }
    }

    /** Streams a document straight from its content provider, so large PDFs are never held in memory. */
    private inner class ContentUriBody(private val uri: Uri, private val size: Long) : RequestBody() {
        override fun contentType(): MediaType = "application/pdf".toMediaType()

        override fun contentLength(): Long = size

        override fun writeTo(sink: BufferedSink) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Can't open $uri")
            stream.source().use { sink.writeAll(it) }
        }
    }
}
