package com.example.medtime.presentation.data.alarm

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.medtime.R
import com.example.medtime.presentation.MainActivity
import com.example.medtime.presentation.ui.alarm.MedicationAlarmActivity

object AlarmNotificationManager {
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val existingChannel = notificationManager.getNotificationChannel(
            DoseAlarmScheduler.NOTIFICATION_CHANNEL_ID
        )
        if (existingChannel != null) {
            if (existingChannel.importance == NotificationManager.IMPORTANCE_NONE ||
                existingChannel.sound == null ||
                !existingChannel.shouldVibrate()
            ) {
                Log.w(TAG, "NOTIFICATION_BLOCKED reason=channel_configuration")
            }
            return
        }
        val channel = NotificationChannel(
            DoseAlarmScheduler.NOTIFICATION_CHANNEL_ID,
            "Alarmas de medicacion",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recordatorios de dosis de MedTime"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 900)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, sourceIntent: Intent) {
        createChannel(context)
        val blockedReason = notificationBlockReason(context)
        if (blockedReason != null) {
            Log.w(TAG, "NOTIFICATION_BLOCKED reason=$blockedReason")
            return
        }
        val doseId = sourceIntent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID)
        if (doseId == null) {
            Log.w(TAG, "NOTIFICATION_BLOCKED reason=missing_dose_id")
            return
        }
        val medicationName = sourceIntent.getStringExtra(DoseAlarmScheduler.EXTRA_MEDICATION_NAME)
        if (medicationName == null) {
            Log.w(TAG, "NOTIFICATION_BLOCKED reason=missing_medication_name")
            return
        }
        val doseDescription = sourceIntent.getStringExtra(
            DoseAlarmScheduler.EXTRA_DOSE_DESCRIPTION
        ).orEmpty()
        val timeLabel = sourceIntent.getStringExtra(DoseAlarmScheduler.EXTRA_TIME_LABEL).orEmpty()
        val isTest = sourceIntent.getBooleanExtra(DoseAlarmScheduler.EXTRA_TEST_ALARM, false)
        val contentIntent = if (isTest) {
            mainActivityPendingIntent(context)
        } else {
            activityPendingIntent(
                context,
                doseId,
                medicationName,
                doseDescription,
                timeLabel
            )
        }
        val builder = Notification.Builder(
            context,
            DoseAlarmScheduler.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_medtime_notification)
            .setContentTitle(
                if (isTest) "Prueba de alarma MedTime" else "Hora de tomar tu medicamento"
            )
            .setContentText(
                if (isTest) "Prueba de alarma MedTime" else "$medicationName · $doseDescription"
            )
            .setSubText("Programada para $timeLabel")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .setContentIntent(contentIntent)
        if (isTest) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "Detener",
                    actionPendingIntent(context, DoseAlarmScheduler.ACTION_SILENCE, doseId)
                ).build()
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_agenda),
                    "Ya lo tome",
                    actionPendingIntent(context, DoseAlarmScheduler.ACTION_CONFIRM, doseId)
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_recent_history),
                    "Posponer",
                    activityPendingIntent(context, doseId, medicationName, doseDescription, timeLabel)
                ).build()
            )
        }
        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(
                DoseAlarmScheduler.notificationId(doseId),
                notification
            )
            Log.i(TAG, "NOTIFICATION_POSTED test=$isTest")
            Log.i(TAG, "SOUND_STARTED source=notification_channel test=$isTest")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "NOTIFICATION_BLOCKED reason=security_exception", securityException)
        }
    }

    @SuppressLint("MissingPermission")
    fun showActionFailure(context: Context, doseId: String, message: String) {
        createChannel(context)
        val blockedReason = notificationBlockReason(context)
        if (blockedReason != null) {
            Log.w(TAG, "NOTIFICATION_BLOCKED reason=$blockedReason")
            return
        }
        val notification = Notification.Builder(
            context,
            DoseAlarmScheduler.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_medtime_notification)
            .setContentTitle("MedTime no pudo sincronizar")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                DoseAlarmScheduler.notificationId(doseId),
                notification
            )
            Log.i(TAG, "NOTIFICATION_POSTED test=false type=action_failure")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "NOTIFICATION_BLOCKED reason=security_exception", securityException)
        }
    }

    fun cancel(context: Context, doseId: String) {
        NotificationManagerCompat.from(context)
            .cancel(DoseAlarmScheduler.notificationId(doseId))
        Log.i(TAG, "SOUND_STOPPED")
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        doseId: String,
        postponeMinutes: Int? = null
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(DoseAlarmScheduler.EXTRA_DOSE_ID, doseId)
            postponeMinutes?.let {
                putExtra(DoseAlarmScheduler.EXTRA_POSTPONE_MINUTES, it)
            }
        }
        val requestCode = 1000 + DoseAlarmScheduler.notificationId(doseId) + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityPendingIntent(
        context: Context,
        doseId: String,
        medicationName: String,
        doseDescription: String,
        timeLabel: String
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmActivity::class.java).apply {
            putExtra(DoseAlarmScheduler.EXTRA_DOSE_ID, doseId)
            putExtra(DoseAlarmScheduler.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(DoseAlarmScheduler.EXTRA_DOSE_DESCRIPTION, doseDescription)
            putExtra(DoseAlarmScheduler.EXTRA_TIME_LABEL, timeLabel)
        }
        return PendingIntent.getActivity(
            context,
            2000 + DoseAlarmScheduler.notificationId(doseId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        3000,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationBlockReason(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return "post_notifications_permission"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "app_notifications_disabled"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(DoseAlarmScheduler.NOTIFICATION_CHANNEL_ID)
                ?: return "channel_missing"
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return "channel_disabled"
            }
            if (channel.sound == null || !channel.shouldVibrate()) {
                return "channel_configuration"
            }
        }
        return null
    }

    private const val NOTIFICATION_TIMEOUT_MILLIS = 60_000L
    private const val TAG = "MedTimeWatch"
}
