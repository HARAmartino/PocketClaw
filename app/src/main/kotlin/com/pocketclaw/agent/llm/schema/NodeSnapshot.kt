package com.pocketclaw.agent.llm.schema

import android.graphics.Rect

/**
 * A snapshot of a single accessibility node, free of Android framework references.
 * Safe to pass across coroutine boundaries and hold in memory without leaking
 * [android.view.accessibility.AccessibilityNodeInfo] pool entries.
 */
data class NodeSnapshot(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val depth: Int,
    val children: List<NodeSnapshot> = emptyList(),
)

/**
 * Compressed DOM tree string produced by [com.pocketclaw.agent.accessibility.AccessibilityTreeCompressor].
 * Contains only semantic leaf nodes and paths in a compact text format suitable
 * for inclusion in an LLM prompt without exceeding context limits.
 */
@JvmInline
value class CompressedDomTree(val content: String)
