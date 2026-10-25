package com.b5solucoes.resultadostv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object Palette {
    val background = Color.rgb(9, 18, 31)
    val panel = Color.rgb(18, 35, 52)
    val card = Color.rgb(23, 44, 61)
    val text = Color.rgb(239, 247, 251)
    val muted = Color.rgb(166, 187, 204)
    val accent = Color.rgb(125, 226, 192)
    val warning = Color.rgb(255, 217, 142)
}
fun Context.dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
fun Context.text(value: String, size: Float = 16f, color: Int = Palette.text, bold: Boolean = false) =
    TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
    }
fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
fun Context.shape(color: Int, radius: Int = 14, stroke: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
    if (stroke != 0) setStroke(dp(2), stroke)
}
fun Context.focusBackground(selected: Boolean = false) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(32, 65, 78), 12, Palette.accent))
    addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(38, 75, 86), 12, Palette.accent))
    addState(intArrayOf(), shape(if (selected) Color.rgb(27, 60, 70) else Palette.panel, 12))
}
fun Context.action(label: String, tagValue: String, selected: Boolean = false, block: () -> Unit): TextView =
    text(label, 15f, if (selected) Palette.accent else Palette.text, true).apply {
        id = View.generateViewId(); tag = tagValue
        contentDescription = label
        isFocusable = true; isClickable = true
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(48)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = focusBackground(selected)
        setOnClickListener { block() }
    }
