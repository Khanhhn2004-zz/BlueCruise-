package com.vibegravity.bluecruise.data.customer

import com.vibegravity.bluecruise.domain.customer.CustomerSongType
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CustomerSongApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String
) : CustomerSongDownloader {

    override suspend fun downloadSong(
        accessToken: String,
        userId: String,
        type: CustomerSongType
    ): CustomerSongDownloadResponse {
        val requestBody = buildJsonObject {
            put("userId", userId)
            put("type", type.wireValue)
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v2/device/download")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful || bodyBytes.isEmpty()) {
                error("Unable to download ${type.wireValue}")
            }
            return CustomerSongDownloadResponse(
                bytes = bodyBytes,
                fileName = response.header("Content-Disposition")
                    ?.substringAfter("filename=", missingDelimiterValue = "")
                    ?.trim('"')
                    ?.ifBlank { null },
                contentType = response.header("Content-Type")
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
