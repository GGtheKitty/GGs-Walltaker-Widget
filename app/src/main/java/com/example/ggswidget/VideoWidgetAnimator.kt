package com.example.ggswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.widget.RemoteViews
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

object VideoWidgetAnimator {
    private const val TAG = "VideoWidgetAnimator"
    private const val HIGH_FPS_FRAME_DIMENSION = 320
    private const val MEDIUM_FPS_FRAME_DIMENSION = 480
    private const val LOW_FPS_FRAME_DIMENSION = 720
    private const val FALLBACK_SOURCE_FPS = 30f
    private const val MAX_WIDGET_FPS = 30f
    private const val MAX_CACHED_VIDEO_DURATION_US = 15_000_000L
    private const val MAX_CACHED_FRAMES = 180

    private val client = OkHttpClient()
    private val executor = Executors.newCachedThreadPool()
    private val jobs = ConcurrentHashMap<Int, AnimationJob>()

    fun isAnimatedVideo(url: String): Boolean {
        val lowerUrl = url.substringBefore('?').lowercase()
        return lowerUrl.endsWith(".mp4") ||
            lowerUrl.endsWith(".webm") ||
            lowerUrl.endsWith(".mov") ||
            lowerUrl.endsWith(".m4v")
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
            var retriever: MediaMetadataRetriever? = null
            try {
                Log.d(TAG, "Loading animated video for widget $appWidgetId")
                val videoFile = downloadVideo(appContext, appWidgetId, url, job)
                job.cacheFile = videoFile

                retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)
                val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.times(1000L)
                    ?.takeIf { it > 0L }
                    ?: 1_000_000L
                val sourceFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                    ?.takeIf { it > 0f }
                    ?: FALLBACK_SOURCE_FPS
                val playbackProfile = choosePlaybackProfile(durationUs, sourceFps)
                val frameSize = readScaledFrameSize(retriever, playbackProfile.maxFrameDimension)
                Log.d(
                    TAG,
                    "Animating widget $appWidgetId at ${playbackProfile.targetFps}fps " +
                        "from ${sourceFps}fps source, duration ${durationUs / 1_000_000f}s, " +
                        "frame cap ${playbackProfile.maxFrameDimension}px"
                )

                if (durationUs <= MAX_CACHED_VIDEO_DURATION_US) {
                    val cachedFrames = prerenderFrames(retriever, durationUs, playbackProfile, frameSize, job)
                    playCachedFrames(appContext, appWidgetId, cachedFrames, playbackProfile, job)
                } else {
                    playLiveFrames(appContext, appWidgetId, retriever, durationUs, playbackProfile, frameSize, job)
                }
            } catch (e: Exception) {
                jobs.remove(appWidgetId, job)
                Log.d(TAG, "Unable to animate video for widget $appWidgetId: ${e.message}")
            } finally {
                retriever?.release()
            }
        }
    }

    private fun prerenderFrames(
        retriever: MediaMetadataRetriever,
        durationUs: Long,
        playbackProfile: PlaybackProfile,
        frameSize: FrameSize?,
        job: AnimationJob
    ): List<Bitmap> {
        val startedMs = System.currentTimeMillis()
        val desiredFrames = (durationUs / playbackProfile.frameStepUs).toInt().coerceAtLeast(1)
        val frameCount = desiredFrames.coerceAtMost(MAX_CACHED_FRAMES)
        val frameStepUs = durationUs / frameCount
        val frames = ArrayList<Bitmap>(frameCount)

        Log.d(TAG, "Prerendering $frameCount frames for short video playback")
        repeat(frameCount) { index ->
            if (job.cancelled.get()) {
                return frames
            }

            val frameTimeUs = index * frameStepUs
            getScaledFrame(retriever, frameTimeUs, frameSize)?.let { frames.add(it) }
        }

        job.cachedFrames.addAll(frames)
        val elapsedMs = System.currentTimeMillis() - startedMs
        Log.d(TAG, "Prerendered ${frames.size} frames in ${elapsedMs}ms")
        return frames
    }

    private fun playCachedFrames(
        context: Context,
        appWidgetId: Int,
        frames: List<Bitmap>,
        playbackProfile: PlaybackProfile,
        job: AnimationJob
    ) {
        if (frames.isEmpty()) {
            throw IllegalStateException("No cached video frames")
        }

        var frameIndex = 0
        var frameCount = 0
        val startedMs = System.currentTimeMillis()
        while (!job.cancelled.get() && jobs[appWidgetId] === job) {
            val loopStartedMs = System.currentTimeMillis()
            updateWidgetFrame(context, appWidgetId, frames[frameIndex])
            frameCount += 1
            if (frameCount == 1 || frameCount % 30 == 0) {
                logAchievedFps(appWidgetId, frameCount, startedMs, "cached")
            }

            frameIndex = (frameIndex + 1) % frames.size
            sleepForCadence(playbackProfile.frameDelayMs, loopStartedMs)
        }
    }

    private fun playLiveFrames(
        context: Context,
        appWidgetId: Int,
        retriever: MediaMetadataRetriever,
        durationUs: Long,
        playbackProfile: PlaybackProfile,
        frameSize: FrameSize?,
        job: AnimationJob
    ) {
        var frameTimeUs = 0L
        var frameCount = 0
        val startedMs = System.currentTimeMillis()
        while (!job.cancelled.get() && jobs[appWidgetId] === job) {
            val loopStartedMs = System.currentTimeMillis()
            val frame = getScaledFrame(retriever, frameTimeUs, frameSize)
            if (frame != null) {
                updateWidgetFrame(context, appWidgetId, frame)
                frameCount += 1
                if (frameCount == 1 || frameCount % 20 == 0) {
                    logAchievedFps(appWidgetId, frameCount, startedMs, "live")
                }
            } else {
                Log.d(TAG, "No video frame available for widget $appWidgetId at ${frameTimeUs}us")
            }

            frameTimeUs = (frameTimeUs + playbackProfile.frameStepUs) % durationUs
            sleepForCadence(playbackProfile.frameDelayMs, loopStartedMs)
        }
    }

    private fun sleepForCadence(frameDelayMs: Long, loopStartedMs: Long) {
        val elapsedMs = System.currentTimeMillis() - loopStartedMs
        val sleepMs = frameDelayMs - elapsedMs
        if (sleepMs > 0L) {
            Thread.sleep(sleepMs)
        }
    }

    private fun logAchievedFps(appWidgetId: Int, frameCount: Int, startedMs: Long, mode: String) {
        val elapsedSeconds = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L) / 1000f
        val achievedFps = frameCount / elapsedSeconds
        Log.d(TAG, "Updated widget $appWidgetId with $mode frame $frameCount, achieved ${achievedFps}fps")
    }

    fun stop(appWidgetId: Int) {
        jobs.remove(appWidgetId)?.cancel()
    }

    fun stop(appWidgetIds: IntArray) {
        appWidgetIds.forEach { stop(it) }
    }

    fun stopAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private fun downloadVideo(context: Context, appWidgetId: Int, url: String, job: AnimationJob): File {
        val outputFile = File(context.cacheDir, "widget_video_${appWidgetId}_${url.hashCode()}.mp4")
        if (outputFile.exists() && outputFile.length() > 0L) {
            return outputFile
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GGWidget/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty video body")
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val input = body.byteStream()
                while (!job.cancelled.get()) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        }

        return outputFile
    }

    private fun updateWidgetFrame(context: Context, appWidgetId: Int, bitmap: Bitmap) {
        val views = RemoteViews(context.packageName, widgetLayout(context, appWidgetId))
        views.setImageViewBitmap(R.id.imageView2, bitmap)
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
    }

    private fun choosePlaybackProfile(durationUs: Long, sourceFps: Float): PlaybackProfile {
        val durationSeconds = durationUs / 1_000_000f
        val durationCapFps = when {
            durationSeconds < 15f -> 30f
            durationSeconds <= 30f -> 15f
            else -> 5f
        }
        val targetFps = minOf(sourceFps, durationCapFps, MAX_WIDGET_FPS).coerceAtLeast(1f)
        val frameDelayMs = (1000f / targetFps).roundToInt().coerceAtLeast(1).toLong()
        val maxFrameDimension = when {
            targetFps > 15f -> HIGH_FPS_FRAME_DIMENSION
            targetFps > 5f -> MEDIUM_FPS_FRAME_DIMENSION
            else -> LOW_FPS_FRAME_DIMENSION
        }
        return PlaybackProfile(
            targetFps = targetFps,
            frameDelayMs = frameDelayMs,
            frameStepUs = frameDelayMs * 1000L,
            maxFrameDimension = maxFrameDimension
        )
    }

    private fun readScaledFrameSize(retriever: MediaMetadataRetriever, maxFrameDimension: Int): FrameSize? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null

        val maxDimension = maxOf(width, height)
        if (maxDimension <= maxFrameDimension) {
            return FrameSize(width, height)
        }

        val scale = min(1f, maxFrameDimension.toFloat() / maxDimension.toFloat())
        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return FrameSize(scaledWidth, scaledHeight)
    }

    private fun getScaledFrame(
        retriever: MediaMetadataRetriever,
        frameTimeUs: Long,
        frameSize: FrameSize?
    ): Bitmap? {
        if (frameSize != null) {
            return retriever.getScaledFrameAtTime(
                frameTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                frameSize.width,
                frameSize.height
            )
        }

        return retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
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
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        var cacheFile: File? = null,
        val cachedFrames: MutableList<Bitmap> = mutableListOf()
    ) {
        fun cancel() {
            cancelled.set(true)
            cachedFrames.forEach { frame ->
                if (!frame.isRecycled) {
                    frame.recycle()
                }
            }
            cachedFrames.clear()
        }
    }

    private data class PlaybackProfile(
        val targetFps: Float,
        val frameDelayMs: Long,
        val frameStepUs: Long,
        val maxFrameDimension: Int
    )

    private data class FrameSize(
        val width: Int,
        val height: Int
    )
}
