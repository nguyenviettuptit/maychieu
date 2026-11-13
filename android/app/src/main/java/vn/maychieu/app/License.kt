package vn.maychieu.app

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bản quyền: mỗi link bán (mã kích hoạt) gắn với 1 điện thoại.
 * Chỉ cần Internet 1 lần lúc kích hoạt; sau đó dùng vĩnh viễn không cần mạng.
 */
object License {

    /** Địa chỉ máy chủ link bán (Cloudflare Worker). */
    val SERVER: String get() = BuildConfig.ACTIVATION_URL

    private const val PREFS = "license"

    /** Mã máy: cố định theo điện thoại (giữ nguyên khi gỡ / cài lại app). */
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun isActivated(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString("token", null) != null && p.getString("device", null) == deviceId(context)
    }

    fun clean(code: String): String = code.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** Gọi máy chủ để kích hoạt. Chạy ở luồng nền. Trả về (thành công, thông báo). */
    fun activate(context: Context, rawCode: String): Pair<Boolean, String> {
        val code = clean(rawCode)
        if (code.length != 8) return false to "Mã kích hoạt gồm 8 ký tự, ví dụ ABCD-2345."
        val device = deviceId(context)
        return try {
            val conn = (URL("$SERVER/api/activate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("content-type", "application/json")
            }
            conn.outputStream.use { it.write(JSONObject().put("token", code).put("device", device).toString().toByteArray()) }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            conn.disconnect()
            val j = JSONObject(body)
            if (j.optBoolean("ok")) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("token", code).putString("device", device).apply()
                true to j.optString("message", "Kích hoạt thành công.")
            } else {
                false to j.optString("message", "Không kích hoạt được (mã lỗi $status).")
            }
        } catch (_: Exception) {
            false to "Không kết nối được máy chủ. Hãy bật Wi-Fi hoặc 4G rồi thử lại."
        }
    }
}
