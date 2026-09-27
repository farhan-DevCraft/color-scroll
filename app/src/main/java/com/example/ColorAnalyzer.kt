package com.example

import android.graphics.ImageFormat
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.AppSettings
import com.example.model.ColorPreset
import java.nio.ByteBuffer

enum class ScrollAction {
    SCROLL_UP,
    SCROLL_DOWN
}

data class ColorAnalysisResult(
    val upPercentage: Float = 0f,
    val downPercentage: Float = 0f,
    val thresholdPercent: Float = 5f,
    val upColorPreset: ColorPreset = ColorPreset.YELLOW,
    val downColorPreset: ColorPreset = ColorPreset.GREEN,
    val triggeredAction: ScrollAction? = null,
    val isCooldownActive: Boolean = false,
    val remainingCooldownMs: Long = 0L,
    val timestamp: Long = 0L
)

class ColorAnalyzer(
    private val settingsProvider: () -> AppSettings,
    private val onResult: (ColorAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastTriggerTimestamp: Long = 0L
    private var lastProcessedTimestamp: Long = 0L

    // Throttle frame processing to ~15-20 FPS for peak efficiency without CPU drain
    private val minFrameIntervalMs: Long = 50L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastProcessedTimestamp < minFrameIntervalMs) {
                return
            }
            lastProcessedTimestamp = now

            val settings = settingsProvider()
            val upPreset = settings.upColorPreset
            val downPreset = settings.downColorPreset
            val threshold = settings.thresholdPercent
            val cooldownMs = (settings.cooldownSeconds * 1000f).toLong().coerceAtLeast(500L)

            val isCooldownActive = (now - lastTriggerTimestamp) < cooldownMs
            val remainingCooldownMs = if (isCooldownActive) (cooldownMs - (now - lastTriggerTimestamp)) else 0L

            if (imageProxy.format != ImageFormat.YUV_420_888) {
                return
            }

            val planes = imageProxy.planes
            if (planes.size < 3) {
                return
            }

            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]

            val yBuffer: ByteBuffer = yPlane.buffer
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer

            val width = imageProxy.width
            val height = imageProxy.height

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            // Sample every 4th pixel horizontally and vertically
            val step = 4
            var sampleCount = 0
            var upMatchCount = 0
            var downMatchCount = 0

            var y = 0
            while (y < height) {
                val uvRow = y / 2
                val uRowOffset = uvRow * uRowStride
                val vRowOffset = uvRow * vRowStride
                val yRowOffset = y * yRowStride

                var x = 0
                while (x < width) {
                    val uvCol = x / 2
                    val uIndex = uRowOffset + uvCol * uPixelStride
                    val vIndex = vRowOffset + uvCol * vPixelStride
                    val yIndex = yRowOffset + x * yPixelStride

                    val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                    val uVal = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                    // Standard YUV to RGB Conversion (BT.601)
                    val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)

                    // Fast RGB to HSV conversion
                    val rf = r / 255.0f
                    val gf = g / 255.0f
                    val bf = b / 255.0f

                    val max = maxOf(rf, maxOf(gf, bf))
                    val min = minOf(rf, minOf(gf, bf))
                    val delta = max - min

                    var hue = 0f
                    if (delta > 0.0001f) {
                        hue = when {
                            max == rf -> 60f * (((gf - bf) / delta) % 6f)
                            max == gf -> 60f * (((bf - rf) / delta) + 2f)
                            else -> 60f * (((rf - gf) / delta) + 4f)
                        }
                        if (hue < 0f) hue += 360f
                    }

                    val sat = if (max <= 0.0001f) 0f else delta / max
                    val value = max

                    sampleCount++
                    if (upPreset.matches(hue, sat, value)) {
                        upMatchCount++
                    } else if (downPreset.matches(hue, sat, value)) {
                        downMatchCount++
                    }

                    x += step
                }
                y += step
            }

            val upPercent = if (sampleCount > 0) (upMatchCount.toFloat() / sampleCount.toFloat()) * 100.0f else 0.0f
            val downPercent = if (sampleCount > 0) (downMatchCount.toFloat() / sampleCount.toFloat()) * 100.0f else 0.0f

            var triggeredAction: ScrollAction? = null

            if (settings.isDetectionActive && !isCooldownActive) {
                if (upPercent >= threshold && upPercent > downPercent) {
                    triggeredAction = ScrollAction.SCROLL_UP
                    lastTriggerTimestamp = now
                    Log.i(TAG, "Triggered SCROLL_UP! Up: $upPercent% (Threshold: $threshold%)")
                    AutoScrollAccessibilityService.scrollUp(
                        swipeDistanceRatio = settings.swipeDistanceRatio,
                        durationMs = settings.swipeDurationMs
                    )
                } else if (downPercent >= threshold && downPercent > upPercent) {
                    triggeredAction = ScrollAction.SCROLL_DOWN
                    lastTriggerTimestamp = now
                    Log.i(TAG, "Triggered SCROLL_DOWN! Down: $downPercent% (Threshold: $threshold%)")
                    AutoScrollAccessibilityService.scrollDown(
                        swipeDistanceRatio = settings.swipeDistanceRatio,
                        durationMs = settings.swipeDurationMs
                    )
                }
            }

            val updatedCooldownActive = (now - lastTriggerTimestamp) < cooldownMs
            val updatedRemainingCooldown = if (updatedCooldownActive) (cooldownMs - (now - lastTriggerTimestamp)) else 0L

            onResult(
                ColorAnalysisResult(
                    upPercentage = upPercent,
                    downPercentage = downPercent,
                    thresholdPercent = threshold,
                    upColorPreset = upPreset,
                    downColorPreset = downPreset,
                    triggeredAction = triggeredAction,
                    isCooldownActive = updatedCooldownActive,
                    remainingCooldownMs = updatedRemainingCooldown,
                    timestamp = now
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in ColorAnalyzer analysis loop", e)
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private const val TAG = "ColorAnalyzer"
    }
}
