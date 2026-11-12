package de.pauseqr.app

import java.time.ZoneId

/**
 * Berechnet alle Erfolge ausschließlich aus der gespeicherten Pausenhistorie – niemals aus
 * einem eigenen, persistierten Freischaltstatus. Das ist bewusst so: nach einer
 * Backup-Wiederherstellung müssen Erfolge aus den wiederhergestellten Pausendaten neu entstehen,
 * nicht aus einem separat gespeicherten (und damit potenziell inkonsistenten) Zustand.
 */
object AchievementEngine {
    private const val OFFLINE_24H_MILLIS = 24L * 60 * 60 * 1000

    fun computeAll(sessions: List<PauseSession>, zone: ZoneId = ZoneId.systemDefault()): List<Achievement> {
        val completed = sessions
            .filter { it.completed && it.endTime != null }
            .sortedBy { it.endTime }

        fun countAchievement(id: AchievementId, threshold: Int): Achievement =
            if (completed.size >= threshold) {
                Achievement(id, true, completed[threshold - 1].endTime)
            } else {
                Achievement(id, false, null)
            }

        val offline24h = run {
            var cumulative = 0L
            var unlockedAt: Long? = null
            for (s in completed) {
                cumulative += s.durationMillis
                if (cumulative >= OFFLINE_24H_MILLIS) {
                    unlockedAt = s.endTime
                    break
                }
            }
            Achievement(AchievementId.OFFLINE_24H, unlockedAt != null, unlockedAt)
        }

        val qualifyingDays = StatsEngine.qualifyingDays(sessions, zone)
        fun streakAchievement(id: AchievementId, target: Int): Achievement {
            val date = StatsEngine.dateStreakFirstReached(qualifyingDays, target)
            val millis = date?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            return Achievement(id, date != null, millis)
        }

        return listOf(
            countAchievement(AchievementId.FIRST_PAUSE, 1),
            countAchievement(AchievementId.FIVE_PAUSES, 5),
            countAchievement(AchievementId.TEN_PAUSES, 10),
            offline24h,
            streakAchievement(AchievementId.STREAK_1, 1),
            streakAchievement(AchievementId.STREAK_3, 3),
            streakAchievement(AchievementId.STREAK_7, 7),
            streakAchievement(AchievementId.STREAK_14, 14),
            streakAchievement(AchievementId.STREAK_30, 30),
            streakAchievement(AchievementId.STREAK_100, 100),
            countAchievement(AchievementId.HUNDRED_PAUSES, 100)
        )
    }
}
