package vn.maychieu.app

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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

        findViewById<ImageButton>(R.id.btnEraser).setOnClickListener {
            drawView.eraseMode = !drawView.eraseMode
            it.isSelected = drawView.eraseMode
        }
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
        val swatches = mapOf(
            R.id.swBlack to 0xFF111827.toInt(), R.id.swRed to 0xFFE5484D.toInt(),
            R.id.swBlue to 0xFF2B5FE8.toInt(), R.id.swGreen to 0xFF22B566.toInt(), R.id.swYellow to 0xFFF5B400.toInt()
        )
        val views = swatches.keys.map { findViewById<View>(it) }
        fun pick(id: Int, c: Int) {
            drawView.color = c; drawView.eraseMode = false
            views.forEach { it.isSelected = it.id == id }
            findViewById<ImageButton>(R.id.btnEraser).isSelected = false
        }
        swatches.forEach { (id, c) -> findViewById<View>(id).setOnClickListener { pick(id, c) } }
        pick(R.id.swRed, 0xFFE5484D.toInt())

        val dm = resources.displayMetrics
        val sizes = mapOf(R.id.sizeS to 4f, R.id.sizeM to 9f, R.id.sizeL to 18f)
        val sizeBtns = sizes.keys.map { findViewById<Button>(it) }
        fun pickSize(id: Int, w: Float) {
            drawView.strokeWidth = w * dm.density
            sizeBtns.forEach { it.isSelected = it.id == id }
        }
        sizes.forEach { (id, w) -> findViewById<Button>(id).setOnClickListener { pickSize(id, w) } }
        pickSize(R.id.sizeM, 9f)
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
