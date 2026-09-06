package com.you.visionaid.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 封装 Android TextToSpeech 的初始化、朗读、暂停与继续能力。 */
class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var lastText = ""

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.SIMPLIFIED_CHINESE
    }

    fun speak(text: String) {
        lastText = text
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qingyue_reading")
    }

    fun pause() = tts.stop()

    fun resume() {
        if (lastText.isNotBlank()) speak(lastText)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
