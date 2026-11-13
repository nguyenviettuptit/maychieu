package vn.maychieu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Bảng vẽ: dùng để chú thích lên ảnh đã chụp (gọi setPhoto), hoặc vẽ đè lên hình camera
 * đang chiếu (không gọi setPhoto, nền trong suốt — hiện cả trên TV vì cùng màn hình đang cast).
 * Hoàn tác / làm lại: lưu danh sách nét vẽ rồi vẽ lại từ đầu mỗi lần hoàn tác — đơn giản, luôn đúng.
 */
class DrawingView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private class Stroke(val path: Path, val color: Int, val strokeW: Float, val erase: Boolean)

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var committed: Bitmap? = null
    private var committedCanvas: Canvas? = null
    private var baseBitmap: Bitmap? = null

    private var curPath: Path? = null
    private var curX = 0f
    private var curY = 0f

    var color: Int = 0xFFE5484D.toInt()
    var strokeWidth: Float = 10f
    var eraseMode: Boolean = false
    var onChanged: (() -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // để chế độ tẩy (PorterDuff.CLEAR) luôn vẽ đúng
    }

    /** Đặt ảnh nền để chỉnh sửa (ảnh được vẽ vừa khung, giữ nguyên tỉ lệ). */
    fun setPhoto(bmp: Bitmap) {
        baseBitmap = bmp
        rebuildCommitted()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) rebuildCommitted()
    }

    private fun ensureCommitted() {
        if (width <= 0 || height <= 0) return
        if (committed == null || committed!!.width != width || committed!!.height != height) rebuildCommitted()
    }

    private fun rebuildCommitted() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        baseBitmap?.let { base ->
            val scale = minOf(w.toFloat() / base.width, h.toFloat() / base.height)
            val dw = base.width * scale; val dh = base.height * scale
            val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(base, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        strokes.forEach { drawStroke(c, it) }
        committed = bmp
        committedCanvas = c
    }

    private fun paintFor(s: Stroke) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = s.strokeW
        color = s.color
        if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private fun drawStroke(c: Canvas, s: Stroke) = c.drawPath(s.path, paintFor(s))

    override fun onTouchEvent(event: MotionEvent): Boolean {
        ensureCommitted()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                curPath = Path().apply { moveTo(event.x, event.y) }
                curX = event.x; curY = event.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val p = curPath ?: return true
                val midX = (curX + event.x) / 2f
                val midY = (curY + event.y) / 2f
                p.quadTo(curX, curY, midX, midY)
                curX = event.x; curY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val p = curPath
                if (p != null) {
                    p.lineTo(curX, curY)
                    val s = Stroke(p, color, strokeWidth, eraseMode)
                    strokes.add(s)
                    committedCanvas?.let { drawStroke(it, s) }
                    redoStack.clear()
                    onChanged?.invoke()
                }
                curPath = null
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        ensureCommitted()
        committed?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        curPath?.let { canvas.drawPath(it, paintFor(Stroke(it, color, strokeWidth, eraseMode))) }
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        rebuildCommitted(); invalidate(); onChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        rebuildCommitted(); invalidate(); onChanged?.invoke()
    }

    fun clearAll() {
        if (strokes.isEmpty()) return
        strokes.clear(); redoStack.clear()
        rebuildCommitted(); invalidate(); onChanged?.invoke()
    }

    fun canUndo() = strokes.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun hasDrawing() = strokes.isNotEmpty()

    /** Ảnh nền + toàn bộ nét vẽ, để lưu lại. */
    fun exportBitmap(): Bitmap {
        ensureCommitted()
        return committed?.copy(Bitmap.Config.ARGB_8888, false) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
