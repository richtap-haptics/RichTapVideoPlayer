package com.example.richtapvideoplayer

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import java.util.*
import kotlin.math.roundToInt

object Utils {
    fun showToast(text: String, duration: Int = Toast.LENGTH_SHORT): Unit {
        Toast.makeText(MyApp.context, text, duration).show()
    }

    private val formatBuilder = StringBuilder()
    private val formatter = Formatter(formatBuilder, Locale.getDefault())

    fun stringForTime(timeMs: Int): String {
        val totalSeconds = (timeMs / 1000.0).roundToInt()
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        formatBuilder.setLength(0)
        return if (hours > 0) {
            formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            formatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }

    fun getMediaDuration(uri: Uri): Int {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(MyApp.context, uri)
            val duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toInt()
                    ?: -1
            retriever.release()
            return duration
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return -1
    }
}

// A smart way to access App.context from anywhere
//  NOTE: remember to add android:name=".MyApp" to <application> in AndroidManifest.xml
class MyApp : Application() {
    companion object {
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}