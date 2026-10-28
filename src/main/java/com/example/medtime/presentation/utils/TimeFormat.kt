package com.example.medtime.presentation.utils

import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val doseTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatDoseTime(value: Instant): String =
    doseTimeFormatter.withZone(ZoneId.systemDefault()).format(value)

fun formatDoseCountdown(value: Instant, now: Instant = Instant.now()): String {
    val seconds = Duration.between(now, value).seconds
    if (seconds <= 0) return "Pendiente"
    val minutes = seconds / 60
    return when {
        minutes < 60 -> "En ${minutes.coerceAtLeast(1)} min"
        minutes < 1440 -> "En ${minutes / 60} h ${minutes % 60} min"
        else -> "En ${minutes / 1440} dias"
    }
}
