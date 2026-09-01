package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.RagSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RagSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("documind_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<RagSettings> = _settings.asStateFlow()

    private fun loadSettings(): RagSettings {
        return RagSettings(
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            offlineModeOnly = prefs.getBoolean("offline_mode_only", false),
            autoIndexOnUpload = prefs.getBoolean("auto_index_on_upload", true),
            chunkSizeWords = prefs.getInt("chunk_size_words", 250),
            chunkOverlapWords = prefs.getInt("chunk_overlap_words", 40),
            topK = prefs.getInt("top_k", 4),
            similarityThreshold = prefs.getFloat("similarity_threshold", 0.20f),
            geminiModel = prefs.getString("gemini_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        )
    }

    fun updateSettings(newSettings: RagSettings) {
        prefs.edit()
            .putString("custom_api_key", newSettings.customApiKey)
            .putBoolean("offline_mode_only", newSettings.offlineModeOnly)
            .putBoolean("auto_index_on_upload", newSettings.autoIndexOnUpload)
            .putInt("chunk_size_words", newSettings.chunkSizeWords)
            .putInt("chunk_overlap_words", newSettings.chunkOverlapWords)
            .putInt("top_k", newSettings.topK)
            .putFloat("similarity_threshold", newSettings.similarityThreshold)
            .putString("gemini_model", newSettings.geminiModel)
            .apply()
        _settings.value = newSettings
    }

    fun getEffectiveApiKey(): String {
        val customKey = _settings.value.customApiKey.trim()
        if (customKey.isNotEmpty()) {
            return customKey
        }
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        return if (buildKey != "MY_GEMINI_API_KEY" && buildKey.isNotBlank()) buildKey else ""
    }

    fun hasValidApiKey(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }
}
