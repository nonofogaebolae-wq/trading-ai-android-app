package com.tradingai.android

import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class DerivApiClient(
    private val onEvent: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isAuthorized = false

    fun connect() {
        val request = Request.Builder()
            .url("wss://ws.deriv.com/websockets/v3")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onEvent("Deriv socket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onEvent("Deriv response: $text")
                val json = JSONObject(text)
                if (json.has("authorize")) {
                    isAuthorized = json.getJSONObject("authorize").optBoolean("authorized", false)
                    onEvent("Authorization status: ${isAuthorized}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onEvent("Connection closing: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onEvent("Connection failed: ${t.message}")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onEvent("Binary message received: ${bytes.hex()}")
            }
        })
    }

    fun authorize(token: String) {
        if (webSocket == null) {
            onEvent("Not connected. Please connect first.")
            return
        }

        val payload = JSONObject()
            .put("authorize", token)
        webSocket?.send(payload.toString())
        onEvent("Sent authorize request")
    }

    fun sendSignalAction(action: String, symbol: String, amount: Double) {
        if (webSocket == null) {
            onEvent("Not connected. Please connect first.")
            return
        }

        val direction = when (action) {
            "BUY" -> "CALL"
            "SELL" -> "PUT"
            else -> "HOLD"
        }

        if (direction == "HOLD") {
            onEvent("Hold signal sent to app only. No trade order generated.")
            return
        }

        val payload = JSONObject().apply {
            put("buy", 1)
            put("price", 1)
            put("parameters", JSONObject().apply {
                put("amount", amount)
                put("basis", "stake")
                put("contract_type", direction)
                put("currency", "USD")
                put("duration", 1)
                put("duration_unit", "m")
                put("symbol", symbol)
            })
        }

        webSocket?.send(payload.toString())
        onEvent("Sent Deriv trade payload: $payload")
    }

    fun close() {
        webSocket?.close(1000, "Closing session")
        webSocket = null
        isAuthorized = false
    }
}
