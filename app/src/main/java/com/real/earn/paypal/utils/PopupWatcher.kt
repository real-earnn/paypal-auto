package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background popup watcher.
 * Continuously scans screen for Gmail/Google sign-in popups and closes them.
 * Runs as a separate coroutine, in parallel with the main automation flow.
 */
object PopupWatcher {

    private var job: Job? = null

    fun start(scope: CoroutineScope, service: AccessibilityService) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            AutomationState.log("👁 PopupWatcher started")
            while (isActive) {
                if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                try {
                    scanAndDismiss(service)
                } catch (_: Exception) {}
                delay(300)
            }
            AutomationState.log("👁 PopupWatcher stopped")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ══════════════════════════════════════════════════════
    //  One scan cycle
    // ══════════════════════════════════════════════════════
    private fun scanAndDismiss(service: AccessibilityService) {
        val root = service.rootInActiveWindow ?: return

        // ═══ Gmail popup markers ═══
        // Any of these = it's a Google sign-in / account chooser popup
        val isGmailPopup =
            NodeFinder.containsAny(
                root,
                "Choose an account for PayPal",
                "Choose an account",
                "Sign-in options",
                "Sign in with Google"
            ) ||
            // PayPal header present AND close button on same row to the right
            hasPaypalHeaderWithClose(root)

        if (!isGmailPopup) return

        // Find X / close / cross button and click it
        if (clickGmailPopupClose(root)) {
            AutomationState.log("👁 Gmail popup closed")
            SystemClock.sleep(500)
        }
    }

    // ══════════════════════════════════════════════════════
    //  "PayPal" header somewhere + X in same Y row to right
    // ══════════════════════════════════════════════════════
    private fun hasPaypalHeaderWithClose(root: AccessibilityNodeInfo): Boolean {
        val headers = findAllByText(root, "PayPal")
        for (h in headers) {
            val hr = Rect(); h.getBoundsInScreen(hr)
            if (findCloseInRow(root, hr) != null) return true
        }
        return false
    }

    private fun clickGmailPopupClose(root: AccessibilityNodeInfo): Boolean {
        // Method 1: close button in same row as "PayPal" header
        val headers = findAllByText(root, "PayPal")
        for (h in headers) {
            val hr = Rect(); h.getBoundsInScreen(hr)
            val close = findCloseInRow(root, hr)
            if (close != null && NodeFinder.click(close)) return true
        }

        // Method 2: any X / close button on screen (only if Gmail popup confirmed by caller)
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (n.isVisibleToUser && closeWords.any { it.equals(t, true) || it.equals(d, true) }) {
                if (NodeFinder.click(n)) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun findCloseInRow(root: AccessibilityNodeInfo, headerRect: Rect): AccessibilityNodeInfo? {
        val closeWords = arrayOf("✕", "✖", "×", "X", "✗", "❌", "❎", "Close", "close")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            if (n.isVisibleToUser && closeWords.any { it.equals(t, true) || it.equals(d, true) }) {
                val r = Rect(); n.getBoundsInScreen(r)
                val sameRow = Math.abs(r.centerY() - headerRect.centerY()) < 130
                val onRight = r.centerX() > headerRect.centerX()
                if (sameRow && onRight) return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findAllByText(root: AccessibilityNodeInfo?, target: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals(target, true) && n.isVisibleToUser) result.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return result
    }
}
