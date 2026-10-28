package com.example.medtime.presentation.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val id: String = "",
    @SerialName("usuario_id") val userId: String = "",
    val nombre: String = "",
    val presentacion: String? = null,
    @SerialName("dosis_indicada") val prescribedDose: String? = null,
    val instrucciones: String? = null,
    @SerialName("fecha_inicio") val startDate: String? = null,
    @SerialName("fecha_fin") val endDate: String? = null,
    val activo: Boolean = true
)
