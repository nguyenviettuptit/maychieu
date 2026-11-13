package vn.maychieu.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * Thêm chụm 2 ngón để zoom + kéo xem cho 1 ImageView hiển thị ảnh tĩnh, độc lập với ImageView khác
 * (dùng cho màn hình So sánh bài tập: mỗi bên phóng to/kéo riêng). Chạm 2 lần để phóng nhanh / về gốc.
 */
class ImageZoomPan(private val context: Context, private val img: ImageView) {
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f
    private var bmpW = 0f
    private var bmpH = 0f

    init {
        img.scaleType = ImageView.ScaleType.MATRIX
    }

    fun setBitmap(bmp: Bitmap) {
        img.setImageBitmap(bmp)
        bmpW = bmp.width.toFloat(); bmpH = bmp.height.toFloat()
        scale = 1f; panX = 0f; panY = 0f
        img.post { applyMatrix() }
    }

    private fun baseScale(): Float {
        val vw = img.width.toFloat(); val vh = img.height.toFloat()
        if (vw <= 0 || vh <= 0 || bmpW <= 0 || bmpH <= 0) return 1f
        return minOf(vw / bmpW, vh / bmpH)
    }

    private fun applyMatrix() {
        val base = baseScale()
        val s = base * scale
        val vw = img.width.toFloat(); val vh = img.height.toFloat()
        val dw = bmpW * s; val dh = bmpH * s
        val mx = maxOf(0f, (dw - vw) / 2f)
        val my = maxOf(0f, (dh - vh) / 2f)
        panX = panX.coerceIn(-mx, mx)
        panY = panY.coerceIn(-my, my)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate((vw - dw) / 2f + panX, (vh - dh) / 2f + panY)
        img.imageMatrix = m
    }

    private fun zoomAt(newScale: Float, fx: Float, fy: Float) {
        val ns = newScale.coerceIn(1f, 6f)
        val cx = img.width / 2f; val cy = img.height / 2f
        val k = ns / scale
        panX = (fx - cx) - ((fx - cx) - panX) * k
        panY = (fy - cy) - ((fy - cy) - panY) * k
        scale = ns
        if (scale <= 1.01f) { panX = 0f; panY = 0f }
        applyMatrix()
    }

    /** Gọi 1 lần sau khi tạo để bật cử chỉ. Trả về true khi đang zoom (để nơi gọi biết có nên kéo cả 2 bên cùng lúc hay không — không dùng ở đây, mỗi bên độc lập). */
    @SuppressLint("ClickableViewAccessibility")
    fun attachGestures() {
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomAt(scale * d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        })
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > 1.05f) zoomAt(1f, e.x, e.y) else zoomAt(2.5f, e.x, e.y)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scale > 1.01f) { panX -= dx; panY -= dy; applyMatrix() }
                return true
            }
        })
        img.setOnTouchListener { _, ev ->
            scaleDetector.onTouchEvent(ev)
            gestureDetector.onTouchEvent(ev)
            true
        }
    }
}
