@file:Suppress("DEPRECATION")

package com.example.ggswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

object GifWidgetAnimator {
    private const val TAG = "GifWidgetAnimator"
    private const val FRAME_DELAY_MS = 33L
    private const val MAX_FRAME_DIMENSION = 720

    private val client = OkHttpClient()
    private val executor = Executors.newCachedThreadPool()
    private val handler = Handler(Looper.getMainLooper())
    private val jobs = ConcurrentHashMap<Int, AnimationJob>()

    fun isAnimatedGif(url: String): Boolean {
        val lowerUrl = url.substringBefore('?').lowercase()
        return lowerUrl.endsWith(".gif")
    }

    fun start(context: Context, appWidgetId: Int, url: String) {
        val existing = jobs[appWidgetId]
        if (existing?.url == url) {
            return
        }

        stop(appWidgetId)
        val appContext = context.applicationContext
        val job = AnimationJob(url)
        jobs[appWidgetId] = job

        executor.execute {
            try {
                Log.d(TAG, "Loading animated GIF for widget $appWidgetId")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "GGWidget/1.0")
                    .build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    response.body?.bytes() ?: throw IllegalStateException("Empty GIF body")
                }

                val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Unable to decode GIF")
                val duration = if (movie.duration() > 0) movie.duration() else 1000
                val frameSize = scaledFrameSize(movie.width(), movie.height())

                handler.post {
                    animateFrame(appContext, appWidgetId, movie, duration, frameSize.first, frameSize.second, job)
                }
            } catch (e: Exception) {
                jobs.remove(appWidgetId, job)
                Log.d(TAG, "Unable to animate GIF for widget $appWidgetId: ${e.message}")
            }
        }
    }

    fun stop(appWidgetId: Int) {
        jobs.remove(appWidgetId)?.cancelled?.set(true)
    }

    fun stop(appWidgetIds: IntArray) {
        appWidgetIds.forEach { stop(it) }
    }

    fun stopAll() {
        jobs.values.forEach { it.cancelled.set(true) }
        jobs.clear()
    }

    private fun animateFrame(
        context: Context,
        appWidgetId: Int,
        movie: Movie,
        duration: Int,
        width: Int,
        height: Int,
        job: AnimationJob
    ) {
        if (job.cancelled.get() || jobs[appWidgetId] !== job) {
            return
        }

        val elapsed = ((System.currentTimeMillis() - job.startedAtMs) % duration).toInt()
        movie.setTime(elapsed)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width.toFloat() / movie.width(), height.toFloat() / movie.height())
        movie.draw(canvas, 0f, 0f)

        val views = RemoteViews(context.packageName, widgetLayout(context, appWidgetId))
        views.setImageViewBitmap(R.id.imageView2, bitmap)
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)

        handler.postDelayed({
            animateFrame(context, appWidgetId, movie, duration, width, height, job)
        }, FRAME_DELAY_MS)
    }

    private fun scaledFrameSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) {
            return MAX_FRAME_DIMENSION to MAX_FRAME_DIMENSION
        }

        val scale = min(1f, MAX_FRAME_DIMENSION.toFloat() / maxOf(width, height).toFloat())
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun widgetLayout(context: Context, appWidgetId: Int): Int {
        return if (Preferences.loadCheckedPref(context, appWidgetId) > 0) {
            R.layout.image_widget_cropped
        } else {
            R.layout.image_widget
        }
    }

    private data class AnimationJob(
        val url: String,
        val startedAtMs: Long = System.currentTimeMillis(),
        val cancelled: AtomicBoolean = AtomicBoolean(false)
    )
}
