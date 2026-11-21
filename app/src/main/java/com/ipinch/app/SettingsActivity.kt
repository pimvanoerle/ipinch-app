package com.ipinch.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class SettingsActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = Prefs(this)

        val url = findViewById<EditText>(R.id.url).apply { setText(prefs.baseUrl) }
        val token = findViewById<EditText>(R.id.token).apply { setText(prefs.token) }
        val device = findViewById<EditText>(R.id.device).apply { setText(prefs.device) }
        val speak = findViewById<CheckBox>(R.id.speak).apply { isChecked = prefs.speak }
        val autoListen = findViewById<CheckBox>(R.id.autoListen).apply { isChecked = prefs.autoListen }
        val result = findViewById<TextView>(R.id.testResult)

        fun save() {
            prefs.baseUrl = url.text.toString()
            prefs.token = token.text.toString()
            prefs.device = device.text.toString()
            prefs.speak = speak.isChecked
            prefs.autoListen = autoListen.isChecked
            url.setText(prefs.baseUrl)
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            save()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.test).setOnClickListener {
            save()
            result.text = "Checking ${prefs.baseUrl}…"
            io.execute {
                val msg = try {
                    if (PinchClient(prefs).healthy()) "✅ Endpoint is up. (Token is checked on your first message.)"
                    else "⚠️ Endpoint answered, but not healthy"
                } catch (e: Exception) {
                    "❌ Can't reach it: ${e.message}\nSame network? (or VPN/Tailscale, if the URL is a private address)"
                }
                runOnUiThread { result.text = msg }
            }
        }

        findViewById<Button>(R.id.assistant).setOnClickListener {
            // "Digital assistant app" lives under Default apps; the exact screen varies by phone.
            val tries = listOf(
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            for (i in tries) {
                try { startActivity(i); break } catch (_: ActivityNotFoundException) {}
            }
            Toast.makeText(this, "Pick iPinch as the Digital assistant app", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
