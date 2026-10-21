package com.tradingai.android

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var connectionText: TextView
    private lateinit var logText: TextView
    private lateinit var tokenInput: EditText
    private lateinit var appIdInput: EditText
    private lateinit var symbolInput: EditText
    private lateinit var amountInput: EditText

    private val derivClient by lazy {
        DerivApiClient { message ->
            runOnUiThread {
                logText.text = "Log: $message"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionText = findViewById(R.id.connectionText)
        logText = findViewById(R.id.logText)
        tokenInput = findViewById(R.id.tokenInput)
        appIdInput = findViewById(R.id.appIdInput)
        symbolInput = findViewById(R.id.symbolInput)
        amountInput = findViewById(R.id.amountInput)

        findViewById<Button>(R.id.connectButton).setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isEmpty()) {
                logText.text = "Log: Enter a Deriv token first"
                return@setOnClickListener
            }

            connectionText.text = "Status: connecting..."
            derivClient.connect()
            CoroutineScope(Dispatchers.Main).launch {
                // give WebSocket a small delay before authorize
                kotlinx.coroutines.delay(600)
                derivClient.authorize(token)
            }
        }

        findViewById<Button>(R.id.buyButton).setOnClickListener {
            triggerSignal("BUY")
        }

        findViewById<Button>(R.id.sellButton).setOnClickListener {
            triggerSignal("SELL")
        }

        findViewById<Button>(R.id.holdButton).setOnClickListener {
            triggerSignal("HOLD")
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            val token = tokenInput.text.toString().trim()
            val symbol = symbolInput.text.toString().trim()
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 1.0

            val payload = JSONObject().apply {
                put("symbol", symbol.ifEmpty { "R_100" })
                put("action", "BUY")
                put("amount", amount)
                put("side", "LONG")
                put("signal", "direction_change")
                put("token", token)
                put("app_id", appIdInput.text.toString().trim())
            }

            connectionText.text = "Status: test ready"
            logText.text = "Log: Payload ready => ${payload.toString()}"
        }
    }

    private fun triggerSignal(action: String) {
        val symbol = symbolInput.text.toString().trim().ifEmpty { "R_100" }
        val amount = amountInput.text.toString().toDoubleOrNull() ?: 1.0

        connectionText.text = "Status: sending $action"
        derivClient.sendSignalAction(action, symbol, amount)
    }

    override fun onDestroy() {
        derivClient.close()
        super.onDestroy()
    }
}
