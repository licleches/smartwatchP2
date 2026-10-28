package com.example.medtime.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.example.medtime.R
import com.example.medtime.presentation.viewmodel.PairingUiState

@Composable
fun PairingScreen(
    state: PairingUiState,
    onStartPairing: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.login_title),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pairing_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))

            when (state) {
                PairingUiState.Starting -> StartingContent()
                is PairingUiState.Pending -> PendingContent(state, onStartPairing)
                PairingUiState.Expired -> ExpiredContent(onStartPairing)
                is PairingUiState.Error -> ErrorContent(state.message, onStartPairing)
                PairingUiState.Ready -> SuccessContent()
            }
        }
    }
}

@Composable
private fun StartingContent() {
    Spacer(Modifier.height(8.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.pairing_requesting_code),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PendingContent(
    state: PairingUiState.Pending,
    onStartPairing: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.pairing_instruction),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = state.pairingCode,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 32.sp,
        letterSpacing = 5.sp
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.pairing_time_remaining,
            formatRemainingTime(state.remainingSeconds)
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.pairing_waiting),
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    if (state.errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.errorMessage,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onStartPairing,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (state.errorMessage == null) {
                stringResource(R.string.pairing_new_code)
            } else {
                stringResource(R.string.pairing_retry)
            }
        )
    }
}

@Composable
private fun ExpiredContent(onStartPairing: () -> Unit) {
    Text(
        text = stringResource(R.string.pairing_expired),
        color = MaterialTheme.colorScheme.error,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onStartPairing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pairing_new_code))
    }
}

@Composable
private fun ErrorContent(message: String, onStartPairing: () -> Unit) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onStartPairing, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pairing_retry))
    }
}

@Composable
private fun SuccessContent() {
    Text(
        text = stringResource(R.string.pairing_success),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {}, enabled = false) {
        Text(stringResource(R.string.pairing_opening_home))
    }
}

private fun formatRemainingTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
