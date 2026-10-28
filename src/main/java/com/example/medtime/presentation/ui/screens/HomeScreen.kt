package com.example.medtime.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.example.medtime.BuildConfig
import com.example.medtime.R
import com.example.medtime.presentation.data.model.DoseAction
import com.example.medtime.presentation.data.model.DashboardData
import com.example.medtime.presentation.data.model.DashboardDose
import com.example.medtime.presentation.utils.formatDoseCountdown
import com.example.medtime.presentation.utils.formatDoseTime
import com.example.medtime.presentation.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    state: HomeUiState,
    actionInProgress: Boolean,
    onRefresh: () -> Unit,
    onDoseAction: (String, DoseAction, Int?) -> Unit,
    exactAlarmsAllowed: Boolean,
    notificationsAllowed: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCredentialInvalid: () -> Unit,
    onTestAlarm: () -> Unit
) {
    LaunchedEffect(state) {
        if (state is HomeUiState.CredentialInvalid) {
            onCredentialInvalid()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        when (state) {
            HomeUiState.Loading -> LoadingContent()
            is HomeUiState.Error -> ErrorContent(state.message, onRefresh)
            HomeUiState.CredentialInvalid -> LoadingContent()
            is HomeUiState.Ready -> DashboardContent(
                data = state.data,
                actionInProgress = actionInProgress,
                onRefresh = onRefresh,
                onDoseAction = onDoseAction,
                exactAlarmsAllowed = exactAlarmsAllowed,
                notificationsAllowed = notificationsAllowed,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onTestAlarm = onTestAlarm
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.load_error_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    actionInProgress: Boolean,
    onRefresh: () -> Unit,
    onDoseAction: (String, DoseAction, Int?) -> Unit,
    exactAlarmsAllowed: Boolean,
    notificationsAllowed: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTestAlarm: () -> Unit
) {
    var showUnpairInstructions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.today),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp
            )
            TextButton(onClick = { showUnpairInstructions = true }) {
                Text(stringResource(R.string.unlink_watch))
            }
        }
        if (showUnpairInstructions) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.unlink_watch_instructions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))

        if (!notificationsAllowed) {
            PermissionCard(
                message = stringResource(R.string.notifications_permission_required),
                buttonLabel = stringResource(R.string.open_settings),
                onClick = onOpenNotificationSettings
            )
        }
        if (!exactAlarmsAllowed) {
            PermissionCard(
                message = stringResource(R.string.exact_alarm_permission_required),
                buttonLabel = stringResource(R.string.open_settings),
                onClick = onRequestExactAlarmPermission
            )
        }

        if (data.nextDose != null) {
            NextDoseCard(
                dose = data.nextDose,
                actionInProgress = actionInProgress,
                onDoseAction = onDoseAction
            )
        } else {
            EmptyDoseCard(data.medicationCount)
        }

        val nextDoseId = data.nextDose?.id
        val otherPendingDoses = data.pendingDoses.filterNot { it.id == nextDoseId }
        if (otherPendingDoses.isNotEmpty()) {
            SectionTitle(stringResource(R.string.pending_doses_title))
            otherPendingDoses.take(MAX_VISIBLE_DOSES).forEach { dose ->
                PendingDoseCard(
                    dose = dose,
                    actionInProgress = actionInProgress,
                    onDoseAction = onDoseAction
                )
            }
        }

        val otherUpcomingDoses = data.upcomingDoses.filterNot { it.id == nextDoseId }
        if (otherUpcomingDoses.isNotEmpty()) {
            SectionTitle(stringResource(R.string.upcoming_doses_title))
            otherUpcomingDoses.take(MAX_VISIBLE_DOSES).forEach { dose ->
                UpcomingDoseCard(dose)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.pending_today, data.pendingTodayCount),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.refresh))
        }
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onTestAlarm) {
                Text(stringResource(R.string.debug_test_alarm))
            }
        }
    }
}

@Composable
private fun NextDoseCard(
    dose: DashboardDose,
    actionInProgress: Boolean,
    onDoseAction: (String, DoseAction, Int?) -> Unit
) {
    var showPostponeOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.next_dose),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = dose.medication.nombre,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 21.sp
        )
        val doseDescription = listOfNotNull(
            dose.medication.prescribedDose,
            dose.medication.presentacion
        ).joinToString(" · ")
        if (doseDescription.isNotBlank()) {
            Text(
                text = doseDescription,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatDoseTime(dose.effectiveAt),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 30.sp
        )
        Text(
            text = if (dose.effectiveAt <= java.time.Instant.now()) {
                stringResource(R.string.dose_due_now)
            } else {
                formatDoseCountdown(dose.effectiveAt)
            },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 13.sp
        )
        Text(
            text = doseStatusLabel(dose.dose.estado),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        if (actionInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
        }
        DoseActionButton(
            label = stringResource(R.string.dose_taken),
            enabled = !actionInProgress,
            onClick = { onDoseAction(dose.id, DoseAction.CONFIRM, null) }
        )
        if (showPostponeOptions) {
            Text(
                text = stringResource(R.string.dose_postpone_options),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 12.sp
            )
            listOf(15, 30, 60, 120).forEach { minutes ->
                DoseActionButton(
                    label = "$minutes min",
                    enabled = !actionInProgress,
                    onClick = {
                        showPostponeOptions = false
                        onDoseAction(dose.id, DoseAction.POSTPONE, minutes)
                    }
                )
            }
            TextButton(
                onClick = { showPostponeOptions = false },
                enabled = !actionInProgress
            ) {
                Text(stringResource(R.string.dose_cancel))
            }
        } else {
            DoseActionButton(
                label = stringResource(R.string.dose_postpone),
                enabled = !actionInProgress,
                onClick = { showPostponeOptions = true }
            )
        }
        DoseActionButton(
            label = stringResource(R.string.dose_omit),
            enabled = !actionInProgress,
            onClick = { onDoseAction(dose.id, DoseAction.OMIT, null) }
        )
    }
}

@Composable
private fun PendingDoseCard(
    dose: DashboardDose,
    actionInProgress: Boolean,
    onDoseAction: (String, DoseAction, Int?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(dose.medication.nombre, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
        Text(
            text = listOfNotNull(dose.medication.prescribedDose, dose.medication.presentacion)
                .joinToString(" · ")
                .ifBlank { stringResource(R.string.dose_without_description) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = "${formatDoseTime(dose.effectiveAt)} · ${doseStatusLabel(dose.dose.estado)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { onDoseAction(dose.id, DoseAction.CONFIRM, null) },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.dose_taken))
            }
            Button(
                onClick = { onDoseAction(dose.id, DoseAction.POSTPONE, 15) },
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.dose_postpone_short))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun UpcomingDoseCard(dose: DashboardDose) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(dose.medication.nombre, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
        Text(
            text = "${formatDoseTime(dose.effectiveAt)} · ${formatDoseCountdown(dose.effectiveAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PermissionCard(message: String, buttonLabel: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(10.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
        TextButton(onClick = onClick) {
            Text(buttonLabel)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun doseStatusLabel(status: String): String = when (status) {
    "programada" -> stringResource(R.string.dose_status_programada)
    "pendiente" -> stringResource(R.string.dose_status_pendiente)
    "pospuesta" -> stringResource(R.string.dose_status_pospuesta)
    "sin_confirmar" -> stringResource(R.string.dose_status_sin_confirmar)
    "confirmada" -> stringResource(R.string.dose_status_confirmada)
    "omitida" -> stringResource(R.string.dose_status_omitida)
    else -> status
}

@Composable
private fun DoseActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
private fun EmptyDoseCard(medicationCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp)
    ) {
        Text(
            text = if (medicationCount == 0) {
                stringResource(R.string.no_active_medications)
            } else {
                stringResource(R.string.no_scheduled_medications)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp
        )
    }
}

private const val MAX_VISIBLE_DOSES = 5
