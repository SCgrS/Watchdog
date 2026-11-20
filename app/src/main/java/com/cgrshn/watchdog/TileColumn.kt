package com.cgrshn.watchdog

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * The column of status tiles. Every tile tagged [EQUAL] gets the height of the tallest of them, so the settings read as
 * cards of one size even when a long name or a list of apps wraps to a second line (the owner's request, 2026-11-20).
 * Auto Blocker's tile, which carries a note, keeps its own height. The tiles center their content vertically.
 */
class TileColumn(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val equal = (0 until childCount).map(::getChildAt).filter { it.visibility != GONE && it.tag == EQUAL }
        val tallest = equal.maxOfOrNull { it.measuredHeight } ?: return
        var added = 0
        for (child in equal) {
            val extra = tallest - child.measuredHeight
            if (extra <= 0) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY),
            )
            added += extra
        }
        if (added > 0) setMeasuredDimension(measuredWidth, measuredHeight + added)
    }

    companion object {
        const val EQUAL = "equal"
    }
}
