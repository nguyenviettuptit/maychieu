package vn.maychieu.app

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Màn hình chính: 2 nút lớn
 *  - Kết nối TV: mở Smart View để chọn TV
 *  - Sử dụng chiếu: mở camera để chiếu
 * Có ô trạng thái cho biết đã kết nối TV hay chưa.
 */
class HomeActivity : ComponentActivity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var cardConnect: LinearLayout
    private lateinit var connectTitle: TextView
    private lateinit var connectSub: TextView
    private lateinit var connectIconBg: FrameLayout
    private lateinit var connectIcon: ImageView
    private var listener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        cardConnect = findViewById(R.id.cardConnect)
        connectTitle = findViewById(R.id.connectTitle)
        connectSub = findViewById(R.id.connectSub)
        connectIconBg = findViewById(R.id.connectIconBg)
        connectIcon = findViewById(R.id.connectIcon)

        cardConnect.setOnClickListener {
            if (CastHelper.open(this)) {
                Toast.makeText(this, "Chọn TV trong danh sách, rồi quay lại app", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Vuốt 2 ngón từ trên xuống → bấm Smart View → chọn TV", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<LinearLayout>(R.id.cardStart).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        val version = try {
            if (Build.VERSION.SDK_INT >= 33)
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { null }
        findViewById<TextView>(R.id.versionText).text = "Phiên bản ${version ?: ""} · không cần Internet"

        // Cập nhật trạng thái ngay khi TV kết nối / ngắt
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = updateStatus()
            override fun onDisplayRemoved(displayId: Int) = updateStatus()
            override fun onDisplayChanged(displayId: Int) = updateStatus()
        }
        listener = l
        dm.registerDisplayListener(l, null)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.let { (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it) }
    }

    private fun updateStatus() {
        val name = CastHelper.connectedName(this)
        if (name != null) {
            statusDot.setBackgroundResource(R.drawable.dot_on)
            statusText.text = "Đã kết nối: $name"
            cardConnect.setBackgroundResource(R.drawable.bg_connect_on)
            connectIconBg.setBackgroundResource(R.drawable.bg_circle_green)
            connectIcon.setColorFilter(0xFF22B566.toInt())
            connectTitle.text = "Đã kết nối TV"
            connectSub.text = "Bấm để đổi TV hoặc ngắt kết nối"
        } else {
            statusDot.setBackgroundResource(R.drawable.dot_off)
            statusText.text = "Chưa kết nối TV"
            cardConnect.setBackgroundResource(R.drawable.bg_connect)
            connectIconBg.setBackgroundResource(R.drawable.bg_circle_light)
            connectIcon.setColorFilter(0xFF2B5FE8.toInt())
            connectTitle.text = "Kết nối TV"
            connectSub.text = "Chọn TV để chiếu màn hình (Smart View)"
        }
    }
}
