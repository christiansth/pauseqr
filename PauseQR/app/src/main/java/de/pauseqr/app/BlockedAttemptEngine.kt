package de.pauseqr.app

import java.time.LocalDate
import java.time.ZoneId

/**
 * Reine Berechnungslogik für die Statistik der blockierten App-Aufrufe – keine UI, kein direkter
 * Zugriff auf [BlockedAttemptStore]. Rechnet in der lokalen Zeitzone des Geräts, konsistent mit
 * [StatsEngine].
 */
object BlockedAttemptEngine {

    data class AppAttemptCount(val packageName: String, val count: Int, val share: Float)

    private fun dayStartMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Anzahl der Versuche mit Zeitstempel in [rangeStart, rangeEndExclusive). */
    fun countInRange(attempts: List<BlockedAppAttempt>, rangeStart: Long, rangeEndExclusive: Long): Int =
        attempts.count { it.timestamp >= rangeStart && it.timestamp < rangeEndExclusive }

    /** Anzahl Versuche je Tag aus [days] – Grundlage des Balkendiagramms. */
    fun countPerDay(attempts: List<BlockedAppAttempt>, days: List<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        days.map { day ->
            val start = dayStartMillis(day, zone)
            val end = dayStartMillis(day.plusDays(1), zone)
            countInRange(attempts, start, end).toLong()
        }

    /** Pro App absteigend nach Häufigkeit, mit Anteil an allen übergebenen Versuchen (0..1). */
    fun topApps(attempts: List<BlockedAppAttempt>): List<AppAttemptCount> {
        val total = attempts.size
        if (total == 0) return emptyList()
        return attempts.groupingBy { it.packageName }.eachCount()
            .map { (pkg, count) -> AppAttemptCount(pkg, count, count.toFloat() / total.toFloat()) }
            .sortedByDescending { it.count }
    }

    fun mostFrequentApp(attempts: List<BlockedAppAttempt>): String? =
        attempts.groupingBy { it.packageName }.eachCount().maxByOrNull { it.value }?.key
}
