package com.example.medtime.presentation.data.repository

import com.example.medtime.presentation.data.local.DeviceCredentialStore
import com.example.medtime.presentation.data.model.DashboardData
import com.example.medtime.presentation.data.model.DashboardDose
import com.example.medtime.presentation.data.remote.EdgeFunctionsClient
import com.example.medtime.presentation.data.remote.DeviceCredentialRejectedException
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MedicationRepository(
    private val client: EdgeFunctionsClient,
    private val credentialStore: DeviceCredentialStore
) {
    suspend fun loadDashboard(): DashboardData {
        val credential = credentialStore.load() ?: throw DeviceCredentialRejectedException()
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.now(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant()
        val queryEnd = today.plusDays(7).atStartOfDay(zone).toInstant()
        val payload = client.listDoses(credential, todayStart, queryEnd)
        val normalizedPayload = payload.normalized()
        val activeMedications = normalizedPayload.medications.filter { it.activo }
        val medicationIds = activeMedications.map { it.id }.filter(String::isNotBlank).toSet()
        val schedules = normalizedPayload.schedules.filter {
            it.activo && it.medicationId in medicationIds
        }
        val scheduleIds = schedules.map { it.id }.filter(String::isNotBlank).toSet()
        val pendingStatuses = setOf("programada", "pendiente", "pospuesta", "sin_confirmar")
        val doses = normalizedPayload.doses.filter {
            it.id.isNotBlank() && it.scheduleId in scheduleIds && it.estado in pendingStatuses
        }

        val medicationsById = activeMedications.associateBy { it.id }
        val schedulesById = schedules.associateBy { it.id }
        val dashboardDoses = doses.mapNotNull { dose ->
            val schedule = schedulesById[dose.scheduleId] ?: return@mapNotNull null
            val medication = medicationsById[schedule.medicationId] ?: return@mapNotNull null
            val effectiveAt = dose.postponedUntil?.let(::parseInstant)
                ?: parseInstant(dose.scheduledFor)
                ?: return@mapNotNull null
            DashboardDose(
                id = dose.id,
                medication = medication,
                schedule = schedule,
                dose = dose,
                effectiveAt = effectiveAt
            )
        }

        val now = Instant.now()
        val sortedDoses = dashboardDoses.sortedBy(DashboardDose::effectiveAt)
        val pendingDoses = sortedDoses.filter { it.effectiveAt <= now }
        val upcomingDoses = sortedDoses.filter { it.effectiveAt > now }
        Log.d(
            TAG,
            "dashboard loaded medications=${activeMedications.size} " +
                "pendingDoses=${pendingDoses.size} upcomingDoses=${upcomingDoses.size}"
        )
        return DashboardData(
            nextDose = pendingDoses.firstOrNull() ?: upcomingDoses.firstOrNull(),
            pendingTodayCount = pendingDoses.size,
            medicationCount = activeMedications.size,
            pendingDoses = pendingDoses,
            upcomingDoses = upcomingDoses
        )
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }.getOrNull()

    private companion object {
        const val TAG = "MedTimeWatch"
    }
}
