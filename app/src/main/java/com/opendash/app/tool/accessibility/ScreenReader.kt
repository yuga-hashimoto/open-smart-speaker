package com.opendash.app.tool.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extracts a lightweight text snapshot of the currently active screen.
 * Used by the LLM to "see" what's on screen without a vision model.
 */
interface ScreenReader {
    fun isReady(): Boolean
    fun readScreen(): ScreenSnapshot?
}

data class ScreenSnapshot(
    val packageName: String,
    val visibleTexts: List<String>,
    val clickableLabels: List<String>
)

class AccessibilityScreenReader : ScreenReader {

    override fun isReady(): Boolean = OpenSmartAccessibilityService.isRunning()

    override fun readScreen(): ScreenSnapshot? {
        val service = OpenSmartAccessibilityService.currentInstance() ?: return null
        val root = service.rootNode() ?: return null

        val texts = mutableListOf<String>()
        val clickable = mutableListOf<String>()
        walk(root, texts, clickable, depth = 0)

        return ScreenSnapshot(
            packageName = root.packageName?.toString().orEmpty(),
            visibleTexts = texts.distinct().take(50),
            clickableLabels = clickable.distinct().take(30)
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        clickable: MutableList<String>,
        depth: Int
    ) {
        if (node == null || depth > MAX_DEPTH) return

        val text = node.text?.toString()?.trim().orEmpty()
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
        val label = text.ifEmpty { contentDesc }

        if (label.isNotEmpty()) {
            texts.add(label)
            if (node.isClickable) clickable.add(label)
        }

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), texts, clickable, depth + 1)
        }
    }

    companion object {
        private const val MAX_DEPTH = 20
    }
}
