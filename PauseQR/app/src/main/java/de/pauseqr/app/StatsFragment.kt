package de.pauseqr.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.DateFormat
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Statistik-Dashboard: Kennzahlen, Balkendiagramm, Pausenverlauf und Erfolge – ausschließlich aus
 * der lokal gespeicherten Pausenhistorie berechnet ([PauseHistoryStore], [StatsEngine],
 * [AchievementEngine]). Keine erfundenen Werte: ohne abgeschlossene Pausen wird der Leerzustand
 * gezeigt, nicht ein Platzhalter-Diagramm mit Fantasiewerten.
 */
class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private var currentRange: TimeRange = TimeRange.WEEK

    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var tvEmpty: TextView
    private lateinit var contentGroup: View
    private lateinit var tvTotalTime: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvLongestPause: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvStreakCurrentDetail: TextView
    private lateinit var tvStreakLongest: TextView
    private lateinit var tvStreakTotalDays: TextView
    private lateinit var tvStreakLastSuccess: TextView
    private lateinit var chartCard: View
    private lateinit var tvChartTitle: TextView
    private lateinit var barChartView: BarChartView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var historyContainer: android.widget.LinearLayout
    private lateinit var achievementsContainer: android.widget.LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleGroup = view.findViewById(R.id.toggleGroupRange)
        tvEmpty = view.findViewById(R.id.tvStatsEmpty)
        contentGroup = view.findViewById(R.id.statsContentGroup)
        tvTotalTime = view.findViewById(R.id.tvTotalTime)
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount)
        tvLongestPause = view.findViewById(R.id.tvLongestPause)
        tvStreak = view.findViewById(R.id.tvStreak)
        tvStreakCurrentDetail = view.findViewById(R.id.tvStreakCurrentDetail)
        tvStreakLongest = view.findViewById(R.id.tvStreakLongest)
        tvStreakTotalDays = view.findViewById(R.id.tvStreakTotalDays)
        tvStreakLastSuccess = view.findViewById(R.id.tvStreakLastSuccess)
        chartCard = view.findViewById(R.id.chartCard)
        tvChartTitle = view.findViewById(R.id.tvChartTitle)
        barChartView = view.findViewById(R.id.barChartView)
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty)
        historyContainer = view.findViewById(R.id.historyContainer)
        achievementsContainer = view.findViewById(R.id.achievementsContainer)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentRange = rangeForButtonId(checkedId)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun rangeForButtonId(id: Int): TimeRange = when (id) {
        R.id.btnRangeToday -> TimeRange.TODAY
        R.id.btnRangeMonth -> TimeRange.MONTH
        R.id.btnRangeAll -> TimeRange.ALL
        else -> TimeRange.WEEK
    }

    private fun refresh() {
        val context = context ?: return
        val allSessions = PauseHistoryStore.getSessions(context)
        val hasAnyCompleted = allSessions.any { it.completed }

        tvEmpty.visibility = if (hasAnyCompleted) View.GONE else View.VISIBLE
        contentGroup.visibility = if (hasAnyCompleted) View.VISIBLE else View.GONE

        if (hasAnyCompleted) {
            refreshStatCards(allSessions)
            refreshChart(allSessions)
            refreshHistory(allSessions)
        }
        refreshAchievements(allSessions)
    }

    private fun locale(context: android.content.Context): Locale = Locale(LanguageUtil.currentLanguage(context))

    private fun refreshStatCards(sessions: List<PauseSession>) {
        val (start, end) = StatsEngine.rangeBounds(currentRange, zone)
        val totals = StatsEngine.computeTotals(sessions, start, end)
        // Die Serie ist bewusst unabhängig vom gewählten Zeitraum (siehe StatsEngine-Dokumentation).
        val streak = StatsEngine.computeStreak(sessions, zone)

        tvTotalTime.text = formatDuration(totals.totalMillis)
        tvCompletedCount.text = totals.completedCount.toString()
        tvLongestPause.text = formatDuration(totals.longestMillis)
        tvStreak.text = resources.getQuantityString(R.plurals.streak_days_count, streak.current, streak.current)

        tvStreakCurrentDetail.text = resources.getQuantityString(R.plurals.streak_days_count, streak.current, streak.current)
        tvStreakLongest.text = resources.getQuantityString(R.plurals.streak_days_count, streak.longest, streak.longest)
        tvStreakTotalDays.text = resources.getQuantityString(
            R.plurals.streak_days_count, streak.totalSuccessfulDays, streak.totalSuccessfulDays
        )
        tvStreakLastSuccess.text = streak.lastSuccessDay?.let { day ->
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale(requireContext())).format(
                Date(day.atStartOfDay(zone).toInstant().toEpochMilli())
            )
        } ?: getString(R.string.stats_value_none)
    }

    private fun refreshChart(sessions: List<PauseSession>) {
        val context = context ?: return
        when (currentRange) {
            TimeRange.WEEK -> {
                chartCard.visibility = View.VISIBLE
                tvChartTitle.text = getString(R.string.stats_chart_title_week)
                val days = StatsEngine.weekDays(zone)
                val values = StatsEngine.perDayMillis(sessions, days, zone)
                val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale(context)) }
                barChartView.setData(values, labels, desiredWidthPx = null)
            }
            TimeRange.MONTH -> {
                chartCard.visibility = View.VISIBLE
                tvChartTitle.text = getString(R.string.stats_chart_title_month)
                val days = StatsEngine.monthDays(zone)
                val values = StatsEngine.perDayMillis(sessions, days, zone)
                val labels = days.map { it.dayOfMonth.toString() }
                val density = resources.displayMetrics.density
                val desiredWidth = (days.size * 32 * density).toInt()
                barChartView.setData(values, labels, desiredWidthPx = desiredWidth)
            }
            else -> chartCard.visibility = View.GONE
        }
    }

    private fun refreshHistory(sessions: List<PauseSession>) {
        val context = context ?: return
        historyContainer.removeAllViews()

        val (start, end) = StatsEngine.rangeBounds(currentRange, zone)
        val openSession = sessions.lastOrNull { !it.completed }
        val completedInRange = sessions
            .filter { it.completed && StatsEngine.overlapMillis(it, start, end) > 0 }
            .sortedByDescending { it.startTime }

        tvHistoryEmpty.visibility = if (completedInRange.isEmpty() && openSession == null) View.VISIBLE else View.GONE

        openSession?.let { addHistoryRow(context, it, isActive = true) }
        completedInRange.forEach { addHistoryRow(context, it, isActive = false) }
    }

    private fun addHistoryRow(context: android.content.Context, session: PauseSession, isActive: Boolean) {
        val row = LayoutInflater.from(context).inflate(R.layout.item_pause_history, historyContainer, false)
        val tvDate = row.findViewById<TextView>(R.id.tvHistoryDate)
        val tvTimeRange = row.findViewById<TextView>(R.id.tvHistoryTimeRange)
        val tvDuration = row.findViewById<TextView>(R.id.tvHistoryDuration)
        val tvEndReason = row.findViewById<TextView>(R.id.tvHistoryEndReason)

        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale(context))
        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale(context))

        tvDate.text = dateFormat.format(Date(session.startTime))
        if (isActive) {
            tvTimeRange.text = timeFormat.format(Date(session.startTime))
            tvDuration.text = ""
            tvEndReason.text = getString(R.string.stats_history_active_label)
        } else {
            val endTime = session.endTime ?: session.startTime
            tvTimeRange.text = getString(
                R.string.stats_history_time_range,
                timeFormat.format(Date(session.startTime)),
                timeFormat.format(Date(endTime))
            )
            tvDuration.text = getString(R.string.stats_history_duration_label, formatDuration(session.durationMillis))
            tvEndReason.text = endReasonLabel(session.endReason)
        }
        historyContainer.addView(row)
    }

    private fun endReasonLabel(reason: PauseEndReason?): String = when (reason) {
        PauseEndReason.QR_CODE -> getString(R.string.stats_history_ended_qr)
        PauseEndReason.EMERGENCY -> getString(R.string.stats_history_ended_emergency)
        PauseEndReason.AUTOMATIC -> getString(R.string.stats_history_ended_automatic)
        null -> ""
    }

    private fun refreshAchievements(sessions: List<PauseSession>) {
        val context = context ?: return
        achievementsContainer.removeAllViews()
        val achievements = AchievementEngine.computeAll(sessions, zone)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale(context))

        achievements.forEach { achievement ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_achievement, achievementsContainer, false)
            val ivIcon = row.findViewById<ImageView>(R.id.ivAchievementIcon)
            val ivCheck = row.findViewById<ImageView>(R.id.ivAchievementCheck)
            val tvTitle = row.findViewById<TextView>(R.id.tvAchievementTitle)
            val tvDesc = row.findViewById<TextView>(R.id.tvAchievementDesc)
            val tvStatus = row.findViewById<TextView>(R.id.tvAchievementStatus)

            tvTitle.text = getString(achievementTitleRes(achievement.id))
            tvDesc.text = getString(achievementDescRes(achievement.id))

            if (achievement.unlocked) {
                ivIcon.setColorFilter(resources.getColor(R.color.text_primary, null))
                ivCheck.visibility = View.VISIBLE
                tvStatus.text = achievement.unlockedAtMillis?.let {
                    getString(R.string.stats_achievement_unlocked_on, dateFormat.format(Date(it)))
                } ?: ""
                tvStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
            } else {
                ivIcon.setColorFilter(resources.getColor(R.color.text_secondary, null))
                ivCheck.visibility = View.GONE
                tvStatus.text = getString(R.string.stats_achievement_locked)
                tvStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
            }
            achievementsContainer.addView(row)
        }
    }

    private fun achievementTitleRes(id: AchievementId): Int = when (id) {
        AchievementId.FIRST_PAUSE -> R.string.achievement_first_pause_title
        AchievementId.FIVE_PAUSES -> R.string.achievement_five_pauses_title
        AchievementId.TEN_PAUSES -> R.string.achievement_ten_pauses_title
        AchievementId.OFFLINE_24H -> R.string.achievement_offline_24h_title
        AchievementId.STREAK_1 -> R.string.achievement_streak_1_title
        AchievementId.STREAK_3 -> R.string.achievement_streak_3_title
        AchievementId.STREAK_7 -> R.string.achievement_streak_7_title
        AchievementId.STREAK_14 -> R.string.achievement_streak_14_title
        AchievementId.STREAK_30 -> R.string.achievement_streak_30_title
        AchievementId.STREAK_100 -> R.string.achievement_streak_100_title
        AchievementId.HUNDRED_PAUSES -> R.string.achievement_hundred_pauses_title
    }

    private fun achievementDescRes(id: AchievementId): Int = when (id) {
        AchievementId.FIRST_PAUSE -> R.string.achievement_first_pause_desc
        AchievementId.FIVE_PAUSES -> R.string.achievement_five_pauses_desc
        AchievementId.TEN_PAUSES -> R.string.achievement_ten_pauses_desc
        AchievementId.OFFLINE_24H -> R.string.achievement_offline_24h_desc
        AchievementId.STREAK_1 -> R.string.achievement_streak_1_desc
        AchievementId.STREAK_3 -> R.string.achievement_streak_3_desc
        AchievementId.STREAK_7 -> R.string.achievement_streak_7_desc
        AchievementId.STREAK_14 -> R.string.achievement_streak_14_desc
        AchievementId.STREAK_30 -> R.string.achievement_streak_30_desc
        AchievementId.STREAK_100 -> R.string.achievement_streak_100_desc
        AchievementId.HUNDRED_PAUSES -> R.string.achievement_hundred_pauses_desc
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.stats_duration_hours_minutes, hours.toInt(), minutes.toInt())
        } else {
            getString(R.string.stats_duration_minutes_only, minutes.toInt())
        }
    }
}
