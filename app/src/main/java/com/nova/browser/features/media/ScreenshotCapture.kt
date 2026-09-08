package com.nova.browser.features.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures the visible viewport or the full scrollable page and writes it to
 * the shared Pictures/NOVA collection via MediaStore (no legacy storage APIs).
 */
object ScreenshotCapture {

    private const val ALBUM = "NOVA"
    private const val MAX_FULL_PAGE_HEIGHT = 12_000

    suspend fun capture(context: Context, webView: WebView, fullPage: Boolean): String? {
        val bitmap = withContext(Dispatchers.Main) {
            try {
                if (fullPage) renderFullPage(webView) else renderViewport(webView)
            } catch (e: Throwable) {
                null
            }
        } ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val name = "NOVA_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
                val path = save(context, bitmap, name)
                bitmap.recycle()
                path
            } catch (e: Exception) {
                if (!bitmap.isRecycled) bitmap.recycle()
                null
            }
        }
    }

    private fun renderViewport(webView: WebView): Bitmap? {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        webView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun renderFullPage(webView: WebView): Bitmap? {
        val width = webView.width
        if (width <= 0) return null
        val scale = webView.scale
        val contentHeight = (webView.contentHeight * scale).toInt()
        val height = contentHeight.coerceIn(webView.height, MAX_FULL_PAGE_HEIGHT)
        if (height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val originalScrollY = webView.scrollY
        var offset = 0
        while (offset < height) {
            webView.scrollTo(0, offset)
            canvas.save()
            canvas.translate(0f, offset.toFloat())
            webView.draw(canvas)
            canvas.restore()
            offset += webView.height.coerceAtLeast(1)
        }
        webView.scrollTo(0, originalScrollY)
        return bitmap
    }

    private fun save(context: Context, bitmap: Bitmap, name: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, name)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file.absolutePath
    }
}
