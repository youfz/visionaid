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

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.SIMPLIFIED_CHINESE
    }

    fun speak(text: String, language: RecognitionLanguage, speed: SpeechSpeed) {
        lastText = text
        lastLanguage = language
        lastSpeed = speed
        if (ready) {
            tts.language = when (language) {
                RecognitionLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
                RecognitionLanguage.ENGLISH -> Locale.US
            }
            tts.setSpeechRate(speed.rate)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qingyue_reading")
        }
    }

    fun pause() = tts.stop()

    fun resume() {
        if (lastText.isNotBlank()) speak(lastText, lastLanguage, lastSpeed)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
