package vn.maychieu.app

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** So sánh bài: chọn 2 ảnh trong Kho ảnh, chia đôi màn hình để xem cạnh nhau. */
class CompareActivity : ComponentActivity() {

    private lateinit var pickContainer: View
    private lateinit var viewContainer: View
    private lateinit var pickRows: LinearLayout
    private lateinit var pickEmpty: TextView
    private lateinit var btnGo: Button
    private lateinit var zoomLeft: ImageZoomPan
    private lateinit var zoomRight: ImageZoomPan
    private lateinit var compareDrawView: DrawingView
    private lateinit var compareDrawToolbar: View
    private var drawModeOn = false
    private val selectedOrder = mutableListOf<String>()   // tối đa 2, theo thứ tự chọn
    private val cols = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compare)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        pickContainer = findViewById(R.id.pickContainer)
        viewContainer = findViewById(R.id.viewContainer)
        pickRows = findViewById(R.id.pickRows)
        pickEmpty = findViewById(R.id.pickEmpty)
        btnGo = findViewById(R.id.btnCompareGo)

        zoomLeft = ImageZoomPan(this, findViewById(R.id.imgLeft)).apply { attachGestures() }
        zoomRight = ImageZoomPan(this, findViewById(R.id.imgRight)).apply { attachGestures() }
        compareDrawView = findViewById(R.id.compareDrawView)
        compareDrawToolbar = findViewById(R.id.compareDrawToolbar)

        findViewById<ImageButton>(R.id.btnBackPick).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnBackCompare).setOnClickListener { showPick() }
        findViewById<ImageButton>(R.id.btnComparePen).setOnClickListener { setDrawMode(!drawModeOn) }
        btnGo.setOnClickListener { showCompare() }

        setupDrawTool()
        reloadPick()
    }

    private fun dpF(v: Float) = v * resources.displayMetrics.density

    /** Vẽ để khoanh vùng khác biệt khi đang so sánh 2 ảnh — không lưu lại, chỉ để xem/chiếu lên TV lúc này. */
    private fun setupDrawTool() {
        val btnEraser = findViewById<ImageButton>(R.id.btnCdEraser)
        val penSizes = mapOf(R.id.cdSizeS to 4f, R.id.cdSizeM to 8f, R.id.cdSizeL to 16f)
        val eraserSizes = mapOf(R.id.cdSizeS to 14f, R.id.cdSizeM to 26f, R.id.cdSizeL to 42f)
        val sizeBtns = penSizes.keys.map { findViewById<Button>(it) }
        var curSizeId = R.id.cdSizeM

        fun applySize(id: Int) {
            curSizeId = id
            val w = (if (compareDrawView.eraseMode) eraserSizes[id] else penSizes[id]) ?: return
            if (compareDrawView.eraseMode) compareDrawView.eraserWidth = dpF(w) else compareDrawView.penWidth = dpF(w)
            sizeBtns.forEach { it.isSelected = it.id == id }
        }
        sizeBtns.forEach { b -> b.setOnClickListener { applySize(b.id) } }

        val swatches = mapOf(
            R.id.cdSwBlack to 0xFF111827.toInt(), R.id.cdSwRed to 0xFFE5484D.toInt(),
            R.id.cdSwBlue to 0xFF2B5FE8.toInt(), R.id.cdSwGreen to 0xFF22B566.toInt(), R.id.cdSwYellow to 0xFFF5B400.toInt()
        )
        val swatchViews = swatches.keys.map { findViewById<View>(it) }
        fun selectColor(id: Int, c: Int) {
            compareDrawView.color = c; compareDrawView.eraseMode = false
            swatchViews.forEach { it.isSelected = it.id == id }
            btnEraser.isSelected = false
            applySize(curSizeId)
        }
        swatches.forEach { (id, c) -> findViewById<View>(id).setOnClickListener { selectColor(id, c) } }

        btnEraser.setOnClickListener {
            compareDrawView.eraseMode = !compareDrawView.eraseMode
            btnEraser.isSelected = compareDrawView.eraseMode
            if (compareDrawView.eraseMode) swatchViews.forEach { it.isSelected = false }
            applySize(curSizeId)
        }

        selectColor(R.id.cdSwRed, 0xFFE5484D.toInt())
        applySize(R.id.cdSizeM)

        findViewById<ImageButton>(R.id.btnCdUndo).setOnClickListener { compareDrawView.undo() }
        findViewById<ImageButton>(R.id.btnCdRedo).setOnClickListener { compareDrawView.redo() }
        findViewById<ImageButton>(R.id.btnCdClear).setOnClickListener { compareDrawView.clearAll() }
        findViewById<Button>(R.id.btnCdDone).setOnClickListener { setDrawMode(false) }
    }

    private fun setDrawMode(on: Boolean) {
        drawModeOn = on
        compareDrawView.visibility = if (on) View.VISIBLE else View.GONE
        compareDrawToolbar.visibility = if (on) View.VISIBLE else View.GONE
        findViewById<ImageButton>(R.id.btnComparePen).isSelected = on
        findViewById<ImageButton>(R.id.btnBackCompare).visibility = if (on) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (viewContainer.visibility != View.VISIBLE) reloadPick()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun updateGoButton() {
        btnGo.text = "So sánh (${selectedOrder.size}/2)"
        btnGo.isEnabled = selectedOrder.size == 2
    }

    private fun reloadPick() {
        pickRows.removeAllViews()
        val items = GalleryStore.list(this)
        pickEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            }
            for (c in 0 until cols) {
                val item = items.getOrNull(i + c)
                row.addView(buildCell(item), LinearLayout.LayoutParams(0, dp(130), 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
            }
            pickRows.addView(row)
            i += cols
        }
        updateGoButton()
    }

    private fun buildCell(item: GalleryItem?): View {
        val frame = FrameLayout(this)
        if (item == null) { frame.visibility = View.INVISIBLE; return frame }
        val img = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#FFE2E8F0"))
            setImageBitmap(GalleryStore.loadThumbnail(this@CompareActivity, item))
        }
        frame.addView(img)

        val nameBar = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
            text = item.name
            setTextColor(Color.WHITE)
            textSize = 12f
            val ph = dp(8); val pv = dp(4)
            setPadding(ph, pv, ph, pv)
            setBackgroundColor(Color.parseColor("#99000000"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        frame.addView(nameBar)

        val isSel = selectedOrder.contains(item.id)
        val order = selectedOrder.indexOf(item.id) + 1
        val badge = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { topMargin = dp(6); marginEnd = dp(6) }
            background = ContextCompat.getDrawable(this@CompareActivity, if (isSel) R.drawable.bg_circle_blue_solid else R.drawable.bg_round)
        }
        if (isSel) {
            badge.addView(TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                text = order.toString()
                setTextColor(Color.WHITE)
                textSize = 13f
            })
        }
        frame.addView(badge)
        frame.alpha = if (isSel) 1f else 0.85f

        frame.setOnClickListener {
            if (isSel) selectedOrder.remove(item.id)
            else {
                if (selectedOrder.size >= 2) Toast.makeText(this, "Chỉ chọn được 2 ảnh. Bỏ chọn 1 ảnh trước.", Toast.LENGTH_SHORT).show()
                else selectedOrder.add(item.id)
            }
            reloadPick()
        }
        return frame
    }

    private fun showCompare() {
        if (selectedOrder.size != 2) return
        val items = GalleryStore.list(this)
        val a = items.find { it.id == selectedOrder[0] } ?: return
        val b = items.find { it.id == selectedOrder[1] } ?: return
        val bmpA = GalleryStore.load(this, a)
        val bmpB = GalleryStore.load(this, b)
        if (bmpA == null || bmpB == null) {
            Toast.makeText(this, "Không mở được ảnh", Toast.LENGTH_SHORT).show()
            return
        }
        zoomLeft.setBitmap(bmpA)
        zoomRight.setBitmap(bmpB)
        findViewById<TextView>(R.id.nameLeft).text = a.name
        findViewById<TextView>(R.id.nameRight).text = b.name
        setDrawMode(false)
        compareDrawView.clearAll()
        pickContainer.visibility = View.GONE
        viewContainer.visibility = View.VISIBLE
    }

    private fun showPick() {
        setDrawMode(false)
        viewContainer.visibility = View.GONE
        pickContainer.visibility = View.VISIBLE
        reloadPick()
    }
}
