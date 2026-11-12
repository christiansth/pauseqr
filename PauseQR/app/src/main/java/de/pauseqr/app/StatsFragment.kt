package de.pauseqr.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private lateinit var tvAttemptsEmpty: TextView
    private lateinit var attemptsContentGroup: View
    private lateinit var tvAttemptsTotal: TextView
    private lateinit var tvAttemptsToday: TextView
    private lateinit var tvAttemptsWeek: TextView
    private lateinit var tvAttemptsMostFrequent: TextView
    private lateinit var attemptsChartCard: View
    private lateinit var tvAttemptsChartTitle: TextView
    private lateinit var barChartAttemptsView: BarChartView
    private lateinit var attemptsPerAppContainer: android.widget.LinearLayout

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

        tvAttemptsEmpty = view.findViewById(R.id.tvAttemptsEmpty)
        attemptsContentGroup = view.findViewById(R.id.attemptsContentGroup)
        tvAttemptsTotal = view.findViewById(R.id.tvAttemptsTotal)
        tvAttemptsToday = view.findViewById(R.id.tvAttemptsToday)
        tvAttemptsWeek = view.findViewById(R.id.tvAttemptsWeek)
        tvAttemptsMostFrequent = view.findViewById(R.id.tvAttemptsMostFrequent)
        attemptsChartCard = view.findViewById(R.id.attemptsChartCard)
        tvAttemptsChartTitle = view.findViewById(R.id.tvAttemptsChartTitle)
        barChartAttemptsView = view.findViewById(R.id.barChartAttemptsView)
        attemptsPerAppContainer = view.findViewById(R.id.attemptsPerAppContainer)

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
        val allAttempts = BlockedAttemptStore.getAttempts(context)

        tvEmpty.visibility = if (hasAnyCompleted) View.GONE else View.VISIBLE
        contentGroup.visibility = if (hasAnyCompleted) View.VISIBLE else View.GONE

        if (hasAnyCompleted) {
            refreshStatCards(allSessions)
            refreshChart(allSessions)
            refreshHistory(allSessions, allAttempts)
        }
        refreshAchievements(allSessions)
        // Unabhängig vom Leerzustand der Pausenhistorie: Aufrufversuche können auch während einer
        // noch laufenden Pause bereits erfasst worden sein.
        refreshAttempts(context, allAttempts)
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

    private fun refreshAttempts(context: android.content.Context, attempts: List<BlockedAppAttempt>) {
        tvAttemptsEmpty.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE
        attemptsContentGroup.visibility = if (attempts.isEmpty()) View.GONE else View.VISIBLE
        if (attempts.isEmpty()) return

        val (todayStart, todayEnd) = StatsEngine.rangeBounds(TimeRange.TODAY, zone)
        val (weekStart, weekEnd) = StatsEngine.rangeBounds(TimeRange.WEEK, zone)
        val todayCount = BlockedAttemptEngine.countInRange(attempts, todayStart, todayEnd)
        val weekCount = BlockedAttemptEngine.countInRange(attempts, weekStart, weekEnd)
        val mostFrequentPkg = BlockedAttemptEngine.mostFrequentApp(attempts)

        tvAttemptsTotal.text = resources.getQuantityString(R.plurals.attempts_count, attempts.size, attempts.size)
        tvAttemptsToday.text = resources.getQuantityString(R.plurals.attempts_count, todayCount, todayCount)
        tvAttemptsWeek.text = resources.getQuantityString(R.plurals.attempts_count, weekCount, weekCount)
        tvAttemptsMostFrequent.text = mostFrequentPkg?.let { appLabel(context, it) } ?: getString(R.string.stats_value_none)

        when (currentRange) {
            TimeRange.WEEK -> {
                attemptsChartCard.visibility = View.VISIBLE
                tvAttemptsChartTitle.text = getString(R.string.stats_attempts_chart_title_week)
                val days = StatsEngine.weekDays(zone)
                val values = BlockedAttemptEngine.countPerDay(attempts, days, zone)
                val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale(context)) }
                barChartAttemptsView.setData(values, labels, desiredWidthPx = null)
            }
            TimeRange.MONTH -> {
                attemptsChartCard.visibility = View.VISIBLE
                tvAttemptsChartTitle.text = getString(R.string.stats_attempts_chart_title_month)
                val days = StatsEngine.monthDays(zone)
                val values = BlockedAttemptEngine.countPerDay(attempts, days, zone)
                val labels = days.map { it.dayOfMonth.toString() }
                val density = resources.displayMetrics.density
                val desiredWidth = (days.size * 32 * density).toInt()
                barChartAttemptsView.setData(values, labels, desiredWidthPx = desiredWidth)
            }
            else -> attemptsChartCard.visibility = View.GONE
        }

        attemptsPerAppContainer.removeAllViews()
        BlockedAttemptEngine.topApps(attempts).forEach { app ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_blocked_app, attemptsPerAppContainer, false)
            val ivIcon = row.findViewById<ImageView>(R.id.ivBlockedAppIcon)
            val tvName = row.findViewById<TextView>(R.id.tvBlockedAppName)
            val tvUninstalled = row.findViewById<TextView>(R.id.tvBlockedAppUninstalled)
            val tvCount = row.findViewById<TextView>(R.id.tvBlockedAppCount)
            val tvShare = row.findViewById<TextView>(R.id.tvBlockedAppShare)

            val icon = appIcon(context, app.packageName)
            if (icon != null) {
                ivIcon.setImageDrawable(icon)
                tvUninstalled.visibility = View.GONE
            } else {
                ivIcon.setImageResource(R.drawable.ic_nav_apps)
                tvUninstalled.visibility = View.VISIBLE
            }
            tvName.text = appLabel(context, app.packageName)
            tvCount.text = resources.getQuantityString(R.plurals.attempts_count, app.count, app.count)
            tvShare.text = getString(R.string.stats_attempts_share_format, Math.round(app.share * 100))

            attemptsPerAppContainer.addView(row)
        }
    }

    private fun appLabel(context: android.content.Context, packageName: String): String = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun appIcon(context: android.content.Context, packageName: String): android.graphics.drawable.Drawable? = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadIcon(context.packageManager)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun refreshHistory(sessions: List<PauseSession>, allAttempts: List<BlockedAppAttempt>) {
        val context = context ?: return
        historyContainer.removeAllViews()

        val (start, end) = StatsEngine.rangeBounds(currentRange, zone)
        val openSession = sessions.lastOrNull { !it.completed }
        val completedInRange = sessions
            .filter { it.completed && StatsEngine.overlapMillis(it, start, end) > 0 }
            .sortedByDescending { it.startTime }

        tvHistoryEmpty.visibility = if (completedInRange.isEmpty() && openSession == null) View.VISIBLE else View.GONE

        openSession?.let { addHistoryRow(context, it, isActive = true, allAttempts) }
        completedInRange.forEach { addHistoryRow(context, it, isActive = false, allAttempts) }
    }

    private fun addHistoryRow(
        context: android.content.Context,
        session: PauseSession,
        isActive: Boolean,
        allAttempts: List<BlockedAppAttempt>
    ) {
        val row = LayoutInflater.from(context).inflate(R.layout.item_pause_history, historyContainer, false)
        val tvDate = row.findViewById<TextView>(R.id.tvHistoryDate)
        val tvTimeRange = row.findViewById<TextView>(R.id.tvHistoryTimeRange)
        val tvDuration = row.findViewById<TextView>(R.id.tvHistoryDuration)
        val tvEndReason = row.findViewById<TextView>(R.id.tvHistoryEndReason)
        val tvBlockedAttempts = row.findViewById<TextView>(R.id.tvHistoryBlockedAttempts)

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

            val sessionAttempts = allAttempts.filter { it.pauseSessionId == session.id }
            if (sessionAttempts.isNotEmpty()) {
                tvBlockedAttempts.visibility = View.VISIBLE
                tvBlockedAttempts.text = getString(R.string.stats_history_blocked_attempts_label, sessionAttempts.size)
                row.setOnClickListener { showAttemptsBreakdown(context, sessionAttempts) }
            }
        }
        historyContainer.addView(row)
    }

    private fun showAttemptsBreakdown(context: android.content.Context, attempts: List<BlockedAppAttempt>) {
        val topApps = BlockedAttemptEngine.topApps(attempts)
        val message = topApps.joinToString("\n") { app ->
            "${appLabel(context, app.packageName)} – " +
                resources.getQuantityString(R.plurals.attempts_count, app.count, app.count)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.stats_attempts_section_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
