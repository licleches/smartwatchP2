package com.example.medtime.presentation.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.medtime.presentation.data.model.DashboardDose
import com.example.medtime.presentation.utils.formatDoseTime
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DoseAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val store = AlarmScheduleStore(appContext)

    fun reschedule(doses: List<DashboardDose>) {
        val now = System.currentTimeMillis()
        val records = doses
            .asSequence()
            .map { dose -> dose.toAlarmRecord() }
            .filter { it.triggerAtMillis > now }
            .distinctBy(AlarmRecord::doseId)
            .sortedBy(AlarmRecord::triggerAtMillis)
            .toList()

        store.load().forEach(::cancelRecord)
        store.save(records)
        records.forEach(::scheduleRecord)
        Log.d(TAG, "alarms rescheduled count=${records.size}")
    }

    fun scheduleTestAlarm(delayMillis: Long = TEST_DELAY_MILLIS) {
        require(delayMillis > 0)
        cancelTestAlarm()
        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        scheduleRecord(
            record = testAlarmRecord(triggerAtMillis),
            isTest = true
        )
    }

    fun cancelTestAlarm() {
        alarmManager.cancel(alarmPendingIntent(testAlarmRecord(0L)))
        Log.d(TAG, "test alarm cancelled")
    }

    fun restore() {
        val now = System.currentTimeMillis()
        val records = store.load()
            .filter { it.triggerAtMillis > now }
            .distinctBy(AlarmRecord::doseId)
        store.load().forEach(::cancelRecord)
        store.save(records)
        records.forEach(::scheduleRecord)
        Log.d(TAG, "alarms restored count=${records.size}")
    }

    fun cancel(doseId: String) {
        if (doseId == TEST_DOSE_ID) {
            cancelTestAlarm()
            return
        }
        val records = store.load()
        records.firstOrNull { it.doseId == doseId }?.let(::cancelRecord)
        store.save(records.filterNot { it.doseId == doseId })
    }

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return appContext.getSystemService(android.app.NotificationManager::class.java)
            ?.areNotificationsEnabled() != false
    }

    private fun scheduleRecord(record: AlarmRecord, isTest: Boolean = record.isTest) {
        val triggerAt = record.triggerAtMillis
        if (triggerAt <= System.currentTimeMillis()) return
        val pendingIntent = alarmPendingIntent(record)
        val exactAllowed = canScheduleExactAlarms()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAllowed) {
            Log.w(TAG, "ALARM_PERMISSION_MISSING test=$isTest")
        }
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || exactAllowed
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            Log.i(TAG, "ALARM_SCHEDULED test=$isTest exact=$exact")
        } catch (securityException: SecurityException) {
            Log.w(TAG, "ALARM_PERMISSION_MISSING test=$isTest; using inexact alarm")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
            Log.i(TAG, "ALARM_SCHEDULED test=$isTest exact=false")
        }
    }

    private fun cancelRecord(record: AlarmRecord) {
        alarmManager.cancel(alarmPendingIntent(record))
    }

    private fun alarmPendingIntent(record: AlarmRecord): PendingIntent {
        val intent = Intent(appContext, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("medtime://dose/${record.doseId}")
            putExtra(EXTRA_DOSE_ID, record.doseId)
            putExtra(EXTRA_MEDICATION_NAME, record.medicationName)
            putExtra(EXTRA_DOSE_DESCRIPTION, record.doseDescription)
            putExtra(EXTRA_TIME_LABEL, record.timeLabel)
            putExtra(EXTRA_TEST_ALARM, record.isTest)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(record.doseId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun DashboardDose.toAlarmRecord(): AlarmRecord = AlarmRecord(
        doseId = id,
        triggerAtMillis = effectiveAt.toEpochMilli(),
        medicationName = medication.nombre,
        doseDescription = listOfNotNull(
            medication.prescribedDose,
            medication.presentacion
        ).joinToString(" · ").ifBlank { "Dosis indicada" },
        timeLabel = formatDoseTime(effectiveAt)
    )

    private fun testAlarmRecord(triggerAtMillis: Long): AlarmRecord = AlarmRecord(
        doseId = TEST_DOSE_ID,
        triggerAtMillis = triggerAtMillis,
        medicationName = "Prueba de alarma MedTime",
        doseDescription = "Prueba local",
        timeLabel = "Prueba",
        isTest = true
    )

    private fun requestCode(doseId: String): Int = doseId.hashCode() and Int.MAX_VALUE

    private class AlarmScheduleStore(context: Context) {
        private val preferences = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

        fun load(): List<AlarmRecord> = runCatching {
            JSON.decodeFromString<List<AlarmRecord>>(
                preferences.getString(KEY_RECORDS, "[]").orEmpty()
            )
        }.getOrDefault(emptyList())

        fun save(records: List<AlarmRecord>) {
            preferences.edit()
                .putString(KEY_RECORDS, JSON.encodeToString(records))
                .apply()
        }

        private companion object {
            const val STORE_NAME = "medtime_alarm_schedule"
            const val KEY_RECORDS = "records"
            val JSON = Json { ignoreUnknownKeys = true }
        }
    }

    @Serializable
    private data class AlarmRecord(
        val doseId: String,
        val triggerAtMillis: Long,
        val medicationName: String,
        val doseDescription: String,
        val timeLabel: String,
        val isTest: Boolean = false
    )

    companion object {
        const val ACTION_FIRE = "com.example.medtime.action.DOSE_ALARM"
        const val ACTION_CONFIRM = "com.example.medtime.action.CONFIRM_DOSE"
        const val ACTION_POSTPONE = "com.example.medtime.action.POSTPONE_DOSE"
        const val ACTION_SILENCE = "com.example.medtime.action.SILENCE_DOSE"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_DOSE_DESCRIPTION = "extra_dose_description"
        const val EXTRA_TIME_LABEL = "extra_time_label"
        const val EXTRA_POSTPONE_MINUTES = "extra_postpone_minutes"
        const val EXTRA_TEST_ALARM = "extra_test_alarm"
        const val NOTIFICATION_CHANNEL_ID = "medication_alarms_v2"
        const val TEST_DOSE_ID = "debug-test-alarm"
        const val TEST_DELAY_MILLIS = 60_000L
        const val TAG = "MedTimeWatch"

        fun notificationId(doseId: String): Int = doseId.hashCode() and Int.MAX_VALUE
    }
}
