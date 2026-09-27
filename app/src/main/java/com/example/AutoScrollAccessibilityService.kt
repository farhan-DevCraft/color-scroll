package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutoScrollAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _serviceStateFlow.value = true
        Log.i(TAG, "AutoScrollAccessibilityService connected successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _serviceStateFlow.value = false
        Log.i(TAG, "AutoScrollAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can monitor window changes if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoScrollAccessibilityService interrupted")
    }

    /**
     * Performs a vertical swipe gesture tailored for feeds like Instagram Reels, TikTok, and YouTube Shorts.
     *
     * @param isUp True to scroll UP (reveals previous video above; finger swipes downwards)
     *             False to scroll DOWN (reveals next video below; finger swipes upwards)
     * @param distanceRatio Fraction of screen height to swipe across (0.2 to 0.8)
     * @param durationMs Gesture duration in milliseconds (150ms to 600ms)
     */
    fun performScrollGesture(
        isUp: Boolean,
        distanceRatio: Float = 0.50f,
        durationMs: Long = 250L
    ): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val centerX = width / 2.0f
        val clampedRatio = distanceRatio.coerceIn(0.20f, 0.80f)
        val swipeLength = height * clampedRatio

        val startY: Float
        val endY: Float

        if (isUp) {
            // Finger swipes from top-half toward bottom to drag previous reel down
            startY = (height / 2.0f) - (swipeLength / 2.0f)
            endY = (height / 2.0f) + (swipeLength / 2.0f)
        } else {
            // Finger swipes from bottom-half toward top to reveal next reel
            startY = (height / 2.0f) + (swipeLength / 2.0f)
            endY = (height / 2.0f) - (swipeLength / 2.0f)
        }

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val clampedDuration = durationMs.coerceIn(100L, 800L)
        val stroke = GestureDescription.StrokeDescription(path, 0, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        _lastGestureEventFlow.value = GestureEvent(
            action = if (isUp) "Scroll Up" else "Scroll Down",
            timestamp = SystemClock.uptimeMillis(),
            success = true
        )

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Gesture completed successfully: isUp=$isUp")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture cancelled: isUp=$isUp; falling back to node scroll")
                fallbackNodeScroll(isUp)
            }
        }, null)

        if (!dispatched) {
            Log.d(TAG, "dispatchGesture returned false; attempting fallback node scroll")
            fallbackNodeScroll(isUp)
        }

        return dispatched
    }

    private fun fallbackNodeScroll(isUp: Boolean) {
        val rootNode = rootInActiveWindow ?: return
        val action = if (isUp) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        findAndPerformScroll(rootNode, action)
    }

    private fun findAndPerformScroll(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.actionList.any { it.id == action }) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndPerformScroll(child, action)) {
                return true
            }
        }
        return false
    }

    data class GestureEvent(
        val action: String,
        val timestamp: Long,
        val success: Boolean
    )

    companion object {
        private const val TAG = "AutoScrollService"

        var instance: AutoScrollAccessibilityService? = null
            private set

        private val _serviceStateFlow = MutableStateFlow(false)
        val serviceStateFlow: StateFlow<Boolean> = _serviceStateFlow.asStateFlow()

        private val _lastGestureEventFlow = MutableStateFlow<GestureEvent?>(null)
        val lastGestureEventFlow: StateFlow<GestureEvent?> = _lastGestureEventFlow.asStateFlow()

        val isServiceRunning: Boolean
            get() = instance != null

        /**
         * Trigger scroll down programmatically from any app context.
         */
        fun scrollDown(swipeDistanceRatio: Float = 0.50f, durationMs: Long = 250L): Boolean {
            return instance?.performScrollGesture(
                isUp = false,
                distanceRatio = swipeDistanceRatio,
                durationMs = durationMs
            ) ?: false
        }

        /**
         * Trigger scroll up programmatically from any app context.
         */
        fun scrollUp(swipeDistanceRatio: Float = 0.50f, durationMs: Long = 250L): Boolean {
            return instance?.performScrollGesture(
                isUp = true,
                distanceRatio = swipeDistanceRatio,
                durationMs = durationMs
            ) ?: false
        }

        /**
         * Checks if the Accessibility Service is enabled in Android System Settings.
         */
        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            if (instance != null) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expectedComponentName = ComponentName(context, AutoScrollAccessibilityService::class.java).flattenToString()
            val shortExpectedName = "${context.packageName}/${AutoScrollAccessibilityService::class.java.name}"

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                    componentName.equals(shortExpectedName, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Opens Android System Accessibility Settings page.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
