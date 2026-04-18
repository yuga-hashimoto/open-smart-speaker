package com.opendash.app.device.provider.switchbot

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class SwitchBotConfig(
    val token: String = "",
    val secret: String = ""
)

class SwitchBotApiClient(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val config: SwitchBotConfig
) {
    companion object {
        private const val BASE_URL = "https://api.switch-bot.com/v1.1"
    }

    suspend fun getDevices(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/devices")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()

        parseDeviceList(response.body?.string() ?: "")
    }

    suspend fun getDeviceStatus(deviceId: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val request = buildRequest("$BASE_URL/devices/$deviceId/status")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyMap()

        parseStatusResponse(response.body?.string() ?: "")
    }

    suspend fun sendCommand(
        deviceId: String,
        command: String,
        parameter: String = "default",
        commandType: String = "command"
    ): Boolean = withContext(Dispatchers.IO) {
        val body = moshi.adapter(Map::class.java).toJson(
            mapOf(
                "command" to command,
                "parameter" to parameter,
                "commandType" to commandType
            )
        ) ?: return@withContext false

        val request = Request.Builder()
            .url("$BASE_URL/devices/$deviceId/commands")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { addAuthHeaders(this) }
            .build()

        val response = client.newCall(request).execute()
        response.isSuccessful
    }

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .apply { addAuthHeaders(this) }
            .build()
    }

    private fun addAuthHeaders(builder: Request.Builder) {
        val timestamp = java.lang.System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val data = config.token + timestamp + nonce

        val sign = try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(config.secret.toByteArray(), "HmacSHA256"))
            android.util.Base64.encodeToString(mac.doFinal(data.toByteArray()), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.w(e, "HMAC signing failed")
            ""
        }

        builder.addHeader("Authorization", config.token)
        builder.addHeader("t", timestamp)
        builder.addHeader("nonce", nonce)
        builder.addHeader("sign", sign)
        builder.addHeader("Content-Type", "application/json")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDeviceList(json: String): List<Map<String, Any?>> {
        return try {
            val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: return emptyList()
            val body = root["body"] as? Map<String, Any?> ?: return emptyList()
            val deviceList = body["deviceList"] as? List<*> ?: return emptyList()
            deviceList.filterIsInstance<Map<String, Any?>>()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse SwitchBot devices")
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStatusResponse(json: String): Map<String, Any?> {
        return try {
            val root = moshi.adapter(Map::class.java).fromJson(json) as? Map<String, Any?> ?: return emptyMap()
            root["body"] as? Map<String, Any?> ?: emptyMap()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse SwitchBot status")
            emptyMap()
        }
    }
}
