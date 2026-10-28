package com.example.medtime.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.medtime.presentation.data.local.DeviceCredentialStore
import com.example.medtime.presentation.data.alarm.DoseAlarmScheduler
import com.example.medtime.presentation.data.alarm.AlarmNotificationManager
import com.example.medtime.presentation.data.remote.EdgeFunctionsClient
import com.example.medtime.presentation.data.repository.DoseRepository
import com.example.medtime.presentation.data.repository.MedicationRepository
import com.example.medtime.presentation.data.repository.PairingRepository
import com.example.medtime.presentation.navigation.MedtimeApp

class MainActivity : ComponentActivity() {
    private val edgeFunctionsClient by lazy {
        EdgeFunctionsClient(applicationContext)
    }
    private val credentialStore by lazy {
        DeviceCredentialStore(applicationContext)
    }
    private val pairingRepository by lazy {
        PairingRepository(edgeFunctionsClient, credentialStore)
    }
    private val medicationRepository by lazy {
        MedicationRepository(edgeFunctionsClient, credentialStore)
    }
    private val doseRepository by lazy {
        DoseRepository(edgeFunctionsClient, credentialStore)
    }
    private val alarmScheduler by lazy {
        DoseAlarmScheduler(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            MedtimeApp(
                pairingRepository = pairingRepository,
                medicationRepository = medicationRepository,
                doseRepository = doseRepository,
                alarmScheduler = alarmScheduler,
                exactAlarmsAllowed = alarmScheduler::canScheduleExactAlarms,
                notificationsAllowed = alarmScheduler::notificationsAllowed,
                onRequestExactAlarmPermission = ::requestExactAlarmPermission,
                onOpenNotificationSettings = ::openNotificationSettings
            )
        }
        AlarmNotificationManager.createChannel(applicationContext)
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
