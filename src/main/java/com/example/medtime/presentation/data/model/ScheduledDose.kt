package com.example.medtime.presentation.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduledDose(
    val id: String = "",
    @SerialName("horario_id") val scheduleId: String = "",
    @SerialName("programada_para") val scheduledFor: String = "",
    val estado: String = "",
    @SerialName("confirmada_en") val confirmedAt: String? = null,
    @SerialName("pospuesta_hasta") val postponedUntil: String? = null
)
