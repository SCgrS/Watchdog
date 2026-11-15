package com.cgrshn.watchdog

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * The app's own choice window, used for the language and the check interval. It is drawn like the screen's cards
 * (graphite surface, rounded corners, hairline border), so it reads as part of the app rather than a system dialog. The
 * chosen option carries a green ring, in the same visual language as the status lamps.
 */
object ChoiceDialog {
    private const val SIDE_MARGIN_DP = 24
    private const val MAX_WIDTH_DP = 420

    /** [onChoose] runs only when a different option is picked. Tapping the current option or Cancel just closes. */
    fun show(
        activity: Activity,
        title: Int,
        subtitle: Int,
        options: List<String>,
        selected: Int,
        onChoose: (Int) -> Unit,
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_choice, FrameLayout(activity), false)
        view.findViewById<TextView>(R.id.choice_title).setText(title)
        view.findViewById<TextView>(R.id.choice_subtitle).setText(subtitle)

        val list = view.findViewById<LinearLayout>(R.id.choice_options)
        options.forEachIndexed { index, label ->
            val row = inflater.inflate(R.layout.row_choice, list, false)
            row.findViewById<TextView>(R.id.choice_label).text = label
            // The selected state drives the ring drawable and is announced by screen readers.
            row.isSelected = index == selected
            row.setOnClickListener {
                dialog.dismiss()
                if (index != selected) onChoose(index)
            }
            list.addView(row)
        }
        view.findViewById<View>(R.id.choice_cancel).setOnClickListener { dialog.cancel() }

        dialog.setContentView(view)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val density = activity.resources.displayMetrics.density
            val screenWidth = activity.resources.displayMetrics.widthPixels
            val width = min(screenWidth - (2 * SIDE_MARGIN_DP * density).toInt(), (MAX_WIDTH_DP * density).toInt())
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
            // Touches are ignored while another app's overlay covers the window.
            window.decorView.filterTouchesWhenObscured = true
        }
        dialog.show()
    }
}
