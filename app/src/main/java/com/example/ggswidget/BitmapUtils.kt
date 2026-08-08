package com.example.ggswidget

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

fun bitmapToString(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}
