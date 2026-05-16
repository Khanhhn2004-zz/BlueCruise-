package com.vibegravity.bluecruise.data.auth

import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LoginApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String
) {
    fun login(phone: String, password: String): LoginApiResponse {
        val requestBody = buildJsonObject {
            put("phoneNumber", phone)
            put("password", password)
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v2/device/login")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return LoginApiResponse(
                    httpStatusCode = response.code,
                    success = false,
                    accessToken = null,
                    userId = null,
                    message = null
                )
            }

            val payload = json.parseToJsonElement(body).jsonObject
            return LoginApiResponse(
                httpStatusCode = response.code,
                success = payload["success"]?.jsonPrimitive?.booleanOrNull ?: false,
                accessToken = payload["accessToken"]?.jsonPrimitive?.contentOrNull,
                userId = payload["userId"]?.jsonPrimitive?.contentOrNull,
                message = payload["message"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
