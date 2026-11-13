package vn.maychieu.app

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
            if (License.isActivated(this)) startActivity(Intent(this, MainActivity::class.java))
            else showLicenseState()
        }
        findViewById<LinearLayout>(R.id.cardGallery).setOnClickListener {
            if (License.isActivated(this)) startActivity(Intent(this, GalleryActivity::class.java))
            else showLicenseState()
        }
        findViewById<LinearLayout>(R.id.cardCompare).setOnClickListener {
            if (License.isActivated(this)) startActivity(Intent(this, CompareActivity::class.java))
            else showLicenseState()
        }

        // Kích hoạt bản quyền
        activateBtn = findViewById(R.id.activateBtn)
        codeInput = findViewById(R.id.codeInput)
        activateMsg = findViewById(R.id.activateMsg)
        activateBtn.setOnClickListener { doActivate(codeInput.text.toString()) }
        showLicenseState()
        handleActivationLink(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleActivationLink(intent)
    }

    /* ---------- Kích hoạt ---------- */

    private lateinit var activateBtn: Button
    private lateinit var codeInput: EditText
    private lateinit var activateMsg: TextView

    /** Mở từ nút "Kích hoạt app" trên trang tải: maychieu://activate?t=MÃ → tự điền mã và kích hoạt. */
    private fun handleActivationLink(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme != "maychieu") return
        val code = data.getQueryParameter("t") ?: return
        if (License.isActivated(this)) {
            Toast.makeText(this, "Máy này đã được kích hoạt", Toast.LENGTH_SHORT).show()
            return
        }
        codeInput.setText(code)
        doActivate(code)
    }

    /** Chưa kích hoạt: hiện ô nhập mã, ẩn 2 nút. Đã kích hoạt: hiện 2 nút như bình thường. */
    private fun showLicenseState() {
        val ok = License.isActivated(this)
        findViewById<View>(R.id.activateCard).visibility = if (ok) View.GONE else View.VISIBLE
        cardConnect.visibility = if (ok) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardStart).visibility = if (ok) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardGallery).visibility = if (ok) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardCompare).visibility = if (ok) View.VISIBLE else View.GONE
    }

    private fun doActivate(code: String) {
        activateBtn.isEnabled = false
        activateBtn.text = "Đang kích hoạt…"
        activateMsg.setTextColor(0xFF64748B.toInt())
        activateMsg.text = ""
        Thread {
            val (ok, msg) = License.activate(this, code)
            runOnUiThread {
                activateBtn.isEnabled = true
                activateBtn.text = "Kích hoạt"
                if (ok) {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    showLicenseState()
                } else {
                    activateMsg.setTextColor(0xFFE5484D.toInt())
                    activateMsg.text = msg
                }
            }
        }.start()
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
