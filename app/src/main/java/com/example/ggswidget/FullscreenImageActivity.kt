package com.example.ggswidget

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide

class FullscreenImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fullscreen_image)

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        val imageView = findViewById<ImageView>(R.id.fullscreenImageView)

        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .into(imageView)

        imageView.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_IMAGE_URL = "com.example.ggswidget.EXTRA_IMAGE_URL"
    }
}
