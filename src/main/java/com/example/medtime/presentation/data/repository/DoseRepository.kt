package com.example.medtime.presentation.data.repository

import com.example.medtime.presentation.data.local.DeviceCredentialStore
import com.example.medtime.presentation.data.model.DoseAction
import com.example.medtime.presentation.data.remote.EdgeFunctionsClient
import com.example.medtime.presentation.data.remote.DeviceCredentialRejectedException

class DoseRepository(
    private val client: EdgeFunctionsClient,
    private val credentialStore: DeviceCredentialStore
) {
    suspend fun applyAction(
        doseId: String,
        action: DoseAction,
        postponeMinutes: Int? = null
    ) {
        val credential = credentialStore.load() ?: throw DeviceCredentialRejectedException()
        val serverStatus = when (action) {
            DoseAction.CONFIRM -> "confirmada"
            DoseAction.POSTPONE -> "pospuesta"
            DoseAction.OMIT -> "omitida"
        }
        client.updateDose(
            credential = credential,
            doseId = doseId,
            status = serverStatus,
            postponeMinutes = postponeMinutes
        )
    }
}
