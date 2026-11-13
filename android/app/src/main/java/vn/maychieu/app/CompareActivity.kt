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

        findViewById<ImageButton>(R.id.btnBackPick).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnBackCompare).setOnClickListener { showPick() }
        btnGo.setOnClickListener { showCompare() }

        reloadPick()
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
        findViewById<ImageView>(R.id.imgLeft).setImageBitmap(GalleryStore.load(this, a))
        findViewById<ImageView>(R.id.imgRight).setImageBitmap(GalleryStore.load(this, b))
        findViewById<TextView>(R.id.nameLeft).text = a.name
        findViewById<TextView>(R.id.nameRight).text = b.name
        pickContainer.visibility = View.GONE
        viewContainer.visibility = View.VISIBLE
    }

    private fun showPick() {
        viewContainer.visibility = View.GONE
        pickContainer.visibility = View.VISIBLE
        reloadPick()
    }
}
