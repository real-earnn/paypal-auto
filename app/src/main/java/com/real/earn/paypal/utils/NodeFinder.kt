package com.real.earn.paypal.utils

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility to walk the accessibility tree and find/tap nodes.
 * All methods handle nulls safely.
 */
object NodeFinder {

    fun findByText(root: AccessibilityNodeInfo?, vararg texts: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            for (target in texts) {
                if (t.equals(target, ignoreCase = true)) return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findByTextContains(root: AccessibilityNodeInfo?, vararg texts: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.lowercase() ?: ""
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            for (target in texts) {
                val tl = target.lowercase()
                if (t.contains(tl) || d.contains(tl)) return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
    }

    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    fun click(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val clickable = if (node.isClickable) node else findClickableParent(node)
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun clickAny(root: AccessibilityNodeInfo?, vararg texts: String): Boolean {
        val node = findByText(root, *texts) ?: findByTextContains(root, *texts)
        return click(node)
    }

    fun containsAny(root: AccessibilityNodeInfo?, vararg texts: String): Boolean {
        return findByTextContains(root, *texts) != null
    }

    fun isVisible(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val r = Rect()
        node.getBoundsInScreen(r)
        return r.width() > 0 && r.height() > 0 && node.isVisibleToUser
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Find first editable node (EditText) */
    fun findEditText(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** Wait for a node matching any text to appear. Returns the node or null. */
    fun waitForAny(
        rootProvider: () -> AccessibilityNodeInfo?,
        timeoutMs: Long,
        pollMs: Long = 400,
        vararg texts: String
    ): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val r = rootProvider()
            val n = findByText(r, *texts) ?: findByTextContains(r, *texts)
            if (n != null) return n
            Thread.sleep(pollMs)
        }
        return null
    }
}
