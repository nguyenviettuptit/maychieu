package vn.maychieu.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings

/**
 * Chiếu lên TV bằng tính năng có sẵn của điện thoại (Smart View của Samsung / Cast của Android).
 * Không cần máy chủ: điện thoại gửi hình thẳng tới TV qua Wi-Fi.
 */
object CastHelper {

    /** Mở bảng chọn TV. Trả về false nếu máy không có màn hình chọn TV nào để mở. */
    fun open(activity: Activity): Boolean {
        val pm = activity.packageManager
        val candidates = mutableListOf<Intent>()
        pm.getLaunchIntentForPackage("com.samsung.android.smartmirroring")?.let { candidates += it }
        candidates += Intent("android.settings.CAST_SETTINGS")
        candidates += Intent(Settings.ACTION_WIRELESS_SETTINGS)
        for (i in candidates) {
            try {
                activity.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    /** Tên TV đang được chiếu tới (màn hình phụ), hoặc null nếu chưa kết nối. */
    fun connectedName(context: Context): String? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull() ?: return null
        return d.name ?: "TV"
    }
}
