package com.ipinch.app

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a conversation endpoint that fronts your assistant (see README for the protocol).
 *
 *   POST {base}/v1/turn   X-Pinch-Token: …
 *   {text, conversation_id, device}  ->  {reply, conversation_id, end}
 */
class PinchClient(private val prefs: Prefs) {

    data class Turn(val reply: String, val conversationId: String, val end: Boolean)

    class AuthException : IOException("Token rejected (401) — check it in settings")

    fun turn(text: String, conversationId: String): Turn {
        val body = JSONObject()
            .put("text", text)
            .put("conversation_id", conversationId)
            .put("device", prefs.device)
            .toString()

        val conn = (URL(prefs.baseUrl + "/v1/turn").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 30_000 // endpoint answers within ~7.5 s, leave headroom
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Pinch-Token", prefs.token)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw AuthException()
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code ${err.take(200)}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return Turn(
                reply = json.optString("reply").replace("<end>", "").trim(),
                conversationId = json.optString("conversation_id", conversationId),
                end = json.optBoolean("end", false),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Unauthenticated liveness check; doesn't touch the brain (costs nothing). */
    fun healthy(): Boolean {
        val conn = (URL(prefs.baseUrl + "/healthz").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
        }
        return try {
            conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    }
}
