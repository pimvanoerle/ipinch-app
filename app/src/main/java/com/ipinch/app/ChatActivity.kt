package com.ipinch.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The whole app: a chat with iPinch. Opened by the launcher icon, or by the phone's
 * assistant gesture (ACTION_ASSIST) — in which case it starts listening straight away,
 * like a smart speaker does after its wake word.
 */
class ChatActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var client: PinchClient
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var crab: ImageView
    private var wobble: ObjectAnimator? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listenAfterSpeech = false

    private var conversationId = newConversationId()
    private var lastTurnAt = 0L
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        prefs = Prefs(this)
        client = PinchClient(prefs)

        messages = findViewById(R.id.messages)
        scroll = findViewById(R.id.scroll)
        status = findViewById(R.id.status)
        input = findViewById(R.id.input)
        crab = findViewById(R.id.crab)

        findViewById<View>(R.id.mic).setOnClickListener { startListening() }
        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.newChat).setOnClickListener { resetConversation(clear = true) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) = runOnUiThread { afterSpeech() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) = runOnUiThread { afterSpeech() }
                })
                ttsReady = true
            }
        }

        addBubble("Hi! 🦀 Tap the mic or type.", fromMe = false)
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent)
    }

    private fun handleLaunch(intent: Intent?) {
        if (!prefs.isConfigured) {
            setStatus("needs setup — add the endpoint and token in ⚙")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val viaAssistant = intent?.action == Intent.ACTION_ASSIST ||
            intent?.action == Intent.ACTION_VOICE_COMMAND
        if (viaAssistant) {
            // Servers typically expire idle conversations; start fresh rather than resume a dead one.
            if (SystemClock.elapsedRealtime() - lastTurnAt > IDLE_MS) resetConversation(clear = false)
            messages.post { startListening() }
        }
    }

    // --- input ---------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun startListening() {
        if (busy) return
        tts?.stop()
        listenAfterSpeech = false
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to iPinch")
        try {
            startActivityForResult(i, REQ_SPEECH)
            setStatus("listening…")
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No speech recogniser on this phone — type instead", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SPEECH) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (resultCode == RESULT_OK && !heard.isNullOrBlank()) send(heard, voice = true)
        else setStatus("ready")
    }

    private fun sendTyped() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        send(text, voice = false)
    }

    // --- turn ----------------------------------------------------------------

    private fun send(text: String, voice: Boolean) {
        if (busy) return
        busy = true
        addBubble(text, fromMe = true)
        setStatus("thinking…")
        startWobble()
        val cid = conversationId
        io.execute {
            val result = runCatching { client.turn(text, cid) }
            runOnUiThread {
                busy = false
                stopWobble()
                lastTurnAt = SystemClock.elapsedRealtime()
                result.onSuccess { t ->
                    conversationId = t.conversationId
                    addBubble(t.reply.ifEmpty { "…" }, fromMe = false)
                    if (t.end) {
                        resetConversation(clear = false)
                        setStatus("bye 👋")
                    } else {
                        setStatus("ready")
                    }
                    if (voice && prefs.speak && ttsReady && t.reply.isNotEmpty()) {
                        listenAfterSpeech = !t.end && prefs.autoListen
                        tts?.speak(t.reply, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                    }
                }.onFailure { e ->
                    val why = when (e) {
                        is PinchClient.AuthException -> e.message
                        is java.net.SocketTimeoutException -> "timed out — the assistant is slow or unreachable"
                        is java.net.ConnectException, is java.net.UnknownHostException ->
                            "can't reach the endpoint. Same network / VPN?"
                        else -> e.message ?: e.javaClass.simpleName
                    }
                    addBubble("⚠️ $why", fromMe = false, error = true)
                    setStatus("error")
                }
            }
        }
    }

    private fun afterSpeech() {
        if (listenAfterSpeech && !isFinishing) {
            listenAfterSpeech = false
            startListening()
        }
    }

    private fun resetConversation(clear: Boolean) {
        conversationId = newConversationId()
        if (clear) {
            messages.removeAllViews()
            addBubble("New conversation 🦀", fromMe = false)
            setStatus("ready")
        }
    }

    // --- ui bits ---------------------------------------------------------------

    private fun setStatus(s: String) { status.text = s }

    private fun addBubble(text: String, fromMe: Boolean, error: Boolean = false) {
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(getColor(if (error) R.color.muted else R.color.foam))
            setBackgroundResource(if (fromMe) R.drawable.bubble_me else R.drawable.bubble_pinch)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextIsSelectable(true)
            maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (fromMe) Gravity.END else Gravity.START
            topMargin = dp(8)
        }
        messages.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startWobble() {
        wobble = ObjectAnimator.ofFloat(crab, View.ROTATION, -8f, 8f).apply {
            duration = 350
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopWobble() {
        wobble?.cancel()
        crab.rotation = 0f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        tts?.shutdown()
        io.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQ_SPEECH = 1
        private const val IDLE_MS = 9 * 60 * 1000L
        private fun newConversationId() = "phone-" + UUID.randomUUID().toString().take(8)
    }
}
