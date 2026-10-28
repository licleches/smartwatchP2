package com.example.medtime.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.medtime.presentation.data.alarm.DoseAlarmScheduler
import com.example.medtime.presentation.data.repository.DoseRepository
import com.example.medtime.presentation.data.repository.MedicationRepository
import com.example.medtime.presentation.data.repository.PairingRepository
import com.example.medtime.presentation.ui.screens.HomeScreen
import com.example.medtime.presentation.ui.screens.PairingScreen
import com.example.medtime.presentation.ui.theme.MedtimeTheme
import com.example.medtime.presentation.viewmodel.HomeViewModel
import com.example.medtime.presentation.viewmodel.PairingUiState
import com.example.medtime.presentation.viewmodel.PairingViewModel

private const val PAIRING_ROUTE = "pairing"
private const val HOME_ROUTE = "home"

@Composable
fun MedtimeApp(
    pairingRepository: PairingRepository,
    medicationRepository: MedicationRepository,
    doseRepository: DoseRepository,
    alarmScheduler: DoseAlarmScheduler,
    exactAlarmsAllowed: () -> Boolean,
    notificationsAllowed: () -> Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    MedtimeTheme {
        val navController = rememberNavController()
        val pairingViewModel: PairingViewModel = viewModel(
            factory = remember(pairingRepository) {
                PairingViewModel.Factory(pairingRepository)
            }
        )
        val pairingState by pairingViewModel.state.collectAsStateWithLifecycle()
        val currentRoute = navController.currentBackStackEntryAsState().value
            ?.destination
            ?.route

        LaunchedEffect(pairingState, currentRoute) {
            when (pairingState) {
                PairingUiState.Ready -> if (currentRoute == PAIRING_ROUTE) {
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(PAIRING_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                else -> if (currentRoute == HOME_ROUTE) {
                    navController.navigate(PAIRING_ROUTE) {
                        popUpTo(HOME_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = PAIRING_ROUTE
        ) {
            composable(PAIRING_ROUTE) {
                PairingScreen(
                    state = pairingState,
                    onStartPairing = pairingViewModel::startPairing
                )
            }
            composable(HOME_ROUTE) {
                if (pairingState == PairingUiState.Ready) {
                    HomeRoute(
                        medicationRepository = medicationRepository,
                        doseRepository = doseRepository,
                        alarmScheduler = alarmScheduler,
                        exactAlarmsAllowed = exactAlarmsAllowed(),
                        notificationsAllowed = notificationsAllowed(),
                        onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onCredentialInvalid = pairingViewModel::clearAndStartPairing
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRoute(
    medicationRepository: MedicationRepository,
    doseRepository: DoseRepository,
    alarmScheduler: DoseAlarmScheduler,
    exactAlarmsAllowed: Boolean,
    notificationsAllowed: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCredentialInvalid: () -> Unit
) {
    val homeViewModel: HomeViewModel = viewModel(
        factory = remember(medicationRepository, doseRepository) {
            HomeViewModel.Factory(medicationRepository, doseRepository, alarmScheduler)
        }
    )
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val actionInProgress by homeViewModel.actionInProgress.collectAsStateWithLifecycle()

    HomeScreen(
        state = homeState,
        actionInProgress = actionInProgress,
        onRefresh = homeViewModel::refresh,
        onDoseAction = homeViewModel::applyDoseAction,
        exactAlarmsAllowed = exactAlarmsAllowed,
        notificationsAllowed = notificationsAllowed,
        onRequestExactAlarmPermission = onRequestExactAlarmPermission,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onCredentialInvalid = onCredentialInvalid,
        onTestAlarm = homeViewModel::scheduleTestAlarm
    )
}
