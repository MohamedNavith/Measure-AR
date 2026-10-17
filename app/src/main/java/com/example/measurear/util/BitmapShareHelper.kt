package com.example.measurear.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.content.FileProvider
import com.example.measurear.ar.OverlaySegment2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object BitmapShareHelper {

    suspend fun captureArFrameWithOverlay(
        surfaceView: SurfaceView,
        segments: List<OverlaySegment2D>,
        summaryText: String?
    ): Bitmap? = withContext(Dispatchers.Main) {
        if (surfaceView.width <= 0 || surfaceView.height <= 0) return@withContext null

        val rawBitmap = Bitmap.createBitmap(
            surfaceView.width,
            surfaceView.height,
            Bitmap.Config.ARGB_8888
        )

        val copySuccess = suspendCancellableCoroutine<Boolean> { cont ->
            try {
                PixelCopy.request(
                    surfaceView,
                    rawBitmap,
                    { result ->
                        if (cont.isActive) {
                            cont.resume(result == PixelCopy.SUCCESS)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }

        if (!copySuccess) return@withContext null

        withContext(Dispatchers.Default) {
            drawMeasurementOverlayOnBitmap(rawBitmap, segments, summaryText)
            rawBitmap
        }
    }

    private fun drawMeasurementOverlayOnBitmap(
        bitmap: Bitmap,
        segments: List<OverlaySegment2D>,
        summaryText: String?
    ) {
        val canvas = Canvas(bitmap)
        val density = bitmap.width / 400f

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFCC00")
            strokeWidth = 4.5f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFCC00")
            strokeWidth = 3f * density
            style = Paint.Style.STROKE
        }

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 24, 24, 28)
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (seg in segments) {
            canvas.drawLine(seg.startX, seg.startY, seg.endX, seg.endY, linePaint)

            val radius = 7f * density
            canvas.drawCircle(seg.startX, seg.startY, radius, dotFillPaint)
            canvas.drawCircle(seg.startX, seg.startY, radius, dotStrokePaint)
            canvas.drawCircle(seg.endX, seg.endY, radius, dotFillPaint)
            canvas.drawCircle(seg.endX, seg.endY, radius, dotStrokePaint)

            val midX = (seg.startX + seg.endX) * 0.5f
            val midY = (seg.startY + seg.endY) * 0.5f - (18f * density)

            val textWidth = textPaint.measureText(seg.label)
            val padH = 14f * density
            val padV = 9f * density
            val rect = RectF(
                midX - textWidth / 2f - padH,
                midY - textPaint.textSize - padV / 2f,
                midX + textWidth / 2f + padH,
                midY + padV
            )
            canvas.drawRoundRect(rect, 24f * density, 24f * density, pillPaint)
            canvas.drawText(seg.label, midX, midY, textPaint)
        }

        if (!summaryText.isNullOrBlank()) {
            val bannerTextPaint = Paint(textPaint).apply {
                textSize = 18f * density
            }
            val bannerWidth = bannerTextPaint.measureText(summaryText) + 40f * density
            val cx = bitmap.width / 2f
            val cy = bitmap.height - 90f * density
            val bannerRect = RectF(
                cx - bannerWidth / 2f,
                cy - 34f * density,
                cx + bannerWidth / 2f,
                cy + 18f * density
            )
            canvas.drawRoundRect(bannerRect, 28f * density, 28f * density, pillPaint)
            canvas.drawText(summaryText, cx, cy, bannerTextPaint)
        }
    }

    suspend fun saveThumbnailToInternalStorage(context: Context, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                val file = File(dir, "thumb_${System.currentTimeMillis()}.jpg")
                val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
                val targetWidth = 480
                val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                file.absolutePath
            } catch (e: Exception) {
                null
            }
        }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap, title: String) =
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "captures").apply { mkdirs() }
                val file = File(cacheDir, "measure_ar_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share AR Measurement")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
}
