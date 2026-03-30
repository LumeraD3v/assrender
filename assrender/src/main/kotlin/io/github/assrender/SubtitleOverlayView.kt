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
 * on top of the video surface.
 */
class SubtitleOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var subtitleBitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()

    fun updateBitmap(bitmap: Bitmap?) {
        subtitleBitmap = bitmap
        postInvalidate()
    }

    fun clear() {
        subtitleBitmap = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = subtitleBitmap ?: return

        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(0, 0, width, height)
        canvas.drawBitmap(bmp, srcRect, dstRect, paint)
    }
}
