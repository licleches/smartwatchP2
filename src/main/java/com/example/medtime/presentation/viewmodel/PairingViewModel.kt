package com.example.medtime.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.medtime.presentation.data.local.PendingPairing
import com.example.medtime.presentation.data.remote.EdgeFunctionException
import com.example.medtime.presentation.data.remote.DeviceCredentialRejectedException
import com.example.medtime.presentation.data.repository.PairingRepository
import com.example.medtime.presentation.data.repository.PairingProtocolException
import com.example.medtime.presentation.data.repository.PairingRepository.Companion.STATUS_APPROVED
import com.example.medtime.presentation.data.repository.PairingRepository.Companion.STATUS_CLAIMED
import com.example.medtime.presentation.data.repository.PairingRepository.Companion.STATUS_EXPIRED
import com.example.medtime.presentation.data.repository.PairingRepository.Companion.STATUS_PENDING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import kotlin.math.max

sealed interface PairingUiState {
    data object Starting : PairingUiState
    data class Pending(
        val pairingCode: String,
        val remainingSeconds: Long,
        val errorMessage: String? = null
    ) : PairingUiState
    data object Expired : PairingUiState
    data class Error(val message: String) : PairingUiState
    data object Ready : PairingUiState
}

class PairingViewModel(
    private val repository: PairingRepository
) : ViewModel() {
    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Starting)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private var pairingJob: Job? = null

    init {
        restoreOrStart()
    }

    fun startPairing() {
        Log.d(TAG, "startPairing invoked")
        pairingJob?.cancel()
        repository.clearPendingPairing()
        launchNewPairing()
    }

    private fun launchNewPairing() {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            _state.value = PairingUiState.Starting
            try {
                val response = repository.startPairing()
                val expiresAt = response.expiresAt
                    .let { value -> runCatching { Instant.parse(value) }.getOrNull() }
                    ?: throw IllegalStateException("El servidor devolvió una expiración inválida")
                require(response.requestId.isNotBlank())
                require(response.challengeSecret.isNotBlank())
                require(response.pairingCode.matches(Regex("\\d{6}")))

                if (!expiresAt.isAfter(Instant.now())) {
                    _state.value = PairingUiState.Expired
                    return@launch
                }

                val pendingPairing = repository.savePendingPairing(response, expiresAt)
                runPolling(pendingPairing)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "startPairing failed type=${error::class.java.name}",
                    error
                )
                _state.value = PairingUiState.Error(errorMessage(error))
            }
        }
    }

    fun clearAndStartPairing() {
        repository.clearCredential()
        launchNewPairing()
    }

    private fun restoreOrStart() {
        if (repository.hasValidCredential()) {
            _state.value = PairingUiState.Ready
        } else {
            val pendingPairing = repository.loadPendingPairing()
            if (pendingPairing != null) {
                resumePendingPairing(pendingPairing)
            } else {
                launchNewPairing()
            }
        }
    }

    private fun resumePendingPairing(pendingPairing: PendingPairing) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            runPolling(pendingPairing)
        }
    }

    private suspend fun runPolling(
        pendingPairing: PendingPairing
    ) {
        val requestId = pendingPairing.requestId
        val challengeSecret = pendingPairing.challengeSecret
        val pairingCode = pendingPairing.pairingCode
        val expiresAt = pendingPairing.expiresAt
        var nextPollAt = System.currentTimeMillis() + INITIAL_POLL_DELAY_MILLIS
        var backoffMillis = INITIAL_POLL_DELAY_MILLIS

        while (currentCoroutineContext().isActive) {
            val remainingSeconds = max(0, expiresAt.epochSecond - Instant.now().epochSecond)
            if (remainingSeconds <= 0) {
                _state.value = PairingUiState.Expired
                return
            }

            updatePending(pairingCode, remainingSeconds, null)
            delay(SECOND_MILLIS)
            if (System.currentTimeMillis() < nextPollAt) {
                continue
            }

            try {
                val response = repository.getPairingStatus(requestId, challengeSecret)
                when (response.status.trim().lowercase()) {
                    STATUS_PENDING -> {
                        backoffMillis = INITIAL_POLL_DELAY_MILLIS
                        nextPollAt = System.currentTimeMillis() + backoffMillis
                    }
                    STATUS_APPROVED -> {
                        repository.saveApprovedCredential(response)
                        Log.i(TAG, "pairing approved; credential stored, opening HomeScreen")
                        _state.value = PairingUiState.Ready
                        return
                    }
                    STATUS_EXPIRED -> {
                        _state.value = PairingUiState.Expired
                        return
                    }
                    STATUS_CLAIMED -> {
                        if (repository.hasCompleteCredential(response)) {
                            Log.i(
                                TAG,
                                "pairing claimed with complete credential; opening HomeScreen"
                            )
                            repository.saveApprovedCredential(response)
                            _state.value = PairingUiState.Ready
                            return
                        }
                        _state.value = PairingUiState.Error(
                            "Este código ya fue utilizado. Genera uno nuevo."
                        )
                        return
                    }
                    else -> if (repository.hasCompleteCredential(response)) {
                        Log.w(
                            TAG,
                            "unexpected pairing status from watch-pair-status " +
                                "status=${response.status.trim()} with complete credential fields"
                        )
                        repository.saveApprovedCredential(response)
                        Log.i(TAG, "pairing credential accepted; opening HomeScreen")
                        _state.value = PairingUiState.Ready
                        return
                    } else {
                        Log.w(
                            TAG,
                            "unexpected pairing status from watch-pair-status " +
                                "status=${response.status.trim()} without complete credential fields"
                        )
                        _state.value = PairingUiState.Error(
                            "El servidor devolvió un estado de vinculación no reconocido."
                        )
                        return
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updatePending(
                    pairingCode = pairingCode,
                    remainingSeconds = remainingSeconds,
                    errorMessage = pollingErrorMessage(error)
                )
                nextPollAt = System.currentTimeMillis() + backoffMillis
                backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    private fun updatePending(
        pairingCode: String,
        remainingSeconds: Long,
        errorMessage: String?
    ) {
        _state.value = PairingUiState.Pending(pairingCode, remainingSeconds, errorMessage)
    }

    private fun errorMessage(error: Exception): String = when (error) {
        is EdgeFunctionException -> when {
            error.errorCode == "ACTIVE_REQUEST" ->
                "Este reloj ya tiene una solicitud activa. Espera su expiración o autorízala desde MedTime Mobile."
            error.errorCode == "INVALID_INPUT" ->
                "Supabase rechazó los datos de vinculación (HTTP ${error.statusCode})."
            error.errorCode == "INVALID_DEVICE_TYPE" ->
                "Supabase rechazó el tipo de dispositivo (HTTP ${error.statusCode})."
            else -> when (error.statusCode) {
            400 -> "Supabase rechazó la solicitud (HTTP 400). Revisa el contrato de pairing."
            401 -> "Supabase rechazó la API key (HTTP 401). Revisa la publishable key."
            403 -> "Supabase denegó la petición (HTTP 403). Revisa la configuración de la función."
            409 -> "Supabase indicó un conflicto (HTTP 409). Inténtalo nuevamente."
            429 -> "Supabase limitó las solicitudes (HTTP 429). Espera unos segundos."
            500 -> "Supabase devolvió un error interno (HTTP 500)."
            else -> "Supabase devolvió HTTP ${error.statusCode} al solicitar el código."
            }
        }
        is DeviceCredentialRejectedException ->
            "La credencial del reloj fue rechazada. Inicia una nueva vinculación."
        is PairingProtocolException ->
            "La respuesta de Supabase no coincide con el contrato de pairing."
        is UnknownHostException ->
            "No se pudo resolver Supabase (DNS). Comprueba Internet del emulador."
        is SSLException ->
            "Falló la conexión HTTPS con Supabase (SSL/TLS)."
        is SocketTimeoutException ->
            "Supabase no respondió a tiempo (timeout)."
        is ConnectException ->
            "No se pudo conectar con Supabase. Comprueba Internet del emulador."
        is SerializationException ->
            "La respuesta JSON de Supabase no coincide con EdgeFunctionModels.kt."
        is IOException ->
            "Falló la red antes de completar la petición (${error::class.java.simpleName})."
        else ->
            "No se pudo solicitar un código (${error::class.java.simpleName}). Comprueba Logcat."
    }

    private fun pollingErrorMessage(error: Exception): String = when (error) {
        is EdgeFunctionException ->
            "No se pudo comprobar la autorización. Reintentando..."
        else -> "Sin conexión con el servicio. Reintentando..."
    }

    class Factory(
        private val repository: PairingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PairingViewModel::class.java))
            return PairingViewModel(repository) as T
        }
    }

    private companion object {
        const val TAG = "MedTimeWatch"
        const val INITIAL_POLL_DELAY_MILLIS = 3_000L
        const val MAX_BACKOFF_MILLIS = 30_000L
        const val SECOND_MILLIS = 1_000L
    }
}
