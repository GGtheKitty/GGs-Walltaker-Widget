package com.example.ggswidget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.AppWidgetTarget
import com.bumptech.glide.request.transition.Transition

class ImageLookup {
    companion object{
        fun setImage(context: Context, appWidgetId: Int, views: RemoteViews = buildWidgetViews(context, appWidgetId)) {
            val imageLinkID = Preferences.loadTitlePref(context, appWidgetId)
            val currentImageUrl = Preferences.loadCurrentImgPref(context, appWidgetId)
            if (currentImageUrl.isEmpty()) {
                Log.d("IMAGE_LOOKUP", "No Walltaker image cached yet for widget $appWidgetId.")
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
                return
            }

            val awt: AppWidgetTarget = object : AppWidgetTarget(context.applicationContext, R.id.imageView2, views, appWidgetId) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    super.onResourceReady(resource, transition)
                }
            }

            Glide.with(context.applicationContext)
                .asBitmap()
                .override(1900,1900)
                .fitCenter()
                .load(currentImageUrl)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Bitmap>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d("IMAGE_LOOKUP", "Unable to load current Walltaker image.")
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Bitmap>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        val loadedImage = resource ?: return false
                        val prevImg = Preferences.loadPrevImagePref(context,appWidgetId)
                        val currentImg = bitmapToString(loadedImage)
                        if(prevImg != "" && currentImg != prevImg){
                            val sp = PreferenceManager.getDefaultSharedPreferences(context)
                            if(sp.getBoolean("notificationToggle",false)){
                                NotificationActivity().createNotificationChannel(context)
                                NotificationActivity().sendNotification(context,appWidgetId,Preferences.loadCurrentSenderPref(context,appWidgetId),imageLinkID)
                            }

                            Preferences.savePrevImagePref(context,appWidgetId,loadedImage)
                        }
                        return false
                    }
                })
                .into(awt)

            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun buildWidgetViews(context: Context, appWidgetId: Int): RemoteViews {
            val layout = if (Preferences.loadCheckedPref(context, appWidgetId) > 0) {
                R.layout.image_widget_cropped
            } else {
                R.layout.image_widget
            }
            val views = RemoteViews(context.packageName, layout)
            setClickable(context, views, appWidgetId)
            return views
        }
    }
}
