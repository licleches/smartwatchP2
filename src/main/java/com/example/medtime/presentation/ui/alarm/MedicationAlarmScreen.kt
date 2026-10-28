package com.example.medtime.presentation.ui.alarm

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton

@Composable
fun MedicationAlarmScreen(
    medicationName: String,
    doseDescription: String,
    timeLabel: String,
    onConfirm: () -> Unit,
    onPostpone: (Int) -> Unit,
    onSilence: () -> Unit
) {
    var showPostponeOptions by remember { mutableStateOf(false) }
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
                text = "MED",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Hora de tomar",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = medicationName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            if (doseDescription.isNotBlank()) {
                Text(
                    text = doseDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = timeLabel,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Ya lo tome")
            }
            if (showPostponeOptions) {
                listOf(15, 30, 60, 120).forEach { minutes ->
                    Button(
                        onClick = { onPostpone(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Posponer $minutes min")
                    }
                }
                TextButton(onClick = { showPostponeOptions = false }) {
                    Text("Cancelar")
                }
            } else {
                Button(
                    onClick = { showPostponeOptions = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Posponer")
                }
            }
            TextButton(onClick = onSilence) {
                Text("Silenciar")
            }
        }
    }
}
