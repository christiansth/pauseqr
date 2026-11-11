package de.pauseqr.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reine Berechnungslogik für das Statistik-Dashboard – keine UI, kein Zugriff auf [Prefs] selbst.
 * Rechnet grundsätzlich in der lokalen Zeitzone des Geräts ([ZoneId.systemDefault]), damit Tages-
 * und Wochenzuordnung der Systemuhr entsprechen. Eine über Mitternacht laufende Pause wird dabei
 * korrekt anteilig auf die betroffenen Kalendertage verteilt (siehe [overlapMillis]).
 *
 * Streak-Hinweis zur Zeitraum-Auswahl: „Aktuelle Erfolgsserie“ ist bewusst unabhängig vom
 * gewählten Zeitraum (Heute/Woche/Monat/Gesamt) – ein Streak ist ein fortlaufender, lebenslanger
 * Zustand und würde innerhalb eines eingeschränkten Zeitfensters keinen sinnvollen Wert ergeben.
 * Nur die drei übrigen Kennzahlen (Gesamtzeit, Anzahl, längste Pause) werden nach Zeitraum gefiltert.
 */
object StatsEngine {

    data class Totals(
        val totalMillis: Long,
        val completedCount: Int,
        val longestMillis: Long
    )

    data class StreakInfo(
        val current: Int,
        val longest: Int,
        val lastSuccessDay: LocalDate?,
        val totalSuccessfulDays: Int
    )

    private fun dayStartMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** [start, endExclusive) in lokaler Zeit für den gewählten Zeitraum. */
    fun rangeBounds(range: TimeRange, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Pair<Long, Long> =
        when (range) {
            TimeRange.TODAY -> dayStartMillis(today, zone) to dayStartMillis(today.plusDays(1), zone)
            TimeRange.WEEK -> {
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                dayStartMillis(monday, zone) to dayStartMillis(monday.plusDays(7), zone)
            }
            TimeRange.MONTH -> {
                val first = today.withDayOfMonth(1)
                dayStartMillis(first, zone) to dayStartMillis(first.plusMonths(1), zone)
            }
            TimeRange.ALL -> Long.MIN_VALUE to Long.MAX_VALUE
        }

    /** Montag bis Sonntag der Woche von [today]. */
    fun weekDays(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): List<LocalDate> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0 until 7).map { monday.plusDays(it.toLong()) }
    }

    /** Alle Kalendertage des Monats von [today]. */
    fun monthDays(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): List<LocalDate> {
        val first = today.withDayOfMonth(1)
        return (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
    }

    /** Überlappungsdauer (ms) einer abgeschlossenen Pause mit [rangeStart, rangeEndExclusive). */
    fun overlapMillis(session: PauseSession, rangeStart: Long, rangeEndExclusive: Long): Long {
        if (!session.completed) return 0L
        val end = session.endTime ?: return 0L
        val clippedStart = maxOf(session.startTime, rangeStart)
        val clippedEnd = minOf(end, rangeEndExclusive)
        return (clippedEnd - clippedStart).coerceAtLeast(0L)
    }

    fun computeTotals(sessions: List<PauseSession>, rangeStart: Long, rangeEndExclusive: Long): Totals {
        var total = 0L
        var count = 0
        var longest = 0L
        for (s in sessions) {
            val overlap = overlapMillis(s, rangeStart, rangeEndExclusive)
            if (overlap <= 0L) continue
            total += overlap
            count++
            // Die „längste Pause“ zeigt die tatsächliche Gesamtdauer, nicht nur den in den
            // Zeitraum fallenden Anteil – sonst wirkte eine Pause über Mitternacht künstlich kurz.
            longest = maxOf(longest, s.durationMillis)
        }
        return Totals(total, count, longest)
    }

    /** Pausenzeit (ms) je Tag aus [days] – Grundlage der Balkendiagramme. */
    fun perDayMillis(sessions: List<PauseSession>, days: List<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        days.map { day ->
            val start = dayStartMillis(day, zone)
            val end = dayStartMillis(day.plusDays(1), zone)
            sessions.sumOf { overlapMillis(it, start, end) }
        }

    fun isStreakQualifying(reason: PauseEndReason?): Boolean =
        reason == PauseEndReason.QR_CODE || reason == PauseEndReason.AUTOMATIC

    /** Kalendertage, an denen mindestens eine Pause regulär (nicht per Notfall) abgeschlossen wurde. */
    fun qualifyingDays(sessions: List<PauseSession>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        sessions.asSequence()
            .filter { it.completed && it.endTime != null && isStreakQualifying(it.endReason) }
            .map { Instant.ofEpochMilli(it.endTime!!).atZone(zone).toLocalDate() }
            .toSet()

    fun computeStreak(sessions: List<PauseSession>, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): StreakInfo {
        val days = qualifyingDays(sessions, zone)
        if (days.isEmpty()) return StreakInfo(0, 0, null, 0)
        return StreakInfo(
            current = currentStreakLength(days, today),
            longest = longestStreakLength(days),
            lastSuccessDay = days.maxOrNull(),
            totalSuccessfulDays = days.size
        )
    }

    /** Mo–So der aktuellen Woche: true, wenn der jeweilige Tag ein erfolgreicher Streak-Tag war. */
    fun weekQualifyingFlags(sessions: List<PauseSession>, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): List<Boolean> {
        val days = qualifyingDays(sessions, zone)
        return weekDays(zone, today).map { it in days }
    }

    /**
     * Eine Serie bleibt am laufenden Tag bestehen, solange der Vortag erfolgreich war – sie wird
     * erst unterbrochen, wenn ein vollständiger Kalendertag ohne Abschluss vergangen ist.
     */
    private fun currentStreakLength(days: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (days.contains(today)) today else today.minusDays(1)
        if (!days.contains(cursor)) return 0
        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun longestStreakLength(days: Set<LocalDate>): Int {
        val sorted = days.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            longest = maxOf(longest, run)
        }
        return longest
    }

    /** Frühestes Datum, an dem irgendeine Serie erstmals die Länge [target] erreicht hat. */
    fun dateStreakFirstReached(days: Set<LocalDate>, target: Int): LocalDate? {
        val sorted = days.sorted()
        if (sorted.isEmpty()) return null
        var runStart = 0
        for (i in sorted.indices) {
            if (i > 0 && sorted[i] != sorted[i - 1].plusDays(1)) runStart = i
            if (i - runStart + 1 == target) return sorted[i]
        }
        return null
    }
}
