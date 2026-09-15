package com.you.visionaid.data.preferences

import android.content.Context
import com.you.visionaid.domain.AppFontSize
import com.you.visionaid.domain.DEFAULT_READING_FONT_SIZE_SP
import com.you.visionaid.domain.FontPreferences
import com.you.visionaid.domain.MAX_READING_FONT_SIZE_SP
import com.you.visionaid.domain.MIN_READING_FONT_SIZE_SP

class SharedPreferencesFontPreferences(context: Context) : FontPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, 0)

    override val appFontSize: AppFontSize
        get() = runCatching {
            AppFontSize.valueOf(
                preferences.getString(KEY_APP_FONT_SIZE, null).orEmpty(),
            )
        }.getOrDefault(AppFontSize.STANDARD)

    override val readingFontSizeSp: Int
        get() = preferences.getInt(KEY_READING_FONT_SIZE_SP, DEFAULT_READING_FONT_SIZE_SP)
            .takeIf { it in MIN_READING_FONT_SIZE_SP..MAX_READING_FONT_SIZE_SP && it % 2 == 0 }
            ?: DEFAULT_READING_FONT_SIZE_SP

    override fun setAppFontSize(fontSize: AppFontSize) {
        preferences.edit().putString(KEY_APP_FONT_SIZE, fontSize.name).apply()
    }

    override fun setReadingFontSizeSp(fontSizeSp: Int) {
        preferences.edit().putInt(KEY_READING_FONT_SIZE_SP, fontSizeSp).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "qingyue"
        const val KEY_APP_FONT_SIZE = "app_font_size"
        const val KEY_READING_FONT_SIZE_SP = "reading_font_size_sp"
    }
}
