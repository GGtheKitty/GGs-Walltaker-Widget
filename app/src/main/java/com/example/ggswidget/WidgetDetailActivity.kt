package com.example.ggswidget

import android.app.Activity
import android.content.ActivityNotFoundException
import android.app.DownloadManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class WidgetDetailActivity : Activity() {
    private val client = OkHttpClient()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var previewImage: ImageView
    private lateinit var previewVideo: VideoView
    private lateinit var linkIdInput: EditText
    private lateinit var cropCheckBox: CheckBox
    private lateinit var responseTextInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_detail)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        previewImage = findViewById(R.id.detailPreviewImage)
        previewVideo = findViewById(R.id.detailPreviewVideo)
        linkIdInput = findViewById(R.id.detailLinkId)
        cropCheckBox = findViewById(R.id.detailCropCheckBox)
        responseTextInput = findViewById(R.id.detailResponseText)

        linkIdInput.setText(Preferences.loadTitlePref(this, appWidgetId))
        cropCheckBox.isChecked = Preferences.loadCheckedPref(this, appWidgetId) > 0

        findViewById<Button>(R.id.saveWidgetButton).setOnClickListener { saveWidgetSettings() }
        findViewById<Button>(R.id.downloadImageButton).setOnClickListener { downloadCurrentImage() }
        findViewById<Button>(R.id.openDirectLinkButton).setOnClickListener { openDirectLink() }
        findViewById<Button>(R.id.responseDisgustButton).setOnClickListener { sendResponse("disgust") }
        findViewById<Button>(R.id.responseHornyButton).setOnClickListener { sendResponse("ok") }
        findViewById<Button>(R.id.responseLoveButton).setOnClickListener { sendResponse("horny") }
        findViewById<Button>(R.id.responseCameButton).setOnClickListener { sendResponse("came") }
        previewImage.setOnClickListener { openFullScreenImage() }
        previewVideo.setOnClickListener {
            if (previewVideo.isPlaying) {
                previewVideo.pause()
            } else {
                previewVideo.start()
            }
        }

        loadPreviewImage()
        updateReactionState()
    }

    override fun onResume() {
        super.onResume()
        loadPreviewImage()
        updateReactionState()
    }

    override fun onPause() {
        previewVideo.pause()
        super.onPause()
    }

    private fun saveWidgetSettings() {
        val linkId = linkIdInput.text.toString().trim()
        saveTitlePref(this, appWidgetId, linkId)
        saveCheckedPref(this, appWidgetId, if (cropCheckBox.isChecked) 1 else 0)

        WalltakerWebSocketService.start(this)
        updateAppWidget(this, appWidgetId)
        showToast("Widget updated")
    }

    private fun loadPreviewImage() {
        val imageUrl = Preferences.loadCurrentImgPref(this, appWidgetId)
        if (imageUrl.isEmpty()) {
            showImagePreview()
            previewImage.setImageResource(R.drawable.test_img)
            return
        }

        if (isVideoUrl(imageUrl)) {
            showVideoPreview(imageUrl)
            return
        }

        showImagePreview()
        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .placeholder(R.drawable.test_img)
            .error(R.drawable.test_img)
            .into(previewImage)
    }

    private fun openFullScreenImage() {
        val imageUrl = Preferences.loadCurrentImgPref(this, appWidgetId)
        if (imageUrl.isEmpty()) {
            showToast("No image loaded yet")
            return
        }

        if (isVideoUrl(imageUrl)) {
            if (previewVideo.isPlaying) {
                previewVideo.pause()
            } else {
                previewVideo.start()
            }
            return
        }

        startActivity(
            Intent(this, FullscreenImageActivity::class.java)
                .putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl)
        )
    }

    private fun showImagePreview() {
        previewVideo.stopPlayback()
        previewVideo.visibility = android.view.View.GONE
        previewImage.visibility = android.view.View.VISIBLE
    }

    private fun showVideoPreview(videoUrl: String) {
        previewImage.visibility = android.view.View.GONE
        previewVideo.visibility = android.view.View.VISIBLE

        if (previewVideo.tag == videoUrl) {
            return
        }

        val controller = MediaController(this)
        controller.setAnchorView(previewVideo)
        previewVideo.setMediaController(controller)
        previewVideo.setVideoURI(Uri.parse(videoUrl))
        previewVideo.setOnPreparedListener { player ->
            player.isLooping = true
            previewVideo.start()
        }
        previewVideo.setOnErrorListener { _, _, _ ->
            showToast("Unable to play video")
            showImagePreview()
            true
        }
        previewVideo.tag = videoUrl
    }

    private fun isVideoUrl(url: String): Boolean {
        return VideoWidgetAnimator.isAnimatedVideo(url)
    }

    private fun downloadCurrentImage() {
        val imageUrl = Preferences.loadCurrentImgPref(this, appWidgetId)
        if (imageUrl.isEmpty()) {
            showToast("No image loaded yet")
            return
        }

        val uri = Uri.parse(imageUrl)
        val extension = MimeTypeMap.getFileExtensionFromUrl(imageUrl).ifEmpty { "jpg" }
        val fileName = "walltaker_${System.currentTimeMillis()}.$extension"
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription("Downloading Walltaker image")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        showToast("Download started")
    }

    private fun openDirectLink() {
        val linkId = linkIdInput.text.toString().trim()
        if (linkId.isEmpty()) {
            showToast("Enter a link ID first")
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WalltakerSettings.linkUrl(this, linkId))))
        } catch (e: ActivityNotFoundException) {
            showToast("No browser found")
        }
    }

    private fun sendResponse(type: String) {
        val linkId = linkIdInput.text.toString().trim()
        val apiKey = WalltakerSettings.apiKey(this)
        val responseText = responseTextInput.text.toString()

        if (linkId.isEmpty()) {
            showToast("Enter a link ID first")
            return
        }

        if (!hasApiKey()) {
            showToast(getString(R.string.api_key_required))
            return
        }

        val bodyJson = JSONObject()
            .put("api_key", apiKey)
            .put("type", type)
            .put("text", responseText)
            .toString()

        val request = Request.Builder()
            .url(WalltakerSettings.responseUrl(this, linkId))
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        setReactionButtonsEnabled(false)
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setReactionButtonsEnabled(true)
                    showToast("Reaction failed")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                runOnUiThread {
                    setReactionButtonsEnabled(true)
                    if (response.isSuccessful) {
                        showToast("Reaction sent")
                    } else {
                        showToast("Reaction failed: ${response.code}")
                    }
                }
            }
        })
    }

    private fun setReactionButtonsEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.responseDisgustButton).isEnabled = enabled
        findViewById<Button>(R.id.responseHornyButton).isEnabled = enabled
        findViewById<Button>(R.id.responseLoveButton).isEnabled = enabled
        findViewById<Button>(R.id.responseCameButton).isEnabled = enabled
    }

    private fun updateReactionState() {
        val enabled = hasApiKey()
        responseTextInput.isEnabled = enabled
        responseTextInput.isFocusable = enabled
        responseTextInput.isFocusableInTouchMode = enabled
        responseTextInput.isClickable = enabled
        responseTextInput.alpha = if (enabled) 1.0f else 0.5f

        if (enabled) {
            if (responseTextInput.text.toString() == getString(R.string.api_key_required)) {
                responseTextInput.setText("")
            }
            responseTextInput.hint = getString(R.string.reaction_message_hint)
        } else {
            responseTextInput.setText(R.string.api_key_required)
        }

        setReactionButtonsEnabled(enabled)
    }

    private fun hasApiKey(): Boolean {
        return WalltakerSettings.apiKey(this).isNotEmpty()
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
