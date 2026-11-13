package vn.maychieu.app

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Chỉnh sửa ảnh: vẽ chú thích (bút màu, cỡ nét, tẩy, hoàn tác/làm lại) rồi lưu.
 *  - MODE_NEW (ảnh vừa chụp, kèm đường dẫn tạm): Lưu sẽ hỏi tên học sinh rồi thêm vào Kho ảnh.
 *  - MODE_EDIT (ảnh đã có trong Kho ảnh, kèm id): Lưu sẽ ghi đè lên đúng ảnh đó.
 */
class PhotoEditorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PATH = "path"
        const val EXTRA_ID = "id"
        const val MODE_NEW = "new"
        const val MODE_EDIT = "edit"
    }

    private lateinit var drawView: DrawingView
    private var mode = MODE_NEW
    private var editId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_editor)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        drawView = findViewById(R.id.drawView)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_NEW
        editId = intent.getStringExtra(EXTRA_ID)

        val bmp = if (mode == MODE_EDIT) editId?.let { GalleryStore.loadById(this, it) }
                  else intent.getStringExtra(EXTRA_PATH)?.let { BitmapFactory.decodeFile(it) }
        if (bmp == null) {
            Toast.makeText(this, "Không mở được ảnh", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        drawView.post { drawView.setPhoto(bmp) }

        findViewById<TextView>(R.id.editorTitle).text = if (mode == MODE_EDIT) "Chỉnh sửa ảnh" else "Ảnh vừa chụp"
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSaveEdit).setOnClickListener { onSaveClicked() }

        setupColorAndSize()
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { drawView.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { drawView.redo() }
        findViewById<ImageButton>(R.id.btnClearAll).setOnClickListener {
            if (!drawView.hasDrawing()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Xóa hết nét vẽ?")
                .setPositiveButton("Xóa") { _, _ -> drawView.clearAll() }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun setupColorAndSize() {
        val dm = resources.displayMetrics
        val btnEraser = findViewById<ImageButton>(R.id.btnEraser)
        val sizeBar = findViewById<SeekBar>(R.id.sizeBar)
        // Bút và Tẩy có cỡ riêng, kéo thanh để chỉnh — tẩy mặc định to hơn hẳn cho dễ xóa
        var penW = 9f
        var eraserW = 26f

        fun applyWidth() {
            if (drawView.eraseMode) drawView.eraserWidth = eraserW * dm.density else drawView.penWidth = penW * dm.density
        }
        fun refreshBar() {
            sizeBar.progress = ((if (drawView.eraseMode) eraserW else penW) - 2f).toInt().coerceIn(0, sizeBar.max)
        }
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val w = (progress + 2).toFloat()
                if (drawView.eraseMode) eraserW = w else penW = w
                applyWidth()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val swatches = mapOf(
            R.id.swBlack to 0xFF111827.toInt(), R.id.swRed to 0xFFE5484D.toInt(),
            R.id.swBlue to 0xFF2B5FE8.toInt(), R.id.swGreen to 0xFF22B566.toInt(), R.id.swYellow to 0xFFF5B400.toInt()
        )
        val swatchViews = swatches.keys.map { findViewById<View>(it) }
        fun pickColor(id: Int, c: Int) {
            drawView.color = c; drawView.eraseMode = false
            swatchViews.forEach { it.isSelected = it.id == id }
            btnEraser.isSelected = false
            applyWidth(); refreshBar()
        }
        swatches.forEach { (id, c) -> findViewById<View>(id).setOnClickListener { pickColor(id, c) } }

        btnEraser.setOnClickListener {
            drawView.eraseMode = !drawView.eraseMode
            btnEraser.isSelected = drawView.eraseMode
            if (drawView.eraseMode) swatchViews.forEach { it.isSelected = false }
            applyWidth(); refreshBar()
        }

        pickColor(R.id.swRed, 0xFFE5484D.toInt())
    }

    private fun onSaveClicked() {
        val result = drawView.exportBitmap()
        if (mode == MODE_EDIT) {
            val id = editId ?: return
            GalleryStore.updateBitmap(this, id, result)
            Toast.makeText(this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Ảnh vừa chụp: hỏi tên học sinh trước khi thêm vào Kho ảnh
        val input = EditText(this).apply {
            hint = "Ví dụ: Nguyễn Văn A"
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        AlertDialog.Builder(this)
            .setTitle("Lưu vào Kho ảnh")
            .setMessage("Nhập tên học sinh cho ảnh này:")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                GalleryStore.add(this, result, input.text.toString().trim())
                Toast.makeText(this, "Đã lưu vào Kho ảnh", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
