package io.github.assrender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay view that draws rendered subtitle bitmaps
 * on top of the video surface. Uses double-buffering to avoid flicker.
 */
class SubtitleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var displayBitmap: Bitmap? = null
    private val lock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()
    private var hasContent = false

    fun updateBitmap(source: Bitmap) {
        synchronized(lock) {
            if (displayBitmap == null ||
                displayBitmap!!.width != source.width ||
                displayBitmap!!.height != source.height) {
                displayBitmap?.recycle()
                displayBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                val canvas = Canvas(displayBitmap!!)
                canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(source, 0f, 0f, null)
            }
            hasContent = true
        }
        if (visibility != VISIBLE) visibility = VISIBLE
        invalidate()
    }

    fun clear() {
        synchronized(lock) {
            if (!hasContent) return
            hasContent = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        synchronized(lock) {
            if (!hasContent) return
            val bmp = displayBitmap ?: return
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(0, 0, width, height)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }
    }
}
