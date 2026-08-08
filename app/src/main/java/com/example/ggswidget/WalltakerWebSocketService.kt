package com.example.ggswidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class WalltakerWebSocketService : Service() {
    private val client = OkHttpClient.Builder().build()
    private val subscriptions = ConcurrentHashMap<Int, LinkSubscription>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncSubscriptions()
        return START_STICKY
    }

    override fun onDestroy() {
        subscriptions.values.forEach { it.webSocket.close(1000, "Service stopped") }
        subscriptions.clear()
        GifWidgetAnimator.stopAll()
        VideoWidgetAnimator.stopAll()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncSubscriptions() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, ImageWidget::class.java)).toSet()

        if (appWidgetIds.isEmpty()) {
            stopSelf()
            return
        }

        subscriptions.keys
            .filter { it !in appWidgetIds }
            .forEach { closeSubscription(it) }

        appWidgetIds.forEach { appWidgetId ->
            ImageLookup.setImage(this, appWidgetId)
            val linkId = Preferences.loadTitlePref(this, appWidgetId).trim()
            val cableUrl = WalltakerSettings.cableUrl(this)
            val existing = subscriptions[appWidgetId]

            if (linkId.isEmpty()) {
                closeSubscription(appWidgetId)
                return@forEach
            }

            if (existing?.linkId == linkId && existing.cableUrl == cableUrl) {
                return@forEach
            }

            closeSubscription(appWidgetId)
            openSubscription(appWidgetId, linkId, cableUrl)
        }
    }

    private fun openSubscription(appWidgetId: Int, linkId: String, cableUrl: String) {
        Log.d(TAG, "Opening WebSocket for widget $appWidgetId link $linkId at $cableUrl")

        val request = Request.Builder()
            .url(cableUrl)
            .header("User-Agent", CLIENT_NAME)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val identifier = JSONObject()
                    .put("channel", "LinkChannel")
                    .put("link_id", linkId)
                    .put("client", CLIENT_NAME)
                    .toString()
                val subscribeMessage = JSONObject()
                    .put("command", "subscribe")
                    .put("identifier", identifier)
                    .toString()

                webSocket.send(subscribeMessage)
                Log.d(TAG, "Subscribed widget $appWidgetId to Walltaker link $linkId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(appWidgetId, linkId, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(TAG, "WebSocket failed for widget $appWidgetId: ${t.message}")
                subscriptions.remove(appWidgetId)
                handler.postDelayed({ reconnectIfCurrent(appWidgetId, linkId, cableUrl) }, RECONNECT_DELAY_MS)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                subscriptions.remove(appWidgetId)
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        subscriptions[appWidgetId] = LinkSubscription(linkId, cableUrl, webSocket)
    }

    private fun handleMessage(appWidgetId: Int, linkId: String, text: String) {
        try {
            val envelope = JSONObject(text)
            if (!envelope.has("message")) {
                return
            }

            val link = envelope.optJSONObject("message") ?: return
            if (!link.optBoolean("success", false)) {
                Log.d(TAG, link.optString("why", "Walltaker link update was not successful."))
                return
            }

            val postUrl = link.optString("post_url")
            if (postUrl.isEmpty()) {
                Log.d(TAG, "Walltaker link $linkId did not include a post_url.")
                return
            }

            Log.d(TAG, "Received Walltaker image for widget $appWidgetId link $linkId")
            Preferences.saveCurrentImgPref(this, appWidgetId, postUrl)
            Preferences.saveCurrentSenderPref(this, appWidgetId, link.optString("set_by"))
            handler.post { ImageLookup.setImage(this, appWidgetId) }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read Walltaker message for link $linkId: ${e.message}")
        }
    }

    private fun reconnectIfCurrent(appWidgetId: Int, linkId: String, cableUrl: String) {
        val currentLinkId = Preferences.loadTitlePref(this, appWidgetId).trim()
        val currentCableUrl = WalltakerSettings.cableUrl(this)
        if (currentLinkId == linkId && currentCableUrl == cableUrl && subscriptions[appWidgetId] == null) {
            openSubscription(appWidgetId, linkId, cableUrl)
        }
    }

    private fun closeSubscription(appWidgetId: Int) {
        subscriptions.remove(appWidgetId)?.webSocket?.close(1000, "Widget removed or link changed")
    }

    private fun startForegroundNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Walltaker Connection",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("GG's Walltaker Widget")
            .setContentText("Listening for Walltaker link updates.")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private data class LinkSubscription(
        val linkId: String,
        val cableUrl: String,
        val webSocket: WebSocket
    )

    companion object {
        private const val TAG = "WalltakerWebSocket"
        private const val CHANNEL_ID = "walltaker_websocket_service"
        private const val NOTIFICATION_ID = 2
        private const val RECONNECT_DELAY_MS = 10_000L
        private const val CLIENT_NAME = "GGWidget/1.0"

        fun start(context: Context) {
            val intent = Intent(context, WalltakerWebSocketService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalltakerWebSocketService::class.java))
        }
    }
}
