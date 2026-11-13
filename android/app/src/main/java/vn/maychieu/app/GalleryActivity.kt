package vn.maychieu.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Kho ảnh: lưới ảnh đã lưu, mỗi ảnh hiện tên học sinh ở đầu.
 * Bấm vào ảnh để mở chỉnh sửa; giữ để đổi tên; bấm "Chọn" để chọn nhiều ảnh rồi xóa.
 */
class GalleryActivity : ComponentActivity() {

    private lateinit var rows: LinearLayout
    private lateinit var empty: TextView
    private lateinit var btnSelectMode: Button
    private lateinit var btnDelete: Button
    private var selectMode = false
    private val selected = mutableSetOf<String>()
    private val cols = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        rows = findViewById(R.id.rows)
        empty = findViewById(R.id.emptyText)
        btnSelectMode = findViewById(R.id.btnSelectMode)
        btnDelete = findViewById(R.id.btnDeleteSelected)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnSelectMode.setOnClickListener { toggleSelectMode() }
        btnDelete.setOnClickListener { confirmDelete() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun toggleSelectMode() {
        selectMode = !selectMode
        selected.clear()
        btnSelectMode.text = if (selectMode) "Xong" else "Chọn"
        btnDelete.visibility = View.GONE
        reload()
    }

    private fun confirmDelete() {
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Xóa ${selected.size} ảnh?")
            .setMessage("Không thể khôi phục sau khi xóa.")
            .setPositiveButton("Xóa") { _, _ ->
                GalleryStore.delete(this, selected.toSet())
                selectMode = false
                selected.clear()
                btnSelectMode.text = "Chọn"
                btnDelete.visibility = View.GONE
                reload()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun renameDialog(item: GalleryItem) {
        val input = EditText(this).apply {
            setText(item.name)
            val p = dp(16)
            setPadding(p, p, p, p)
        }
        AlertDialog.Builder(this)
            .setTitle("Đổi tên ảnh")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ -> GalleryStore.rename(this, item.id, input.text.toString().trim()); reload() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun reload() {
        rows.removeAllViews()
        val items = GalleryStore.list(this)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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
            rows.addView(row)
            i += cols
        }
    }

    private fun buildCell(item: GalleryItem?): View {
        val frame = FrameLayout(this)
        if (item == null) { frame.visibility = View.INVISIBLE; return frame }

        val img = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#FFE2E8F0"))
            setImageBitmap(GalleryStore.loadThumbnail(this@GalleryActivity, item))
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

        if (selectMode) {
            val isSel = selected.contains(item.id)
            val badge = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { topMargin = dp(6); marginEnd = dp(6) }
                background = ContextCompat.getDrawable(this@GalleryActivity, if (isSel) R.drawable.bg_circle_blue_solid else R.drawable.bg_round)
            }
            if (isSel) {
                badge.addView(ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                    setImageResource(R.drawable.ic_check)
                })
            }
            frame.addView(badge)
            frame.alpha = if (isSel) 1f else 0.78f
        }

        frame.setOnClickListener {
            if (selectMode) {
                if (selected.contains(item.id)) selected.remove(item.id) else selected.add(item.id)
                btnDelete.text = "Xóa (${selected.size})"
                btnDelete.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
                reload()
            } else {
                startActivity(Intent(this@GalleryActivity, PhotoEditorActivity::class.java).apply {
                    putExtra(PhotoEditorActivity.EXTRA_MODE, PhotoEditorActivity.MODE_EDIT)
                    putExtra(PhotoEditorActivity.EXTRA_ID, item.id)
                })
            }
        }
        frame.setOnLongClickListener {
            if (!selectMode) renameDialog(item)
            true
        }
        return frame
    }
}
