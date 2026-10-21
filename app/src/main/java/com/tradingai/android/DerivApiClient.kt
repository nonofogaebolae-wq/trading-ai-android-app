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

    private var pendingProposalRequest: String? = null
    private var lastProposalId: Long? = null
    private var lastAskPrice: Double? = null

    fun connect() {
        if (webSocket != null) {
            onEvent("Deriv socket already connected")
            return
        }

        val request = Request.Builder()
            .url("wss://ws.deriv.com/websockets/v3")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onEvent("Deriv socket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onEvent("Deriv response: $text")

                try {
                    val json = JSONObject(text)

                    if (json.has("authorize")) {
                        val auth = json.getJSONObject("authorize")
                        isAuthorized = auth.optBoolean("authorized", false)
                        onEvent("Authorization status: $isAuthorized")
                    }

                    if (json.has("proposal")) {
                        val proposal = json.getJSONObject("proposal")
                        val proposalId = proposal.optLong("id", -1L)
                        val askPrice = proposal.optDouble("ask_price", -1.0)

                        if (proposalId > 0 && askPrice > 0) {
                            lastProposalId = proposalId
                            lastAskPrice = askPrice
                            onEvent("Proposal received: id=$proposalId, ask_price=$askPrice")
                            placeBuyOrder(proposalId, askPrice)
                        } else {
                            onEvent("Proposal response not ready for trade")
                        }
                    }

                    if (json.has("buy")) {
                        val buyResponse = json.getJSONObject("buy")
                        val buyId = buyResponse.optLong("contract_id", -1L)
                        val status = buyResponse.optString("status", "unknown")
                        val remainingCost = buyResponse.optDouble("buy_price", -1.0)

                        if (buyId > 0) {
                            onEvent("Trade executed successfully: contract_id=$buyId, status=$status, buy_price=$remainingCost")
                        } else {
                            onEvent("Buy response received but contract_id missing")
                        }
                    }

                    if (json.has("error")) {
                        val error = json.getJSONObject("error")
                        onEvent("Deriv error: ${error.optString("message", "unknown error")}")
                    }

                } catch (e: Exception) {
                    onEvent("Parse error: ${e.message}")
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

        val payload = JSONObject().put("authorize", token)
        webSocket?.send(payload.toString())
        onEvent("Sent authorize request")
    }

    fun sendSignalAction(action: String, symbol: String, amount: Double) {
        if (webSocket == null) {
            onEvent("Not connected. Please connect first.")
            return
        }

        if (!isAuthorized) {
            onEvent("Authorization is required before trade execution")
            return
        }

        if (action == "HOLD") {
            onEvent("Hold signal received. No Deriv order sent.")
            return
        }

        val contractType = if (action == "BUY") "CALL" else if (action == "SELL") "PUT" else "CALL"

        val proposal = JSONObject().apply {
            put("proposal", 1)
            put("amount", amount)
            put("basis", "stake")
            put("contract_type", contractType)
            put("currency", "USD")
            put("duration", 5)
            put("duration_unit", "m")
            put("symbol", symbol)
        }

        pendingProposalRequest = action
        webSocket?.send(proposal.toString())
        onEvent("Requesting Deriv proposal for $symbol as $contractType")
    }

    private fun placeBuyOrder(contractId: Long, askPrice: Double) {
        if (!isAuthorized) {
            onEvent("Cannot buy because not authorized")
            return
        }

        val payload = JSONObject().apply {
            put("buy", contractId)
            put("price", askPrice)
        }

        webSocket?.send(payload.toString())
        onEvent("Executing trade: buy=$contractId, price=$askPrice")
    }

    fun close() {
        webSocket?.close(1000, "Closing session")
        webSocket = null
        isAuthorized = false
        lastProposalId = null
        lastAskPrice = null
        pendingProposalRequest = null
    }
}
