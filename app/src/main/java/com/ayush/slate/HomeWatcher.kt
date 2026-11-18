package com.ayush.slate

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS doesn't let third-party launchers own the Home button. This service only listens for
 * "a new window came to the front". When it's Amazon's home screen, it opens Slate instead.
 * It reads no screen content, sees no text and has no network access.
 */
class HomeWatcher : AccessibilityService() {
    private var last = 0L

    // Popups that belong to the Amazon launcher package but aren't the home screen itself
    // (e.g. the long-press-Home quick menu). Leave those alone.
    private val ignore = listOf("Dialog", "Quick", "Popup", "Toast", "Overlay", "Menu", "Sleep", "Notification")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        val prefs = Prefs(this)

        if (pkg.startsWith(Apps.AMAZON_HOME)) {
            prefs.noteSeen(cls)
            if (!prefs.takeover || prefs.bypassActive()) return
            if (ignore.any { cls.contains(it, ignoreCase = true) }) return
            val now = SystemClock.uptimeMillis()
            if (now - last < 700) return
            last = now
            try {
                startActivity(
                    Intent(this, HomeActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                )
            } catch (e: Exception) { }
        } else if (prefs.bypass && pkg != packageName && !isSystemChrome(pkg)) {
            // User left Amazon Home for a real app — the one-time bypass is over.
            prefs.bypass = false
        }
    }

    private fun isSystemChrome(pkg: String) =
        pkg == "android" || pkg.startsWith("com.android.systemui") ||
            pkg.contains("inputmethod") || pkg.contains("keyboard")

    override fun onInterrupt() {}
}
