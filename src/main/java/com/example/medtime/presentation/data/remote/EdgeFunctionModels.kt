package com.example.medtime.presentation.data.remote

import com.example.medtime.presentation.data.model.Medication
import com.example.medtime.presentation.data.model.MedicationSchedule
import com.example.medtime.presentation.data.model.ScheduledDose
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class PairingStartRequest(
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("device_identifier") val deviceIdentifier: String,
    val platform: String? = null
)

@Serializable
data class PairingStartResponse(
    @SerialName("request_id") val requestId: String,
    @SerialName("code") val pairingCode: String,
    @SerialName("challenge_secret") val challengeSecret: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class PairingStatusRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("challenge_secret") val challengeSecret: String
)

@Serializable
data class PairingStatusResponse(
    val status: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_token") val deviceToken: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("token_expires_at") val tokenExpiresAt: String? = null
) {
    fun credentialExpiresAt(): String? = tokenExpiresAt ?: expiresAt
}

@Serializable
data class WatchDosesRequest(
    val from: String,
    val to: String
)

@Serializable
data class WatchDoseUpdateRequest(
    @SerialName("dose_id") val doseId: String,
    val status: String,
    @SerialName("postpone_minutes") val postponeMinutes: Int? = null
)

@Serializable
data class WatchDosesPayload(
    @SerialName("medicamentos") val medications: List<Medication> = emptyList(),
    @SerialName("horarios") val schedules: List<MedicationSchedule> = emptyList(),
    @SerialName("dosis_programadas") val doses: List<ScheduledDose> = emptyList(),
    @SerialName("medications") val englishMedications: List<Medication> = emptyList(),
    @SerialName("schedules") val englishSchedules: List<MedicationSchedule> = emptyList(),
    @SerialName("doses") val englishDoses: List<ScheduledDose> = emptyList()
) {
    fun normalized(): WatchDosesPayload = WatchDosesPayload(
        medications = medications.ifEmpty { englishMedications },
        schedules = schedules.ifEmpty { englishSchedules },
        doses = doses.ifEmpty { englishDoses }
    )
}

@Serializable
data class WatchDoseItem(
    @SerialName("dose_id") val doseId: String = "",
    @SerialName("scheduled_for") val scheduledFor: String = "",
    val status: String = "",
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("postponed_until") val postponedUntil: String? = null,
    val medication: WatchMedication? = null,
    val schedule: WatchSchedule? = null
) {
    fun toPayload(): WatchDosesPayload {
        val medicationModel = medication?.let {
            Medication(
                id = it.id,
                nombre = it.name,
                presentacion = it.presentation,
                prescribedDose = it.prescribedDose,
                instrucciones = it.instructions
            )
        }
        val scheduleModel = schedule?.let {
            MedicationSchedule(
                id = it.id,
                medicationId = medication?.id.orEmpty(),
                hora = it.time
            )
        }
        return WatchDosesPayload(
            medications = listOfNotNull(medicationModel),
            schedules = listOfNotNull(scheduleModel),
            doses = listOf(
                ScheduledDose(
                    id = doseId,
                    scheduleId = schedule?.id.orEmpty(),
                    scheduledFor = scheduledFor,
                    estado = status,
                    confirmedAt = confirmedAt,
                    postponedUntil = postponedUntil
                )
            )
        )
    }
}

@Serializable
data class WatchMedication(
    val id: String = "",
    val name: String = "",
    val presentation: String? = null,
    @SerialName("prescribed_dose") val prescribedDose: String? = null,
    val instructions: String? = null
)

@Serializable
data class WatchSchedule(
    val id: String = "",
    val time: String? = null,
    val weekdays: JsonElement? = null
)

fun WatchDosesResponse.toPayload(): WatchDosesPayload {
    val joined = englishDoses
        .map(WatchDoseItem::toPayload)
        .fold(WatchDosesPayload()) { result, item ->
            WatchDosesPayload(
                medications = result.medications + item.medications,
                schedules = result.schedules + item.schedules,
                doses = result.doses + item.doses
            )
        }
    val direct = WatchDosesPayload(
        medications = medications.ifEmpty { englishMedications }.ifEmpty { joined.medications },
        schedules = schedules.ifEmpty { englishSchedules }.ifEmpty { joined.schedules },
        doses = doses.ifEmpty { joined.doses }
    )
    val nested = (data as? kotlinx.serialization.json.JsonObject)?.let { value ->
        runCatching {
            Json.decodeFromJsonElement<WatchDosesPayload>(value).normalized()
        }.getOrNull()
    }
    val result = WatchDosesPayload(
        medications = direct.medications.ifEmpty { nested?.medications.orEmpty() },
        schedules = direct.schedules.ifEmpty { nested?.schedules.orEmpty() },
        doses = direct.doses.ifEmpty { nested?.doses.orEmpty() }
    ).normalized()
    return result.copy(
        medications = result.medications.distinctBy { it.id },
        schedules = result.schedules.distinctBy { it.id },
        doses = result.doses.distinctBy { it.id }
    )
}

@Serializable
data class WatchDosesResponse(
    @SerialName("medicamentos") val medications: List<Medication> = emptyList(),
    @SerialName("horarios") val schedules: List<MedicationSchedule> = emptyList(),
    @SerialName("dosis_programadas") val doses: List<ScheduledDose> = emptyList(),
    @SerialName("medications") val englishMedications: List<Medication> = emptyList(),
    @SerialName("schedules") val englishSchedules: List<MedicationSchedule> = emptyList(),
    @SerialName("doses") val englishDoses: List<WatchDoseItem> = emptyList(),
    val data: JsonElement? = null
) {
    fun topLevelPayload(): WatchDosesPayload = toPayload()
}
