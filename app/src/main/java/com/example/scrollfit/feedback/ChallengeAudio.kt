package com.example.scrollfit.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.scrollfit.R
import com.example.scrollfit.core.feedback.*
import com.example.scrollfit.core.time.MonotonicClock
import java.text.NumberFormat
import java.math.BigDecimal
import java.util.Locale

internal fun formatRewardSeconds(amountMs: Long, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }
        .format(BigDecimal.valueOf(amountMs, 3))

enum class AudioStatus { CHECKING, VOICE, SOUNDS, VISUAL, MUTED }

/** Main-thread owner. Initialization and binder callbacks never play or resume speech directly. */
internal class ChallengeAudio(context: Context) : FeedbackOutput {
    private val context = context.applicationContext
    // Resolve the language that Android actually chose for strings, including unsupported locales.
    private val locale = Locale.forLanguageTag(context.getString(R.string.feedback_language))
    private val main = Handler(Looper.getMainLooper())
    val controller = ChallengeFeedback(this, object : MonotonicClock {
        override fun elapsedRealtimeMillis() = SystemClock.elapsedRealtime()
    })
    private var engine: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var initialized = false
    private var closed = false
    private var initialization = 0L
    private var playing: Pair<Long, FeedbackMessage>? = null
    private var transport by mutableStateOf(AudioStatus.CHECKING)
    var enabled by mutableStateOf(false); private set
    val status get() = if (enabled) transport else AudioStatus.MUTED

    fun setEnabled(value: Boolean) {
        if (closed) return
        enabled = value
        controller.setEnabled(value)
        if (value && !initialized) initialize()
    }

    private fun initialize() {
        initialized = true
        val ticket = ++initialization
        try {
            engine = TextToSpeech(context) { result ->
                main.post {
                    if (!closed && initialization == ticket) onInitialized(result)
                }
            }
            // A missing/broken engine must not leave the visible state checking forever.
            main.postDelayed({
                if (!closed && initialization == ticket && transport == AudioStatus.CHECKING) fallback()
            }, 3_000)
        } catch (_: RuntimeException) { fallback() }
    }

    private fun onInitialized(result: Int) {
        val tts = engine ?: return
        try {
            if (result != TextToSpeech.SUCCESS) { fallback(); return }
            val voices = tts.voices.orEmpty()
            val selected = selectOfflineVoice(voices.map { voice ->
                OfflineVoice(voice.name, voice.locale.language, voice.locale.country,
                    voice.isNetworkConnectionRequired,
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty())
            }, locale.language, locale.country)
            val voice = voices.firstOrNull { it.name == selected?.name }
            if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) { fallback(); return }
            tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { main.post { finish(utteranceId?.toLongOrNull()) } }
                @Deprecated("Android callback")
                override fun onError(utteranceId: String?) { main.post { fail(utteranceId?.toLongOrNull()) } }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { fail(utteranceId?.toLongOrNull()) }
                }
            })
            transport = AudioStatus.VOICE
        } catch (_: RuntimeException) { fallback() }
    }

    override fun play(token: Long, message: FeedbackMessage) {
        if (closed || !enabled) return
        playing = token to message
        // A tone begun while the voice was initializing must end before speech starts.
        try { tone?.stopTone() } catch (_: RuntimeException) { }
        if (transport == AudioStatus.VOICE) {
            try {
                if (engine?.speak(text(message), TextToSpeech.QUEUE_FLUSH, null, token.toString()) == TextToSpeech.SUCCESS) return
            } catch (_: RuntimeException) { /* Continue with local fallback. */ }
            fallback()
        }
        playTone(token, message)
    }

    private fun text(message: FeedbackMessage): String = when (message) {
        is FeedbackMessage.Count -> NumberFormat.getIntegerInstance(locale).format(message.repetitions)
        FeedbackMessage.Position -> context.getString(R.string.feedback_position)
        is FeedbackMessage.Completed -> if (message.awardedMs == null) context.getString(R.string.challenge_only_success)
            else context.getString(R.string.feedback_reward, formatRewardSeconds(message.awardedMs, locale))
    }

    private fun fallback() {
        ++initialization // Invalidate late initialization, including timeout callbacks.
        transport = AudioStatus.SOUNDS
        val old = engine; engine = null
        try { old?.stop(); old?.shutdown() } catch (_: RuntimeException) { }
    }

    private fun playTone(token: Long, message: FeedbackMessage) {
        val duration = when (message) {
            is FeedbackMessage.Count -> 80
            FeedbackMessage.Position -> 180
            is FeedbackMessage.Completed -> 260
        }
        try {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 65).also { tone = it }
            val type = when (message) {
                is FeedbackMessage.Count -> ToneGenerator.TONE_PROP_BEEP
                FeedbackMessage.Position -> ToneGenerator.TONE_PROP_NACK
                is FeedbackMessage.Completed -> ToneGenerator.TONE_PROP_ACK
            }
            if (!generator.startTone(type, duration)) transport = AudioStatus.VISUAL
        } catch (_: RuntimeException) { transport = AudioStatus.VISUAL }
        main.postDelayed({ finish(token) }, duration.toLong())
    }

    private fun finish(token: Long?) {
        if (closed || token == null || playing?.first != token) return
        playing = null
        controller.playbackFinished(token)
    }

    private fun fail(token: Long?) {
        if (closed || !enabled || token == null || transport != AudioStatus.VOICE) return
        val delivery = playing?.takeIf { it.first == token } ?: return
        fallback()
        playTone(token, delivery.second)
    }

    override fun stop() {
        playing = null // Invalidate callbacks before invoking the engine.
        try { engine?.stop() } catch (_: RuntimeException) { }
        try { tone?.stopTone() } catch (_: RuntimeException) { }
    }

    fun close() {
        if (closed) return
        controller.stop()
        closed = true; ++initialization
        main.removeCallbacksAndMessages(null)
        try { engine?.shutdown() } catch (_: RuntimeException) { }
        try { tone?.release() } catch (_: RuntimeException) { }
        engine = null; tone = null
    }
}
