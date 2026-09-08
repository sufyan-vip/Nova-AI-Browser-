package com.nova.browser.core.utils

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object FileUtils {

    enum class Category(val label: String) {
        DOCUMENT("Document"), IMAGE("Image"), VIDEO("Video"),
        AUDIO("Audio"), ARCHIVE("Archive"), APP("App"), OTHER("Other");

        companion object {
            fun from(label: String?): Category =
                entries.firstOrNull { it.label.equals(label, true) } ?: OTHER
        }
    }

    fun extension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.US)

    fun mimeType(fileName: String): String {
        val ext = extension(fileName)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun categoryOf(fileName: String, mimeType: String? = null): Category {
        val mime = mimeType ?: mimeType(fileName)
        val ext = extension(fileName)
        return when {
            mime.startsWith("image/") -> Category.IMAGE
            mime.startsWith("video/") -> Category.VIDEO
            mime.startsWith("audio/") -> Category.AUDIO
            ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> Category.ARCHIVE
            ext in setOf("apk", "aab") -> Category.APP
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "epub", "rtf") -> Category.DOCUMENT
            else -> Category.OTHER
        }
    }

    /** Public Downloads dir with app subfolder; falls back to app-private storage. */
    fun downloadDir(context: Context, category: Category? = null): File {
        val base = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.takeIf { it.exists() || it.mkdirs() }
                ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
        } catch (e: Exception) {
            File(context.filesDir, "downloads")
        }
        val dir = if (category == null) File(base, "NOVA") else File(base, "NOVA/${category.label}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Returns a non-clashing file inside [dir] by appending (1), (2)… */
    fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, sanitize(fileName))
        if (!candidate.exists()) return candidate
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension
        var index = 1
        while (candidate.exists() && index < 1000) {
            val name = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(dir, name)
            index++
        }
        return candidate
    }

    fun sanitize(fileName: String): String =
        fileName.replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_").take(180).ifBlank { "download" }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${value.toInt()} ${units[unit]}"
        else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun formatSpeed(bytesPerSecond: Long): String = "${formatSize(bytesPerSecond)}/s"

    fun availableBytes(file: File): Long = try {
        file.usableSpace
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }

    fun md5(file: File): String? = try {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }

    fun deleteQuietly(path: String?): Boolean = try {
        if (path.isNullOrBlank()) false else File(path).let { if (it.exists()) it.delete() else false }
    } catch (e: Exception) {
        false
    }

    fun writeText(file: File, text: String): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(text)
        true
    } catch (e: Exception) {
        false
    }

    fun readTextOrNull(file: File): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        null
    }
}
