package com.example.medtime.presentation.data.model

import java.time.Instant

data class DashboardDose(
    val id: String,
    val medication: Medication,
    val schedule: MedicationSchedule,
    val dose: ScheduledDose,
    val effectiveAt: Instant
)

data class DashboardData(
    val nextDose: DashboardDose?,
    val pendingTodayCount: Int,
    val medicationCount: Int,
    val pendingDoses: List<DashboardDose>,
    val upcomingDoses: List<DashboardDose>
)
