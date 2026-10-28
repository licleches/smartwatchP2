package com.example.medtime.presentation.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medtime.presentation.data.local.DeviceCredentialStore
import com.example.medtime.presentation.data.model.DoseAction
import com.example.medtime.presentation.data.remote.DeviceCredentialRejectedException
import com.example.medtime.presentation.data.remote.EdgeFunctionsClient
import com.example.medtime.presentation.data.repository.DoseRepository
import com.example.medtime.presentation.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationAlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID)
                    ?: return@launch
                when (intent.action) {
                    DoseAlarmScheduler.ACTION_SILENCE -> {
                        DoseAlarmScheduler(appContext).cancel(doseId)
                        AlarmNotificationManager.cancel(appContext, doseId)
                    }
                    DoseAlarmScheduler.ACTION_CONFIRM,
                    DoseAlarmScheduler.ACTION_POSTPONE -> {
                        val action = if (intent.action == DoseAlarmScheduler.ACTION_CONFIRM) {
                            DoseAction.CONFIRM
                        } else {
                            DoseAction.POSTPONE
                        }
                        val minutes = intent.getIntExtra(
                            DoseAlarmScheduler.EXTRA_POSTPONE_MINUTES,
                            -1
                        ).takeIf { it > 0 }
                        val client = EdgeFunctionsClient(appContext)
                        val credentialStore = DeviceCredentialStore(appContext)
                        DoseRepository(client, credentialStore).applyAction(
                            doseId = doseId,
                            action = action,
                            postponeMinutes = minutes
                        )
                        val scheduler = DoseAlarmScheduler(appContext)
                        scheduler.cancel(doseId)
                        AlarmNotificationManager.cancel(appContext, doseId)
                        val dashboard = MedicationRepository(client, credentialStore).loadDashboard()
                        scheduler.reschedule(
                            dashboard.pendingDoses + dashboard.upcomingDoses
                        )
                    }
                }
            } catch (_: DeviceCredentialRejectedException) {
                val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return@launch
                AlarmNotificationManager.showActionFailure(
                    appContext,
                    doseId,
                    "Vincula nuevamente el reloj para sincronizar la dosis."
                )
            } catch (_: Exception) {
                val doseId = intent.getStringExtra(DoseAlarmScheduler.EXTRA_DOSE_ID) ?: return@launch
                AlarmNotificationManager.showActionFailure(
                    appContext,
                    doseId,
                    "No se pudo actualizar la dosis. Inténtalo desde Home."
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
