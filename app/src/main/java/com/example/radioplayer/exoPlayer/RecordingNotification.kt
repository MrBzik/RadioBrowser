package com.example.radioplayer.exoPlayer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.radioplayer.R
import com.example.radioplayer.utils.Constants.COMMAND_STOP_RECORDING
import com.example.radioplayer.utils.Constants.RECORDING_CHANNEL_ID

class RecordingNotification (
    private val context : Context,
    private val currentStation : () -> String
        ) {

    fun showNotification(){

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stopRecordingIntent = PendingIntent.getService(
            context, 7, Intent(context, RadioService::class.java).also {
                   it.action = COMMAND_STOP_RECORDING
            },
           PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_start_recording)
            .setContentTitle("Recording")
            .setContentText(currentStation())
            .setUsesChronometer(true)
//            .setColor(ContextCompat.getColor(context, R.color.main_background))
            .addAction(0, "Stop",
                stopRecordingIntent
            ).build()

         notificationManager.notify(7, notification)

    }


}