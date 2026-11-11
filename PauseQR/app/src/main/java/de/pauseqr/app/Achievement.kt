package de.pauseqr.app

/** Die sieben festen ScanOff-Erfolge, in der Reihenfolge, in der sie angezeigt werden. */
enum class AchievementId {
    FIRST_PAUSE,
    FIVE_PAUSES,
    TEN_PAUSES,
    OFFLINE_24H,
    STREAK_7,
    STREAK_30,
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
