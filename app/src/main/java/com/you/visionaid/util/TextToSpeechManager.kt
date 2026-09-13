package com.you.visionaid.util

import android.content.Context
import android.speech.tts.TextToSpeech
import com.you.visionaid.domain.RecognitionLanguage
import com.you.visionaid.domain.SpeechSpeed
import java.util.Locale

/** 封装 Android TextToSpeech 的初始化、朗读、暂停与继续能力。 */
class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var lastText = ""
    private var lastLanguage = RecognitionLanguage.SIMPLIFIED_CHINESE
    private var lastSpeed = SpeechSpeed.NORMAL
    private var pendingSpeak = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.SIMPLIFIED_CHINESE
            if (pendingSpeak && lastText.isNotBlank()) speakNow()
        }
    }

    fun speak(text: String, language: RecognitionLanguage, speed: SpeechSpeed) {
        lastText = text
        lastLanguage = language
        lastSpeed = speed
        pendingSpeak = text.isNotBlank()
        if (ready && pendingSpeak) speakNow()
    }

    private fun speakNow() {
        tts.language = when (lastLanguage) {
            RecognitionLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            RecognitionLanguage.ENGLISH -> Locale.US
        }
        tts.setSpeechRate(lastSpeed.rate)
        tts.speak(lastText, TextToSpeech.QUEUE_FLUSH, null, "qingyue_reading")
        pendingSpeak = false
    }

    fun pause() {
        pendingSpeak = false
        tts.stop()
    }

    fun resume() {
        if (lastText.isNotBlank()) speak(lastText, lastLanguage, lastSpeed)
    }

    fun shutdown() {
        pendingSpeak = false
        tts.stop()
        tts.shutdown()
    }
}
