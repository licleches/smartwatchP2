package com.example.medtime.presentation.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> DoseAlarmScheduler(context).restore()
        }
    }
}
