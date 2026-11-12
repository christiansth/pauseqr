package de.pauseqr.app

/** Die festen ScanOff-Erfolge, in der Reihenfolge, in der sie angezeigt werden. */
enum class AchievementId {
    FIRST_PAUSE,
    FIVE_PAUSES,
    TEN_PAUSES,
    OFFLINE_24H,

    // Offline-Streak-Meilensteine (siehe „Feature: Offline-Streaks“) – einmal erreicht, bleiben sie
    // freigeschaltet, auch wenn eine laufende Serie später unterbrochen wird (siehe AchievementEngine).
    STREAK_1,
    STREAK_3,
    STREAK_7,
    STREAK_14,
    STREAK_30,
    STREAK_100,

    HUNDRED_PAUSES
}

/**
 * Ein berechneter Erfolg. Erfolge werden nie eigenständig gespeichert – sie ergeben sich immer
 * neu aus der Pausenhistorie (siehe [AchievementEngine]), auch nach einer Backup-Wiederherstellung.
 */
data class Achievement(
    val id: AchievementId,
    val unlocked: Boolean,
    val unlockedAtMillis: Long?
)
