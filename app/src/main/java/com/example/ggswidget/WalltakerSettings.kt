package com.example.ggswidget

import android.content.Context
import androidx.preference.PreferenceManager

object WalltakerSettings {
    const val BASE_URL_KEY = "walltakerBaseUrl"
    const val API_KEY_KEY = "walltakerApiKey"
    const val DEFAULT_BASE_URL = "https://walltaker.joi.how"

    fun baseUrl(context: Context): String {
        val savedUrl = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(BASE_URL_KEY, DEFAULT_BASE_URL)
            .orEmpty()
        return normalizeBaseUrl(savedUrl)
    }

    fun linkUrl(context: Context, linkId: String): String {
        return "${baseUrl(context)}/links/${android.net.Uri.encode(linkId)}"
    }

    fun responseUrl(context: Context, linkId: String): String {
        return "${baseUrl(context)}/api/links/${android.net.Uri.encode(linkId)}/response.json"
    }

    fun cableUrl(context: Context): String {
        val baseUrl = baseUrl(context)
        val websocketBaseUrl = when {
            baseUrl.startsWith("https://") -> baseUrl.replaceFirst("https://", "wss://")
            baseUrl.startsWith("http://") -> baseUrl.replaceFirst("http://", "ws://")
            else -> "wss://$baseUrl"
        }
        return "$websocketBaseUrl/cable"
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmedUrl = url.trim().trimEnd('/')
        if (trimmedUrl.isEmpty()) {
            return DEFAULT_BASE_URL
        }

        return when {
            trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://") -> trimmedUrl
            else -> "https://$trimmedUrl"
        }
    }

    fun apiKey(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(API_KEY_KEY, "")
            .orEmpty()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(API_KEY_KEY, apiKey.trim())
            .apply()
    }
}
