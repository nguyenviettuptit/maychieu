package vn.maychieu.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Máy Chiếu Vật Thể — camera chiếu bài làm lên TV.
 * Dùng CameraX (thư viện camera chính thức của Google): đi thẳng vào camera của máy như app camera gốc,
 * zoom bằng cảm biến, lấy nét tự động / chạm để lấy nét.
 */
class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var frozenView: ImageView
    private lateinit var focusRing: View
    private lateinit var freezeBadge: TextView
    private lateinit var panel: ScrollView
    private lateinit var toolbar: LinearLayout
    private lateinit var btnHide: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var toastView: TextView
    private lateinit var info: TextView
    private lateinit var brightBar: SeekBar
    private lateinit var rotLabel: TextView
    private lateinit var btnFreeze: Button
    private lateinit var btnMirror: Button
    private lateinit var btnTorch: Button
    private lateinit var btnFit: Button
    private lateinit var btnFill: Button

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private val worker = Executors.newSingleThreadExecutor()

    private val prefs by lazy { getSharedPreferences("maychieu", MODE_PRIVATE) }
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var rotationDeg = 0
    private var mirrored = false
    private var fill = false
    private var torchOn = false
    private var frozen = false
    private var uiHidden = false

    /* Hướng màn hình của app (cũng là hướng hiện trên TV khi chiếu Smart View).
       Khóa cứng theo nút bấm, không xoay theo cảm biến: lắc điện thoại không làm đổi hướng. */
    private val orients = intArrayOf(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    )
    private val orientNames = arrayOf("Ngang", "Ngang (lật ngược)", "Dọc", "Dọc (lật ngược)")
    private val orientShort = arrayOf("Ngang", "Ngang ↻", "Dọc", "Dọc ↻")
    private var orientIdx = 0
    private var displayListener: DisplayManager.DisplayListener? = null

    // Hình đóng băng + phóng to / kéo trên hình đó
    private var frozenBitmap: Bitmap? = null
    private var fZoom = 1f
    private var fPanX = 0f
    private var fPanY = 0f

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCameraPermission()) startCamera()
            else showToast("Cần cho phép dùng camera để chiếu", 3000)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!License.isActivated(this)) {   // chưa kích hoạt thì không cho vào màn hình chiếu
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // màn hình luôn sáng khi đang chiếu
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        root = findViewById(R.id.root)
        stage = findViewById(R.id.stage)
        previewView = findViewById(R.id.previewView)
        frozenView = findViewById(R.id.frozenView)
        focusRing = findViewById(R.id.focusRing)
        freezeBadge = findViewById(R.id.freezeBadge)
        panel = findViewById(R.id.panel)
        toolbar = findViewById(R.id.toolbar)
        btnHide = findViewById(R.id.btnHide)
        btnSettings = findViewById(R.id.btnSettings)
        toastView = findViewById(R.id.toast)
        info = findViewById(R.id.info)
        brightBar = findViewById(R.id.brightBar)
        rotLabel = findViewById(R.id.rotLabel)
        btnFreeze = findViewById(R.id.btnFreeze)
        btnMirror = findViewById(R.id.btnMirror)
        btnTorch = findViewById(R.id.btnTorch)
        btnFit = findViewById(R.id.btnFit)
        btnFill = findViewById(R.id.btnFill)

        // TextureView: cho phép xoay / lật hình bằng phép biến đổi khung
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        rotationDeg = prefs.getInt("rotation", 0)
        mirrored = prefs.getBoolean("mirror", false)
        fill = prefs.getBoolean("fill", false)
        lensFacing = prefs.getInt("lens", CameraSelector.LENS_FACING_BACK)
        orientIdx = prefs.getInt("orient", 0).coerceIn(0, orients.size - 1)
        requestedOrientation = orients[orientIdx]

        // Lật 180° (ngang ↔ ngang ngược) không báo onConfigurationChanged → theo dõi màn hình để camera xoay đúng
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { checkCast(true) }
            override fun onDisplayRemoved(displayId: Int) { checkCast(true) }
            override fun onDisplayChanged(displayId: Int) { updateTargetRotation() }
        }
        displayListener = listener
        dm.registerDisplayListener(listener, null)

        setupButtons()
        setupGestures()
        setupHideButton()
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyTransform() }

        if (hasCameraPermission() && (Build.VERSION.SDK_INT > 28 || hasWritePermission())) startCamera()
        else {
            val need = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= 28) need += Manifest.permission.WRITE_EXTERNAL_STORAGE
            permLauncher.launch(need.toTypedArray())
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasWritePermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        checkCast(true)   // quay lại app sau khi chọn TV: báo đã kết nối
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        displayListener?.let { (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it) }
        worker.shutdown()
    }

    /* ---------------- Chiếu lên TV (Smart View / Screen Cast có sẵn của điện thoại) ----------------
       Không cần máy chủ: điện thoại gửi hình thẳng tới TV qua Wi-Fi (Miracast / Smart View). */

    private var castConnected = false

    /** Mở bảng chọn TV của điện thoại: ưu tiên Smart View (Samsung), rồi tới Cast của Android. */
    private fun openCast() {
        if (CastHelper.open(this)) showToast("Chọn TV trong danh sách để chiếu", 2500)
        else showToast("Vuốt 2 ngón từ trên xuống → bấm Smart View → chọn TV", 5000)
    }

    /** Phát hiện TV đã kết nối (màn hình phụ) để đổi màu nút và gợi ý ẩn nút. */
    private fun checkCast(announce: Boolean) {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val now = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()
        if (now == castConnected) return
        castConnected = now
        findViewById<ImageButton>(R.id.btnCast).isSelected = now
        if (announce) {
            if (now) showToast("Đã chiếu lên TV · bấm nút con mắt để TV chỉ hiện hình", 4000)
            else showToast("Đã ngắt chiếu TV", 2000)
        }
    }

    private fun updateTargetRotation() {
        root.post {
            val rot = previewView.display?.rotation ?: return@post
            preview?.targetRotation = rot
            imageCapture?.targetRotation = rot
            applyTransform()
        }
    }

    private fun setOrientation(idx: Int) {
        orientIdx = idx % orients.size
        prefs.edit().putInt("orient", orientIdx).apply()
        requestedOrientation = orients[orientIdx]
        rotLabel.text = orientShort[orientIdx]
    }

    /* ---------------- Camera ---------------- */

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            provider = f.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return
        if (!p.hasCamera(CameraSelector.Builder().requireLensFacing(lensFacing).build())) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        }
        val rot = previewView.display?.rotation ?: Surface.ROTATION_0

        // Khung xem 16:9 Full HD (vừa TV), ảnh chụp ở độ phân giải cao nhất của cảm biến
        val previewRes = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
        val pv = Preview.Builder().setResolutionSelector(previewRes).setTargetRotation(rot).build()
        pv.setSurfaceProvider(previewView.surfaceProvider)

        val capRes = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()
        val ic = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(capRes)
            .setTargetRotation(rot)
            .build()

        try {
            p.unbindAll()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val cam = p.bindToLifecycle(this, selector, pv, ic)
            camera = cam
            preview = pv
            imageCapture = ic
            if (!cam.cameraInfo.hasFlashUnit()) torchOn = false
            cam.cameraControl.enableTorch(torchOn)
            setupBrightness(cam)
            cam.cameraInfo.zoomState.observe(this) { updateInfo() }
            previewView.postDelayed({ updateInfo() }, 800)
            syncButtons()
        } catch (e: Exception) {
            showToast("Không mở được camera: ${e.message}", 3500)
        }
    }

    private fun setupBrightness(cam: Camera) {
        val ex = cam.cameraInfo.exposureState
        if (!ex.isExposureCompensationSupported) {
            brightBar.isEnabled = false
            return
        }
        brightBar.isEnabled = true
        val r = ex.exposureCompensationRange
        brightBar.max = r.upper - r.lower
        val saved = prefs.getInt("exposure", 0).coerceIn(r.lower, r.upper)
        brightBar.progress = saved - r.lower
        cam.cameraControl.setExposureCompensationIndex(saved)
        brightBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val idx = progress + r.lower
                camera?.cameraControl?.setExposureCompensationIndex(idx)
                prefs.edit().putInt("exposure", idx).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun updateInfo() {
        val pr = preview?.resolutionInfo?.resolution
        val cr = imageCapture?.resolutionInfo?.resolution
        val z = camera?.cameraInfo?.zoomState?.value
        val name = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Camera sau" else "Camera trước"
        info.text = buildString {
            append(name)
            if (pr != null) append(" · khung xem ${pr.width}×${pr.height}")
            if (cr != null) append(" · ảnh chụp ${cr.width}×${cr.height}")
            if (z != null) append(" · zoom ${"%.1f".format(z.zoomRatio)}× / tối đa ${"%.0f".format(z.maxZoomRatio)}×")
        }
    }

    /* ---------------- Xoay, lật, vừa / lấp đầy ---------------- */

    private fun applyTransform() {
        val w = root.width
        val h = root.height
        if (w == 0 || h == 0) return
        val sideways = rotationDeg % 180 != 0
        val tw = if (sideways) h else w
        val th = if (sideways) w else h
        val lp = stage.layoutParams as FrameLayout.LayoutParams
        if (lp.width != tw || lp.height != th) {
            lp.width = tw
            lp.height = th
            lp.gravity = Gravity.CENTER
            stage.layoutParams = lp
        }
        stage.rotation = rotationDeg.toFloat()
        stage.scaleX = if (mirrored) -1f else 1f
        val st = if (fill) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
        if (previewView.scaleType != st) previewView.scaleType = st
        rotLabel.text = orientShort[orientIdx]
        findViewById<Button>(R.id.btnRotImg).text = if (rotationDeg == 0) "Xoay hình 90°" else "Xoay hình 90° · đang $rotationDeg°"

        val maxPanel = (400 * resources.displayMetrics.density).toInt()
        val plp = panel.layoutParams as FrameLayout.LayoutParams
        val want = min(maxPanel, w - (20 * resources.displayMetrics.density).toInt())
        if (plp.width != want) {
            plp.width = want
            panel.layoutParams = plp
        }
        // bảng cài đặt cao tối đa ~45% màn hình để vẫn thấy hình bên dưới
        val maxH = (h * 0.45f).toInt()
        if (panel.height > maxH) {
            plp.height = maxH
            panel.layoutParams = plp
        }
        stage.post { updateFrozenMatrix() }
    }

    /* ---------------- Cử chỉ: chụm zoom, chạm lấy nét, chạm 2 lần ---------------- */

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                if (frozen) {
                    zoomFrozen(d.scaleFactor, d.focusX, d.focusY)
                } else {
                    // Zoom bằng chính camera (cắt từ cảm biến) — nét như app camera gốc
                    val zs = camera?.cameraInfo?.zoomState?.value ?: return true
                    val nz = (zs.zoomRatio * d.scaleFactor).coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(nz)
                }
                return true
            }
        })
        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (panel.visibility == View.VISIBLE) {
                    showPanel(false)
                    return true
                }
                if (!frozen) focusAt(e.x, e.y, e.rawX, e.rawY)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (frozen) {
                    if (fZoom > 1.05f) {
                        fZoom = 1f; fPanX = 0f; fPanY = 0f
                        updateFrozenMatrix()
                    } else zoomFrozen(2.5f, e.x, e.y)
                } else {
                    val zs = camera?.cameraInfo?.zoomState?.value ?: return true
                    camera?.cameraControl?.setZoomRatio(if (zs.zoomRatio > 1.1f) 1f else min(2.5f, zs.maxZoomRatio))
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (frozen && !scale.isInProgress) {
                    fPanX -= distanceX
                    fPanY -= distanceY
                    updateFrozenMatrix()
                }
                return true
            }
        })
        stage.setOnTouchListener { _, ev ->
            scale.onTouchEvent(ev)
            gesture.onTouchEvent(ev)
            true
        }
    }

    private fun focusAt(x: Float, y: Float, rawX: Float, rawY: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)   // sau 4 giây quay lại tự lấy nét liên tục
            .build()
        showRing(rawX, rawY, done = false, ok = true)
        val fut = cam.cameraControl.startFocusAndMetering(action)
        fut.addListener({
            val ok = try { fut.get().isFocusSuccessful } catch (_: Exception) { false }
            showRing(rawX, rawY, done = true, ok = ok)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showRing(rawX: Float, rawY: Float, done: Boolean, ok: Boolean) {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        focusRing.visibility = View.VISIBLE
        focusRing.x = rawX - loc[0] - focusRing.width / 2f
        focusRing.y = rawY - loc[1] - focusRing.height / 2f
        focusRing.animate().cancel()
        if (!done) {
            focusRing.setBackgroundResource(R.drawable.focus_ring)
            focusRing.alpha = 1f
            focusRing.scaleX = 1.4f
            focusRing.scaleY = 1.4f
            focusRing.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
        } else {
            focusRing.setBackgroundResource(if (ok) R.drawable.focus_ring_ok else R.drawable.focus_ring)
            focusRing.animate().alpha(0f).setStartDelay(600).setDuration(300).start()
        }
    }

    /* ---------------- Đóng băng: giữ hình + chụp ảnh nét cao thay vào ---------------- */

    private fun setFrozen(on: Boolean) {
        if (on == frozen) return
        if (on) {
            val snap = previewView.bitmap
            if (snap == null) {
                showToast("Chưa có hình để đóng băng")
                return
            }
            frozen = true
            frozenBitmap = snap
            fZoom = 1f; fPanX = 0f; fPanY = 0f
            frozenView.setImageBitmap(snap)
            frozenView.visibility = View.VISIBLE
            updateFrozenMatrix()
            freezeBadge.text = "ĐANG ĐÓNG BĂNG"
            freezeBadge.visibility = if (uiHidden) View.GONE else View.VISIBLE
            captureBitmap { bmp ->
                if (frozen && bmp != null) {
                    frozenBitmap = bmp
                    frozenView.setImageBitmap(bmp)
                    updateFrozenMatrix()
                    freezeBadge.text = "ĐÓNG BĂNG · ẢNH NÉT CAO ${bmp.width}×${bmp.height}"
                } else if (frozen) freezeBadge.text = "ĐANG ĐÓNG BĂNG"
            }
            freezeBadge.text = "ĐANG CHỤP ẢNH NÉT CAO…"
        } else {
            frozen = false
            frozenView.visibility = View.GONE
            frozenView.setImageDrawable(null)
            frozenBitmap = null
            freezeBadge.visibility = View.GONE
        }
        syncButtons()
    }

    /** Chụp 1 ảnh độ phân giải cao (đúng chiều, camera trước được lật như khung xem). */
    private fun captureBitmap(done: (Bitmap?) -> Unit) {
        val ic = imageCapture ?: return done(null)
        val front = lensFacing == CameraSelector.LENS_FACING_FRONT
        ic.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rot = image.imageInfo.rotationDegrees
                val raw = try { image.toBitmap() } catch (_: Throwable) { null }
                image.close()
                var out: Bitmap? = null
                if (raw != null) {
                    var b: Bitmap = raw
                    val maxSide = 4096
                    if (max(b.width, b.height) > maxSide) {
                        val s = maxSide.toFloat() / max(b.width, b.height)
                        b = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
                    }
                    if (rot != 0 || front) {
                        val m = Matrix()
                        m.postRotate(rot.toFloat())
                        if (front) m.postScale(-1f, 1f)
                        b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                    }
                    out = b
                }
                runOnUiThread { done(out) }
            }

            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { done(null) }
            }
        })
    }

    private fun updateFrozenMatrix() {
        val bmp = frozenBitmap ?: return
        val vw = stage.width.toFloat()
        val vh = stage.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val base = if (fill) max(vw / bmp.width, vh / bmp.height) else min(vw / bmp.width, vh / bmp.height)
        val s = base * fZoom
        val dw = bmp.width * s
        val dh = bmp.height * s
        val mx = max(0f, (dw - vw) / 2f)
        val my = max(0f, (dh - vh) / 2f)
        fPanX = fPanX.coerceIn(-mx, mx)
        fPanY = fPanY.coerceIn(-my, my)
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate((vw - dw) / 2f + fPanX, (vh - dh) / 2f + fPanY)
        frozenView.imageMatrix = m
    }

    private fun zoomFrozen(factor: Float, fx: Float, fy: Float) {
        val nz = (fZoom * factor).coerceIn(1f, 8f)
        val k = nz / fZoom
        val cx = stage.width / 2f
        val cy = stage.height / 2f
        fPanX = (fx - cx) - ((fx - cx) - fPanX) * k
        fPanY = (fy - cy) - ((fy - cy) - fPanY) * k
        fZoom = nz
        updateFrozenMatrix()
    }

    /* ---------------- Lưu ảnh ---------------- */

    private fun savePhoto() {
        val name = "MayChieu_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val current = frozenBitmap
        if (frozen && current != null) {
            saveBitmap(current, name)
            return
        }
        showToast("Đang chụp…")
        captureBitmap { bmp ->
            if (bmp == null) showToast("Không chụp được ảnh") else saveBitmap(bmp, name)
        }
    }

    /** Lưu ảnh vào Thư viện › MayChieu, theo đúng chiều xoay / lật đang chiếu. */
    private fun saveBitmap(src: Bitmap, name: String) {
        val rot = rotationDeg
        val mir = mirrored
        worker.execute {
            try {
                var b = src
                if (rot != 0 || mir) {
                    val m = Matrix()
                    if (mir) m.postScale(-1f, 1f)
                    m.postRotate(rot.toFloat())
                    b = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MayChieu")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("insert")
                contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                runOnUiThread { showToast("Đã lưu ảnh vào Thư viện › MayChieu") }
            } catch (_: Exception) {
                runOnUiThread { showToast("Không lưu được ảnh") }
            }
        }
    }

    /* ---------------- Nút ---------------- */

    private fun setupButtons() {
        btnSettings.setOnClickListener { showPanel(panel.visibility != View.VISIBLE) }
        findViewById<ImageButton>(R.id.panelClose).setOnClickListener { showPanel(false) }

        findViewById<ImageButton>(R.id.btnSwitch).setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
            prefs.edit().putInt("lens", lensFacing).apply()
            setFrozen(false)
            torchOn = false
            bindCamera()
            showToast(if (lensFacing == CameraSelector.LENS_FACING_BACK) "Camera sau" else "Camera trước")
        }

        // Nút Xoay trên thanh công cụ: đổi hướng màn hình app (TV hiện theo) — không cần lắc điện thoại
        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            setOrientation(orientIdx + 1)
            showToast("Màn hình: ${orientNames[orientIdx]}")
        }
        // Trong Cài đặt: xoay riêng hình camera 90° (khi trang vở hiện bị nghiêng)
        findViewById<Button>(R.id.btnRotImg).setOnClickListener {
            rotationDeg = (rotationDeg + 90) % 360
            prefs.edit().putInt("rotation", rotationDeg).apply()
            applyTransform()
            showToast("Xoay hình $rotationDeg°")
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { savePhoto() }
        findViewById<ImageButton>(R.id.btnCast).setOnClickListener {
            if (castConnected) showToast("Đang chiếu lên TV · muốn ngắt: vuốt xuống, bấm Smart View → Ngắt kết nối", 4000)
            else openCast()
        }
        checkCast(false)

        btnFreeze.setOnClickListener { setFrozen(!frozen) }
        btnMirror.setOnClickListener {
            mirrored = !mirrored
            prefs.edit().putBoolean("mirror", mirrored).apply()
            applyTransform(); syncButtons()
        }
        btnTorch.setOnClickListener {
            val cam = camera ?: return@setOnClickListener
            if (!cam.cameraInfo.hasFlashUnit()) {
                showToast("Camera này không có đèn")
                return@setOnClickListener
            }
            torchOn = !torchOn
            cam.cameraControl.enableTorch(torchOn)
            syncButtons()
        }
        btnFit.setOnClickListener { setFill(false) }
        btnFill.setOnClickListener { setFill(true) }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            rotationDeg = 0; mirrored = false; fill = false; torchOn = false
            setOrientation(0)   // về màn hình ngang (vừa TV)
            prefs.edit().putInt("rotation", 0).putBoolean("mirror", false).putBoolean("fill", false)
                .putInt("exposure", 0).apply()
            setFrozen(false)
            camera?.cameraControl?.let {
                it.enableTorch(false)
                it.setZoomRatio(1f)
                it.setExposureCompensationIndex(0)
                it.cancelFocusAndMetering()   // quay lại tự lấy nét liên tục
            }
            camera?.cameraInfo?.exposureState?.let { brightBar.progress = -it.exposureCompensationRange.lower }
            applyTransform(); syncButtons()
            showToast("Đã đặt lại cài đặt khuyên dùng")
        }
    }

    private fun setFill(on: Boolean) {
        fill = on
        prefs.edit().putBoolean("fill", fill).apply()
        applyTransform(); syncButtons()
    }

    private fun showPanel(show: Boolean) {
        panel.visibility = if (show) View.VISIBLE else View.GONE
        btnSettings.isSelected = show
        if (show) {
            updateInfo()
            panel.post { applyTransform() }
        }
    }

    private fun syncButtons() {
        btnFreeze.isSelected = frozen
        btnFreeze.text = if (frozen) "Tiếp tục" else "Đóng băng"
        btnMirror.isSelected = mirrored
        btnTorch.isSelected = torchOn
        btnTorch.isEnabled = camera?.cameraInfo?.hasFlashUnit() ?: true
        btnFit.isSelected = !fill
        btnFill.isSelected = fill
    }

    /* ---------------- Nút tròn nhỏ: ẩn hết / hiện lại (kéo để đổi chỗ) ---------------- */

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHideButton() {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var moved = false
        btnHide.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = v.x; startY = v.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (moved || hypot(dx, dy) > 16f) {
                        moved = true
                        v.x = (startX + dx).coerceIn(0f, (root.width - v.width).toFloat())
                        v.y = (startY + dy).coerceIn(0f, (root.height - v.height).toFloat())
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggleUi()
            }
            true
        }
    }

    private fun toggleUi() {
        uiHidden = !uiHidden
        toolbar.visibility = if (uiHidden) View.GONE else View.VISIBLE
        if (uiHidden) showPanel(false)
        freezeBadge.visibility = if (frozen && !uiHidden) View.VISIBLE else View.GONE
        btnHide.setImageResource(if (uiHidden) R.drawable.ic_eye_off else R.drawable.ic_eye)
        btnHide.alpha = if (uiHidden) 0.35f else 1f
        hideSystemBars()
    }

    /* ---------------- Xoay điện thoại ---------------- */

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        btnHide.translationX = 0f
        btnHide.translationY = 0f
        updateTargetRotation()
    }

    private fun showToast(msg: String, ms: Long = 1300) {
        toastView.text = msg
        toastView.animate().cancel()
        toastView.alpha = 1f
        toastView.animate().alpha(0f).setStartDelay(ms).setDuration(250).start()
    }
}
