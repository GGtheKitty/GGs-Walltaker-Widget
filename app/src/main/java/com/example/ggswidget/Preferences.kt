package com.example.ggswidget

import android.content.Context
import android.graphics.Bitmap

class Preferences {
    companion object{
        private const val PREFS_NAME = "com.example.ggswidget.ImageWidget"
        private const val PREF_PREFIX_KEY = "appwidget_"
        private const val PREF_CHECKED_KEY = "pref_checked_key"
        private const val PREF_PREV_IMG_KEY = "pref_prev_img_key"
        private const val PREF_CURRENT_IMG_KEY = "pref_current_img_key"
        private const val PREF_CURRENT_SND_KEY = "pref_current_snd_key"

        // Write the prefix to the SharedPreferences object for this widget
        internal fun saveTitlePref(context: Context, appWidgetId: Int, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_PREFIX_KEY + appWidgetId, text)
            prefs.apply()
        }

        // Read the prefix from the SharedPreferences object for this widget.
        // If there is no preference saved, get the default from a resource
        internal fun loadTitlePref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val titleValue = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
            return titleValue ?: ""
        }

        internal fun deleteTitlePref(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            for(appWidgetId in appWidgetIds){
                prefs.remove(PREF_PREFIX_KEY + appWidgetId)
            }
            prefs.apply()
        }

        internal fun saveCheckedPref(context: Context, appWidgetId: Int, value: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putInt(PREF_CHECKED_KEY + appWidgetId, value)
            prefs.apply()
        }

        internal fun loadCheckedPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val checkedValue = prefs.getInt(PREF_CHECKED_KEY + appWidgetId, 0)
            return checkedValue
        }

        internal fun deleteCheckedPref(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            for(appWidgetId in appWidgetIds){
                prefs.remove(PREF_CHECKED_KEY + appWidgetId)
            }
            prefs.apply()
        }

        internal fun savePrevImagePref(context: Context, appWidgetId: Int, bitmap: Bitmap) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_PREV_IMG_KEY + appWidgetId, bitmapToString(bitmap))
            prefs.apply()
        }

        internal fun loadPrevImagePref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val titleValue = prefs.getString(PREF_PREV_IMG_KEY + appWidgetId, null)
            return titleValue ?: "Love you <3"
        }

        internal fun deletePrevImagePref(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            for(appWidgetId in appWidgetIds){
                prefs.remove(PREF_PREV_IMG_KEY + appWidgetId)
            }
            prefs.apply()
        }

        internal fun saveCurrentImgPref(context: Context, appWidgetId: Int, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_CURRENT_IMG_KEY + appWidgetId, text)
            prefs.apply()
        }

        internal fun loadCurrentImgPref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val titleValue = prefs.getString(PREF_CURRENT_IMG_KEY + appWidgetId, null)
            return titleValue ?: ""
        }

        internal fun deleteCurrentImgPref(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            for(appWidgetId in appWidgetIds){
                prefs.remove(PREF_CURRENT_IMG_KEY + appWidgetId)
            }
            prefs.apply()
        }

        internal fun saveCurrentSenderPref(context: Context, appWidgetId: Int, text: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_CURRENT_SND_KEY + appWidgetId, text)
            prefs.apply()
        }

        internal fun loadCurrentSenderPref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            val titleValue = prefs.getString(PREF_CURRENT_SND_KEY + appWidgetId, null)
            return titleValue ?: ""
        }

        internal fun deleteCurrentSenderPref(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            for(appWidgetId in appWidgetIds){
                prefs.remove(PREF_CURRENT_SND_KEY + appWidgetId)
            }
            prefs.apply()
        }

        internal fun deletePrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit().clear()
            prefs.apply()
        }

        internal fun deletePrefs(context: Context, appWidgetIds: IntArray) {
            deleteTitlePref(context, appWidgetIds)
            deleteCheckedPref(context, appWidgetIds)
            deletePrevImagePref(context, appWidgetIds)
            deleteCurrentImgPref(context, appWidgetIds)
            deleteCurrentSenderPref(context, appWidgetIds)
        }
    }
}
