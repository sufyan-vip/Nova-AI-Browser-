package com.nova.browser.core.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.util.Locale

/* ---------------- Context ---------------- */

fun Context.copyToClipboard(text: String, label: String = "NOVA") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    try {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (e: Exception) {
        // Clipboard unavailable (e.g. device policy) — silently ignore.
    }
}

fun Context.readClipboardText(): String? = try {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
} catch (e: Exception) {
    null
}

fun Context.clearClipboard() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    try {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    } catch (e: Exception) {
        // ignore
    }
}

fun Context.toast(message: String, long: Boolean = false) {
    try {
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Toast can fail on some backgrounded contexts.
    }
}

fun Context.shareText(text: String, subject: String? = null): Boolean = try {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

fun Context.openExternally(url: String): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: Exception) {
    false
}

/* ---------------- String ---------------- */

fun String.ellipsize(max: Int): String =
    if (length <= max) this else take(max - 1).trimEnd() + "…"

fun String.titleCaseWords(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun String?.orDash(): String = if (this.isNullOrBlank()) "—" else this

fun String.toColorOrNull(): Color? = try {
    Color(android.graphics.Color.parseColor(if (startsWith("#")) this else "#$this"))
} catch (e: Exception) {
    null
}

/* ---------------- Collections ---------------- */

fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    if (index !in indices) this else toMutableList().also { it[index] = item }

fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val mutable = toMutableList()
    val element = mutable.removeAt(from)
    mutable.add(to, element)
    return mutable
}

/* ---------------- Flow ---------------- */

/** Emits [fallback] and swallows the exception when the upstream flow fails. */
fun <T> Flow<T>.catchTo(fallback: T): Flow<T> = catch { emit(fallback) }

/* ---------------- Numbers ---------------- */

fun Int.compactCount(): String = when {
    this < 1_000 -> toString()
    this < 1_000_000 -> String.format(Locale.US, "%.1fK", this / 1000f)
    else -> String.format(Locale.US, "%.1fM", this / 1_000_000f)
}

fun Long.percentOf(total: Long): Float =
    if (total <= 0) 0f else (this.toFloat() / total.toFloat()).coerceIn(0f, 1f)
