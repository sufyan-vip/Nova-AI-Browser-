package com.nova.browser.core.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun time(millis: Long): String = timeFormat.format(Date(millis))
    fun date(millis: Long): String = dateFormat.format(Date(millis))
    fun full(millis: Long): String = fullFormat.format(Date(millis))
    fun fileStamp(millis: Long = System.currentTimeMillis()): String = fileFormat.format(Date(millis))

    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        if (diff < 0) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 30 -> "${days / 7}w ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }

    /** Groups history entries: Today / Yesterday / weekday / date. */
    fun dayLabel(millis: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            now - millis < TimeUnit.DAYS.toMillis(7) ->
                SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))
            else -> date(millis)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun greeting(now: Long = System.currentTimeMillis()): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Working late"
        }
    }

    fun durationText(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
