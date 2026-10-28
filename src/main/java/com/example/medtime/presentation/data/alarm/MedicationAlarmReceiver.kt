package com.example.medtime.presentation.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DoseAlarmScheduler.ACTION_FIRE) return
        val isTest = intent.getBooleanExtra(DoseAlarmScheduler.EXTRA_TEST_ALARM, false)
        Log.i(TAG, "ALARM_TRIGGERED test=$isTest")
        AlarmNotificationManager.show(context, intent)
    }

    private companion object {
        const val TAG = "MedTimeWatch"
    }
}
