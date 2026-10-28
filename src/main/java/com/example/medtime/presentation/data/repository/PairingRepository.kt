package com.example.medtime.presentation.data.repository

import com.example.medtime.presentation.data.local.DeviceCredential
import com.example.medtime.presentation.data.local.DeviceCredentialStore
import com.example.medtime.presentation.data.local.PendingPairing
import com.example.medtime.presentation.data.remote.EdgeFunctionsClient
import com.example.medtime.presentation.data.remote.PairingStartResponse
import com.example.medtime.presentation.data.remote.PairingStatusResponse
import java.time.Instant

class PairingRepository(
    private val client: EdgeFunctionsClient,
    private val credentialStore: DeviceCredentialStore
) {
    fun hasValidCredential(): Boolean = credentialStore.load() != null

    suspend fun startPairing(): PairingStartResponse = client.startPairing()

    fun loadPendingPairing(): PendingPairing? = credentialStore.loadPendingPairing()

    fun savePendingPairing(response: PairingStartResponse, expiresAt: Instant): PendingPairing =
        PendingPairing(
            requestId = response.requestId,
            pairingCode = response.pairingCode,
            challengeSecret = response.challengeSecret,
            expiresAt = expiresAt
        ).also(credentialStore::savePendingPairing)

    suspend fun getPairingStatus(
        requestId: String,
        challengeSecret: String
    ): PairingStatusResponse = client.getPairingStatus(requestId, challengeSecret)

    fun hasCompleteCredential(response: PairingStatusResponse): Boolean =
        runCatching { buildCredential(response) }.isSuccess

    fun saveApprovedCredential(response: PairingStatusResponse): DeviceCredential {
        return buildCredential(response).also(credentialStore::save)
    }

    private fun buildCredential(response: PairingStatusResponse): DeviceCredential {
        val deviceId = response.deviceId?.takeIf(String::isNotBlank)
            ?: throw PairingProtocolException("La respuesta no contiene device_id")
        val deviceToken = response.deviceToken?.takeIf(String::isNotBlank)
            ?: throw PairingProtocolException("La respuesta no contiene device_token")
        val expiresAt = response.credentialExpiresAt()
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?.takeIf { it.isAfter(Instant.now()) }
            ?: throw PairingProtocolException("La respuesta no contiene una expiración válida")

        return DeviceCredential(deviceId, deviceToken, expiresAt)
    }

    fun clearCredential() {
        credentialStore.clear()
    }

    fun clearPendingPairing() {
        credentialStore.clearPendingPairing()
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_CLAIMED = "claimed"
    }
}

class PairingProtocolException(message: String) : IllegalStateException(message)
