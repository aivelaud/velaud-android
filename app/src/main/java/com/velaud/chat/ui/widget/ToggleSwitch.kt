package com.velaud.chat.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.animation.ObjectAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

class ToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private var isOn = false
    private var knobX = 0f

    private val offTrackColor = Color.parseColor("#1c1c1c")
    private val offBorderColor = Color.parseColor("#3a3a3a")
    private val offKnobColor = Color.parseColor("#6e6e6e")
    private val onTrackColor = Color.WHITE
    private val onKnobColor = Color.BLACK

    var onToggle: ((Boolean) -> Unit)? = null

    init {
        setOnClickListener {
            toggle()
        }
    }

    fun setOn(on: Boolean) {
        isOn = on
        isActivated = on
        invalidate()
    }

    fun toggle() {
        isOn = !isOn
        isActivated = isOn
        animateKnob()
        onToggle?.invoke(isOn)
    }

    private fun animateKnob() {
        val targetX = if (isOn) width - knobSize() - trackPad() else trackPad()
        ObjectAnimator.ofFloat(this, "knobX", knobX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun setKnobX(x: Float) {
        knobX = x
        invalidate()
    }

    private fun knobSize() = height - 2 * trackPad()
    private fun trackPad() = height * 0.08f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackRect.set(0f, 0f, w.toFloat(), h.toFloat())
        knobX = if (isOn) w - knobSize() - trackPad() else trackPad()
    }

    override fun onDraw(canvas: Canvas) {
        // Track
        trackPaint.color = if (isOn) onTrackColor else offTrackColor
        canvas.drawRoundRect(trackRect, height / 2f, height / 2f, trackPaint)

        // Border when off
        if (!isOn) {
            trackPaint.color = offBorderColor
            trackPaint.style = Paint.Style.STROKE
            trackPaint.strokeWidth = 2f
            canvas.drawRoundRect(trackRect, height / 2f, height / 2f, trackPaint)
            trackPaint.style = Paint.Style.FILL
        }

        // Knob
        knobPaint.color = if (isOn) onKnobColor else offKnobColor
        val r = knobSize() / 2f
        val pad = trackPad()
        canvas.drawCircle(knobX + r, pad + r, r, knobPaint)
    }
}
