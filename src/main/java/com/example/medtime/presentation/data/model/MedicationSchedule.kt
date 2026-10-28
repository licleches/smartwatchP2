package com.example.medtime.presentation.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicationSchedule(
    val id: String = "",
    @SerialName("medicamento_id") val medicationId: String = "",
    val hora: String? = null,
    val activo: Boolean = true
)
