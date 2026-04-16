package com.opensmarthome.speaker.tool.system

/**
 * Sends SMS messages. Requires SEND_SMS.
 * OpenClaw sms.send equivalent.
 */
interface SmsSender {
    suspend fun send(phoneNumber: String, message: String): SmsResult
    fun hasPermission(): Boolean
}

sealed class SmsResult {
    object Sent : SmsResult()
    data class Failed(val reason: String) : SmsResult()
    object NoPermission : SmsResult()
}
