package com.opendash.app.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Root-free UI control surface for Phase 15. This PR (P15.1) establishes the
 * service skeleton only — follow-up PRs (P15.2 – P15.5) add the
 * `read_active_screen`, `tap_by_text`, `scroll_screen`, and `type_text` tools
 * on top of the reference exposed via [A11yServiceHolder].
 *
 * The user must enable the service in Settings > Accessibility. The companion
 * [A11yServiceHolder] is notified on connect / unbind so tool executors can
 * call back into the live service without depending on Android context.
 */
@AndroidEntryPoint
open class OpenDashA11yService : AccessibilityService() {

    @Inject
    lateinit var holder: A11yServiceHolder

    @Volatile
    private var lastForegroundPackage: String? = null

    /**
     * Returns the most recently observed foreground package name. Callers may
     * also observe [A11yServiceHolder.currentPackage] as a StateFlow.
     */
    fun currentForegroundPackage(): String? = lastForegroundPackage

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        holder.attach(this)
        Timber.d("OpenDashA11yService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != lastForegroundPackage) {
            lastForegroundPackage = pkg
            holder.updateCurrentPackage(pkg)
            Timber.v("a11y event: type=%d pkg=%s", event.eventType, pkg)
        }
    }

    override fun onInterrupt() {
        // No-op: skeleton service does not coordinate long-running feedback.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        holder.detach(this)
        lastForegroundPackage = null
        Timber.d("OpenDashA11yService unbound")
        return super.onUnbind(intent)
    }

    /**
     * Walks [rootInActiveWindow] as a BFS tree and returns a flat list of
     * nodes with non-blank text or content-description. Caps traversal at
     * [MAX_DEPTH] and [MAX_NODES] for safety. Recycles every visited node
     * explicitly — even on API 33+ where the platform recommends against it,
     * we keep the manual recycle for broad compatibility with older devices
     * this app still targets (min SDK 28).
     */
    fun dumpActiveWindow(): List<NodeSummary> {
        val root = rootInActiveWindow ?: return emptyList()
        return walkTree(root)
    }

    /**
     * BFS walk of [rootInActiveWindow] to find the first clickable node whose
     * `text` or `contentDescription` contains [query] (case-insensitive).
     *
     * Returns null if the root is unavailable or no match is found. The
     * returned node is NOT recycled — caller owns it. Sibling nodes visited
     * during the search are recycled.
     */
    fun findNodeByText(query: String): AccessibilityNodeInfo? {
        if (query.isBlank()) return null
        val root = rootInActiveWindow ?: return null
        val needle = query.trim().lowercase()
        val queue: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
        queue.addLast(root to 0)
        var visited = 0
        var match: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && visited < MAX_NODES && match == null) {
            val (node, depth) = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (node.isClickable && (text.contains(needle) || desc.contains(needle))) {
                match = node
                continue
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(child to depth + 1)
                }
            }
            if (node !== root) {
                @Suppress("DEPRECATION")
                runCatching { node.recycle() }
            }
        }
        // Drain remaining queue (they're not the match, not the root).
        while (queue.isNotEmpty()) {
            val (leftover, _) = queue.removeFirst()
            if (leftover !== root && leftover !== match) {
                @Suppress("DEPRECATION")
                runCatching { leftover.recycle() }
            }
        }
        return match
    }

    /**
     * Computes [node]'s on-screen centre and dispatches a short tap gesture
     * via [dispatchTap]. Returns true on success, false if the node has a
     * zero-area bounds, dispatch returns false, or an exception is thrown.
     */
    fun performTapOnNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Timber.w("performTapOnNode: empty bounds %s", bounds)
            return false
        }
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        return dispatchTap(cx, cy, TAP_DURATION_MS)
    }

    /**
     * Swipes across the visible display centre in [direction]
     * ("up", "down", "left", "right"). Distance is 60% of the root window's
     * width (horizontal) or height (vertical). Returns false for unknown
     * directions or when dispatch fails.
     */
    fun performSwipe(direction: String, durationMs: Long = DEFAULT_SWIPE_DURATION_MS): Boolean {
        val root = rootInActiveWindow
        val bounds = Rect()
        if (root != null) {
            root.getBoundsInScreen(bounds)
        }
        // Fall back to a sensible default if root bounds are empty.
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            bounds.set(0, 0, DEFAULT_WINDOW_WIDTH_PX, DEFAULT_WINDOW_HEIGHT_PX)
        }
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val horizDelta = bounds.width() * SWIPE_DISTANCE_FRACTION / 2f
        val vertDelta = bounds.height() * SWIPE_DISTANCE_FRACTION / 2f
        val (sx, sy, ex, ey) = when (direction.trim().lowercase()) {
            // A scroll-up gesture moves content up, i.e. finger swipes from
            // bottom to top.
            "up" -> floatArrayOf(cx, cy + vertDelta, cx, cy - vertDelta)
            "down" -> floatArrayOf(cx, cy - vertDelta, cx, cy + vertDelta)
            "left" -> floatArrayOf(cx + horizDelta, cy, cx - horizDelta, cy)
            "right" -> floatArrayOf(cx - horizDelta, cy, cx + horizDelta, cy)
            else -> {
                Timber.w("performSwipe: unknown direction %s", direction)
                return false
            }
        }.let { arr -> SwipeCoords(arr[0], arr[1], arr[2], arr[3]) }
        return dispatchSwipe(sx, sy, ex, ey, durationMs)
    }

    /**
     * Sends [text] to the currently focused input node via
     * `ACTION_SET_TEXT`. If that fails, falls back to putting [text] on the
     * clipboard and dispatching `ACTION_PASTE`.
     *
     * Returns false when there is no focused input node, or when both paths
     * fail.
     */
    fun typeIntoFocused(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val bundle = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        val setOk = runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }.getOrElse { false }
        if (setOk) return true
        // Fallback: clipboard paste.
        val clipboard = runCatching {
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        }.getOrNull() ?: return false
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("a11y-paste", text))
        }.onFailure { return false }
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrElse { false }
    }

    /**
     * Dispatches a single-point tap gesture at ([x], [y]). Isolated into an
     * open method so unit tests can stub it without mocking
     * [GestureDescription] or [dispatchGesture] directly.
     */
    internal open fun dispatchTap(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return runCatching { dispatchGesture(gesture, null, null) }.getOrElse { false }
    }

    /**
     * Dispatches a straight-line swipe from ([sx], [sy]) to ([ex], [ey]).
     * Isolated for the same reason as [dispatchTap].
     */
    internal open fun dispatchSwipe(
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return runCatching { dispatchGesture(gesture, null, null) }.getOrElse { false }
    }

    private data class SwipeCoords(val sx: Float, val sy: Float, val ex: Float, val ey: Float)

    companion object {
        const val MAX_DEPTH: Int = 8
        const val MAX_NODES: Int = 200
        const val TAP_DURATION_MS: Long = 50L
        const val DEFAULT_SWIPE_DURATION_MS: Long = 300L
        const val SWIPE_DISTANCE_FRACTION: Float = 0.6f

        /** Conservative fallback window size when rootInActiveWindow bounds are empty. */
        private const val DEFAULT_WINDOW_WIDTH_PX: Int = 1080
        private const val DEFAULT_WINDOW_HEIGHT_PX: Int = 1920

        /**
         * Pure BFS walker extracted for unit testing. Consumes a root
         * [AccessibilityNodeInfo] and returns a flat list of [NodeSummary].
         *
         * Skips nodes where both `text` and `contentDescription` are null or
         * blank. Every visited node is recycled to avoid leaking native
         * references on older API levels.
         */
        fun walkTree(root: AccessibilityNodeInfo): List<NodeSummary> {
            val out = mutableListOf<NodeSummary>()
            // Queue entries carry (node, depth). Nodes are owned by the
            // queue and recycled after being processed.
            val queue: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
            queue.addLast(root to 0)

            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++

                try {
                    val text = node.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
                    val desc = node.contentDescription?.toString()?.trim()
                        .takeUnless { it.isNullOrBlank() }

                    if (text != null || desc != null) {
                        val bounds = Rect().also { node.getBoundsInScreen(it) }
                        out.add(
                            NodeSummary(
                                text = text,
                                role = roleFor(node),
                                contentDescription = desc,
                                clickable = node.isClickable,
                                bounds = bounds
                            )
                        )
                    }

                    if (depth < MAX_DEPTH) {
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            queue.addLast(child to depth + 1)
                        }
                    }
                } finally {
                    // Don't recycle the root — caller owns it via
                    // rootInActiveWindow and the platform manages its
                    // lifecycle. We still recycle discovered children.
                    if (node !== root) {
                        @Suppress("DEPRECATION")
                        runCatching { node.recycle() }
                    }
                }
            }

            // Drain the remaining queue if we hit MAX_NODES without
            // processing them, so we don't leak those nodes either.
            while (queue.isNotEmpty()) {
                val (leftover, _) = queue.removeFirst()
                if (leftover !== root) {
                    @Suppress("DEPRECATION")
                    runCatching { leftover.recycle() }
                }
            }

            return out
        }

        private fun roleFor(node: AccessibilityNodeInfo): String {
            val className = node.className?.toString().orEmpty()
            val simple = className.substringAfterLast('.')
            return when {
                simple.isBlank() -> "view"
                simple.equals("EditText", ignoreCase = true) -> "edit-text"
                simple.contains("Button", ignoreCase = true) -> "button"
                simple.contains("ImageView", ignoreCase = true) -> "image"
                simple.contains("TextView", ignoreCase = true) -> "text"
                simple.contains("CheckBox", ignoreCase = true) -> "checkbox"
                simple.contains("Switch", ignoreCase = true) -> "switch"
                else -> simple.lowercase()
            }
        }
    }
}

/**
 * Flat summary of a single [AccessibilityNodeInfo] produced by
 * [OpenDashA11yService.dumpActiveWindow].
 */
data class NodeSummary(
    val text: String?,
    val role: String,
    val contentDescription: String?,
    val clickable: Boolean,
    val bounds: Rect
)
