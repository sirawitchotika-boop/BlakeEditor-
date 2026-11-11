package com.blake.editor.ui.preview

/**
 * Pure layout math used by Blake 2.0 Preview when the system reports status
 * bars, a display cutout, navigation bars and the soft keyboard.
 *
 * The insets are absolute on every callback, not cumulative. In particular a
 * camera cutout and status bar must use their maximum, not be added together.
 */
data class SafeEdges(val left: Int = 0, val top: Int = 0,
    val right: Int = 0, val bottom: Int = 0
)

object PreviewSafeArea {
    fun padding(base: SafeEdges, bars: SafeEdges, cutout: SafeEdges,
        keyboardBottom: Int
    ): SafeEdges = SafeEdges(
        base.left + maxOf(bars.left, cutout.left),
        base.top + maxOf(bars.top, cutout.top),
        base.right + maxOf(bars.right, cutout.right),
        base.bottom + maxOf(bars.bottom, cutout.bottom, keyboardBottom.coerceAtLeast(0))
    )

    /** True means Android should use dark icons on a light background. */
    fun darkSystemIcons(background: Int): Boolean {
        val red = background ushr 16 and 255
        val green = background ushr 8 and 255
        val blue = background and 255
        return (299 * red + 587 * green + 114 * blue) / 1000 >= 160
    }
}
