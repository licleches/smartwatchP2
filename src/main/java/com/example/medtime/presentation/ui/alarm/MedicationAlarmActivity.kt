package com.example.medtime.presentation.ui.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.medtime.presentation.data.alarm.AlarmNotificationManager
import com.example.medtime.presentation.data.alarm.DoseAlarmScheduler
import com.example.medtime.presentation.ui.theme.MedtimeTheme

class MedicationAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID)
        val medicationName = intent.getStringExtra(DoseAlarmScheduler.EXTRA_MEDICATION_NAME)
        val doseDescription = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_DESCRIPTION)
        val timeLabel = intent.getStringExtra(DoseAlarmScheduler.EXTRA_TIME_LABEL)
        if (doseId == null || medicationName == null) {
            finish()
            return
        }

        setContent {
            MedtimeTheme {
                MedicationAlarmScreen(
                    medicationName = medicationName,
                    doseDescription = doseDescription.orEmpty(),
                    timeLabel = timeLabel.orEmpty(),
                    onConfirm = {
                        sendAction(DoseAlarmScheduler.ACTION_CONFIRM, doseId)
                    },
                    onPostpone = { minutes ->
                        sendAction(DoseAlarmScheduler.ACTION_POSTPONE, doseId, minutes)
                    },
                    onSilence = {
                        sendAction(DoseAlarmScheduler.ACTION_SILENCE, doseId)
                    }
                )
            }
        }
    }

    private fun sendAction(action: String, doseId: String, minutes: Int? = null) {
        sendBroadcast(Intent(this, com.example.medtime.presentation.data.alarm.MedicationAlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(DoseAlarmScheduler.EXTRA_DOSE_ID, doseId)
            minutes?.let { putExtra(DoseAlarmScheduler.EXTRA_POSTPONE_MINUTES, it) }
        })
        if (action == DoseAlarmScheduler.ACTION_SILENCE) {
            AlarmNotificationManager.cancel(this, doseId)
        }
        finish()
    }
}
