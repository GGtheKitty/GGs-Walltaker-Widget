package com.example.ggswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews

class ImageWidget : AppWidgetProvider() {

    val ACTION_UPDATE_WIDGET = "com.example.app.ACTION_UPDATE_WIDGET"

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WalltakerWebSocketService.start(context)
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        GifWidgetAnimator.stop(appWidgetIds)
        VideoWidgetAnimator.stop(appWidgetIds)
        Preferences.deletePrefs(context, appWidgetIds)
        WalltakerWebSocketService.start(context)
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        WalltakerWebSocketService.start(context)

    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        GifWidgetAnimator.stopAll()
        VideoWidgetAnimator.stopAll()
        WalltakerWebSocketService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_UPDATE_WIDGET) {
            WalltakerWebSocketService.start(context)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ImageWidget::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }

    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetId: Int
) {
    val cropImage = loadCheckedPref(context, appWidgetId)
    var views = RemoteViews(context.packageName, R.layout.image_widget)
    Log.d("CROP?", cropImage.toString())

    if(cropImage > 0){
        views = RemoteViews(context.packageName, R.layout.image_widget_cropped)
    }

    setClickable(context, views, appWidgetId)

    ImageLookup.setImage(context, appWidgetId, views)
}

fun setClickable(context: Context, views: RemoteViews, appWidgetId: Int) {
    val intent = Intent(context, WidgetDetailActivity::class.java)
    intent.action = "${context.packageName}.OPEN_WIDGET_DETAIL"
    intent.data = Uri.parse("ggswidget://widget/$appWidgetId")
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    views.setOnClickPendingIntent(R.id.widgetFrameLayout, pendingIntent)
}
