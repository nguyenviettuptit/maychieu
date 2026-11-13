package vn.maychieu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Kho ảnh: lưu ảnh bài làm đã chụp, có tên học sinh, trong bộ nhớ riêng của app
 * (không dùng Thư viện ảnh hệ thống) để đổi tên / xóa / chỉnh sửa được ngay trong app.
 */
data class GalleryItem(val id: String, val name: String, val fileName: String, val created: Long)

object GalleryStore {
    private const val INDEX = "gallery_index.json"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, "gallery")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun indexFile(context: Context) = File(dir(context), INDEX)

    @Synchronized
    fun list(context: Context): List<GalleryItem> {
        val f = indexFile(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                GalleryItem(o.getString("id"), o.getString("name"), o.getString("fileName"), o.getLong("created"))
            }.sortedByDescending { it.created }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    private fun saveIndex(context: Context, items: List<GalleryItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("fileName", it.fileName).put("created", it.created))
        }
        indexFile(context).writeText(arr.toString())
    }

    /** Thêm ảnh mới vào kho, trả về mục đã lưu. */
    fun add(context: Context, bitmap: Bitmap, name: String): GalleryItem {
        val id = System.currentTimeMillis().toString() + (1000..9999).random()
        val fileName = "$id.jpg"
        FileOutputStream(File(dir(context), fileName)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val item = GalleryItem(id, name.ifBlank { "Không tên" }, fileName, System.currentTimeMillis())
        saveIndex(context, list(context) + item)
        return item
    }

    /** Ghi đè ảnh đã có (sau khi vẽ thêm / chỉnh sửa). */
    fun updateBitmap(context: Context, id: String, bitmap: Bitmap) {
        val item = list(context).find { it.id == id } ?: return
        FileOutputStream(File(dir(context), item.fileName)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    fun rename(context: Context, id: String, newName: String) {
        if (newName.isBlank()) return
        saveIndex(context, list(context).map { if (it.id == id) it.copy(name = newName) else it })
    }

    fun delete(context: Context, ids: Set<String>) {
        val items = list(context)
        items.filter { it.id in ids }.forEach { File(dir(context), it.fileName).delete() }
        saveIndex(context, items.filterNot { it.id in ids })
    }

    fun load(context: Context, item: GalleryItem): Bitmap? =
        try { BitmapFactory.decodeFile(File(dir(context), item.fileName).absolutePath) } catch (_: Exception) { null }

    fun loadById(context: Context, id: String): Bitmap? = list(context).find { it.id == id }?.let { load(context, it) }

    /** Ảnh thu nhỏ, đỡ tốn bộ nhớ khi hiện cả lưới nhiều ảnh. */
    fun loadThumbnail(context: Context, item: GalleryItem, reqSize: Int = 360): Bitmap? {
        return try {
            val path = File(dir(context), item.fileName).absolutePath
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqSize && bounds.outHeight / (sample * 2) >= reqSize) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }
}
