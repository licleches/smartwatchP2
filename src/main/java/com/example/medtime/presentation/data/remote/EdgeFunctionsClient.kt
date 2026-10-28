package com.example.medtime.presentation.data.remote

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.medtime.BuildConfig
import com.example.medtime.presentation.data.local.DeviceCredential
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import java.time.Duration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class EdgeFunctionsClient(
    context: Context,
    private val httpClient: HttpClient = createHttpClient()
) {
    private val deviceIdentifier = Settings.Secure.getString(
        context.applicationContext.contentResolver,
        Settings.Secure.ANDROID_ID
    )?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("El emulador no tiene ANDROID_ID disponible")

    suspend fun startPairing(): PairingStartResponse {
        val payload: JsonObject = post(
            function = "watch-pair-start",
            body = PairingStartRequest(
                deviceName = "MedTimeWatch",
                deviceType = "wear_os",
                deviceIdentifier = deviceIdentifier,
                platform = "wearos"
            )
        )
        Log.d(TAG, "pairing response keys=${payload.keys.sorted().joinToString(",")}")
        val response = runCatching {
            JSON.decodeFromJsonElement<PairingStartResponse>(payload)
        }.onFailure { error ->
            Log.e(
                TAG,
                "pairing response JSON shape invalid type=${error::class.java.name}",
                error
            )
        }.getOrThrow()
        Log.d(
            TAG,
            "pairing response shape=" +
                "requestIdPresent=${response.requestId.isNotBlank()} " +
                "pairingCodeLength=${response.pairingCode.length} " +
                "challengePresent=${response.challengeSecret.isNotBlank()} " +
                "expiresAtPresent=${response.expiresAt.isNotBlank()}"
        )
        return response
    }

    suspend fun getPairingStatus(
        requestId: String,
        challengeSecret: String
    ): PairingStatusResponse {
        val payload: JsonObject = post(
            function = "watch-pair-status",
            body = PairingStatusRequest(requestId, challengeSecret)
        )
        val status = payload["status"]?.jsonPrimitive?.contentOrNull
        Log.d(
            TAG,
            "pairing status response keys=${payload.keys.sorted().joinToString(",")} " +
                "status=${status ?: "<missing>"}"
        )
        return JSON.decodeFromJsonElement(payload)
    }

    suspend fun listDoses(
        credential: DeviceCredential,
        from: Instant,
        to: Instant
    ): WatchDosesPayload {
        require(!to.isBefore(from))
        require(Duration.between(from, to) <= MAX_DOSE_QUERY_RANGE)

        val responsePayload: JsonObject = post(
            function = "watch-doses-list",
            body = WatchDosesRequest(from.toString(), to.toString()),
            credential = credential
        )
        Log.d(
            TAG,
            "watch-doses-list response keys=${responsePayload.keys.sorted().joinToString(",")}"
        )
        val doses = responsePayload["doses"] as? JsonArray
        val firstDose = doses?.firstOrNull() as? JsonObject
        val medicationKeys = (firstDose?.get("medication") as? JsonObject)
            ?.keys
            ?.sorted()
            ?.joinToString(",")
            ?: "none"
        val scheduleKeys = (firstDose?.get("schedule") as? JsonObject)
            ?.keys
            ?.sorted()
            ?.joinToString(",")
            ?: "none"
        val statuses = doses.orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull }
            .distinct()
            .sorted()
        Log.d(
            TAG,
            "watch-doses-list dosesCount=${doses?.size ?: 0} " +
                "firstDoseKeys=${firstDose?.keys?.sorted()?.joinToString(",") ?: "none"} " +
                "medicationKeys=$medicationKeys scheduleKeys=$scheduleKeys " +
                "statuses=${statuses.joinToString(",")}"
        )
        return JSON.decodeFromJsonElement<WatchDosesResponse>(responsePayload)
            .toPayload()
    }

    suspend fun updateDose(
        credential: DeviceCredential,
        doseId: String,
        status: String,
        postponeMinutes: Int? = null
    ) {
        require(status in ALLOWED_DOSE_STATUSES)
        if (status == "pospuesta") {
            require(postponeMinutes != null && postponeMinutes in ALLOWED_POSTPONE_MINUTES)
        } else {
            require(postponeMinutes == null)
        }

        Log.d(
            TAG,
            "dose update request status=$status " +
                "postponeMinutesPresent=${postponeMinutes != null} " +
                "doseIdPresent=${doseId.isNotBlank()}"
        )
        postWithBody(
            function = "watch-dose-update",
            body = WatchDoseUpdateRequest(doseId, status, postponeMinutes),
            credential = credential
        )
    }

    private suspend inline fun <reified T> post(
        function: String,
        body: Any,
        credential: DeviceCredential? = null
    ): T {
        val url = functionUrl(function)
        Log.d(
            TAG,
            "request start function=$function url=$url " +
                "contentType=application/json credentialPresent=${credential != null}"
        )
        try {
            val response = httpClient.post(url) {
                addCommonHeaders(credential)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            Log.d(TAG, "response received function=$function status=${response.status.value}")
            return response.requireSuccess(function, credential != null).body()
        } catch (error: Exception) {
            Log.e(
                TAG,
                "request failed function=$function type=${error::class.java.name}",
                error
            )
            throw error
        }
    }

    private suspend fun postWithBody(
        function: String,
        body: Any,
        credential: DeviceCredential
    ) {
        val url = functionUrl(function)
        Log.d(
            TAG,
            "request start function=$function url=$url " +
                "contentType=application/json credentialPresent=true"
        )
        try {
            val response = httpClient.post(url) {
                addCommonHeaders(credential)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            Log.d(TAG, "response received function=$function status=${response.status.value}")
            response.requireSuccess(function, hasCredential = true)
        } catch (error: Exception) {
            Log.e(
                TAG,
                "request failed function=$function type=${error::class.java.name}",
                error
            )
            throw error
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addCommonHeaders(
        credential: DeviceCredential?
    ) {
        header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        if (credential != null) {
            header(HttpHeaders.Authorization, "Bearer ${credential.deviceToken}")
        }
    }

    private suspend fun HttpResponse.requireSuccess(
        function: String,
        hasCredential: Boolean
    ): HttpResponse {
        if (status.value in 200..299) {
            return this
        }

        val errorBody = runCatching { body<String>() }
            .onFailure { error ->
                Log.w(
                    TAG,
                    "error response body unavailable function=$function " +
                        "type=${error::class.java.name}"
                )
            }
            .getOrNull()
        val parsedError = errorBody?.let { body ->
            runCatching { Json.decodeFromString<FunctionError>(body) }
                .onFailure { error ->
                    Log.w(
                        TAG,
                        "error response JSON could not be parsed " +
                            "type=${error::class.java.name}"
                    )
                }
                .getOrNull()
        }
        val errorCode = parsedError?.code?.let { value ->
            runCatching { value.jsonPrimitive.content }.getOrNull()
        } ?: parsedError?.error
        val errorDetail = parsedError?.message ?: parsedError?.error
        Log.w(
            TAG,
            "http error function=$function status=${status.value} " +
                "errorCode=${safeErrorCode(errorCode)} " +
                "detail=${safeErrorDetail(errorDetail)}"
        )
        if (hasCredential &&
            (status.value == 401 || status.value == 403 || errorCode in DEVICE_CREDENTIAL_ERROR_CODES)
        ) {
            throw DeviceCredentialRejectedException()
        }
        throw EdgeFunctionException(
            function = function,
            statusCode = status.value,
            errorCode = safeErrorCode(errorCode),
            errorDetail = safeErrorDetail(errorDetail)
        )
    }

    private fun functionUrl(function: String): String {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(baseUrl.startsWith("https://")) {
            "SUPABASE_URL no esta configurada con HTTPS"
        }
        return "$baseUrl/functions/v1/$function"
    }

    private fun safeErrorCode(value: String?): String =
        value?.takeIf { it.matches(SAFE_ERROR_CODE_REGEX) } ?: "unavailable"

    private fun safeErrorDetail(value: String?): String {
        if (value.isNullOrBlank()) return "unavailable"
        return value
            .replace(SENSITIVE_VALUE_REGEX, "$1=<redacted>")
            .replace(Regex("\\b\\d{6}\\b"), "<code>")
            .take(MAX_LOG_DETAIL_LENGTH)
    }

    private companion object {
        const val TAG = "MedTimeWatch"
        val SAFE_ERROR_CODE_REGEX = Regex("[A-Za-z0-9_.-]{1,64}")
        val SENSITIVE_VALUE_REGEX = Regex(
            "(?i)(token|secret|password|challenge_secret|authorization|apikey|publishablekey|key)" +
                "\\s*[:=]\\s*[^\\s,;}]+"
        )
        const val MAX_LOG_DETAIL_LENGTH = 180
        val ALLOWED_DOSE_STATUSES = setOf("confirmada", "pospuesta", "omitida")
        val ALLOWED_POSTPONE_MINUTES = setOf(15, 30, 60, 120)
        val DEVICE_CREDENTIAL_ERROR_CODES = setOf(
            "device_token_expired",
            "device_token_revoked",
            "device_not_authorized",
            "invalid_device_token"
        )
        val MAX_DOSE_QUERY_RANGE: Duration = Duration.ofDays(7)
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(JSON)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}

@Serializable
private data class FunctionError(
    val code: JsonElement? = null,
    val error: String? = null,
    val message: String? = null
)

class EdgeFunctionException(
    val function: String,
    val statusCode: Int,
    val errorCode: String = "unavailable",
    val errorDetail: String = "unavailable"
) : IllegalStateException("La función $function devolvió HTTP $statusCode")

class DeviceCredentialRejectedException : IllegalStateException(
    "La credencial del reloj ya no es válida"
)
