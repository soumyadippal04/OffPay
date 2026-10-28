// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.features.qr_scanner.presentation

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.offpay.app.OffPayApplication
import com.offpay.app.R
import com.offpay.app.data.TransactionSource
import com.offpay.app.data.UPIData
import com.offpay.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.offpay.app.features.qr_scanner.domain.QRCodeParser
import com.offpay.app.features.qr_scanner.domain.messageFor
import com.offpay.app.helpers.SetupHelper
import com.offpay.app.helpers.TransactionDetector
import com.offpay.app.managers.PaymentDialer
import com.offpay.app.managers.SmsConfirmation
import com.offpay.app.payment.PaymentSessionManager
import com.offpay.app.ui.theme.savedColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    companion object {
        const val RESULT_CANCELLED = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_ERROR = 2
        const val RESULT_HANDOFF = 3
        private const val SCAN_ERROR_VISIBLE_MS = 3500L
        private const val GALLERY_MAX_EDGE_PX = 2048
        private const val DISABLED_CONTROL_ALPHA = 0.4f
        private const val STATE_HANDOFF = "qr_handoff"
        private const val STATE_CAMERA_REQUESTED = "qr_camera_requested"
        private const val STATE_GALLERY_PENDING = "qr_gallery_pending"
        private const val STATE_GALLERY_URI = "qr_gallery_uri"
        private const val STATE_CLIPBOARD_OWNED = "qr_clipboard_owned"
        private const val STATE_PAYMENT_ERROR = "qr_payment_error"
        private const val STATE_AUTOMATIC_VERIFICATION = "qr_automatic_verification"
        private const val CLIPBOARD_LABEL = "OffPay payee UPI ID"
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var scannerOverlay: View
    private lateinit var progressBar: View
    private lateinit var tvStatus: TextView
    private lateinit var tvScanError: TextView
    private lateinit var tvCameraHint: TextView
    private lateinit var btnTerminate: View
    private lateinit var topBar: View
    private lateinit var instructionsBox: View
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var bottomActionBar: View
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var isFlashOn = false
    private var cameraStarting = false
    private var cameraPermissionRequested = false
    private var galleryPending = false
    private var pendingGalleryUri: Uri? = null
    private var isProcessingQRCode = false
    private var hasDialedUSSD = false
    private var hasHandedOff = false
    private var paymentError = false
    private var didCopyVpa = false
    private var automaticVerification = false
    private var pendingSession: PaymentSessionManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showGalleryMode()
    }

    // The photo picker grants access only to the selected image; no storage permission is needed.
    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            galleryPending = false
            resumeScanning()
        } else {
            decodeQrFromGallery(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)
        SetupHelper.getScanToPayBlockedMessage(this)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELLED)
            finish()
            return
        }
        initializeViews()
        cameraExecutor = Executors.newSingleThreadExecutor()
        hasHandedOff = savedInstanceState?.getBoolean(STATE_HANDOFF) == true
        hasDialedUSSD = hasHandedOff
        cameraPermissionRequested = savedInstanceState?.getBoolean(STATE_CAMERA_REQUESTED) == true
        galleryPending = savedInstanceState?.getBoolean(STATE_GALLERY_PENDING) == true
        pendingGalleryUri = savedInstanceState?.getString(STATE_GALLERY_URI)?.let(Uri::parse)
        didCopyVpa = savedInstanceState?.getBoolean(STATE_CLIPBOARD_OWNED) == true
        paymentError = savedInstanceState?.getBoolean(STATE_PAYMENT_ERROR) == true
        automaticVerification = savedInstanceState?.getBoolean(STATE_AUTOMATIC_VERIFICATION) == true
        isProcessingQRCode = hasHandedOff || paymentError
        onBackPressedDispatcher.addCallback(this) { closeScanner() }
        when {
            hasHandedOff -> showPaymentGuide(confirmationGuideMessage())
            paymentError -> showPaymentGuide(R.string.error_payment_init_failed)
            pendingGalleryUri != null -> decodeQrFromGallery(requireNotNull(pendingGalleryUri))
            else -> checkPermissionsAndStartCamera()
        }
    }

    private fun initializeViews() {
        viewFinder = findViewById(R.id.viewFinder)
        scannerOverlay = findViewById(R.id.scannerOverlay)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvScanError = findViewById(R.id.tvScanError)
        tvCameraHint = findViewById(R.id.tvCameraHint)
        btnTerminate = findViewById(R.id.btnTerminate)
        topBar = findViewById(R.id.topBar)
        instructionsBox = findViewById(R.id.instructionsBox)
        btnFlash = findViewById(R.id.btnFlash)
        btnGallery = findViewById(R.id.btnGallery)
        bottomActionBar = findViewById(R.id.bottomActionBar)
        findViewById<View>(R.id.btnBack).setOnClickListener { closeScanner() }
        btnTerminate.setOnClickListener { closeScanner() }
        btnFlash.isEnabled = false
        btnFlash.alpha = DISABLED_CONTROL_ALPHA
        btnFlash.setOnClickListener {
            val currentCamera = camera ?: return@setOnClickListener
            if (!currentCamera.cameraInfo.hasFlashUnit()) return@setOnClickListener
            isFlashOn = !isFlashOn
            currentCamera.cameraControl.enableTorch(isFlashOn)
            btnFlash.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        }
        btnGallery.setOnClickListener {
            if (isProcessingQRCode || galleryPending) return@setOnClickListener
            galleryPending = true
            imageAnalyzer?.clearAnalyzer()
            galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        applyWindowInsets()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val gap = resources.getDimensionPixelSize(R.dimen.qr_chrome_gap)
            (instructionsBox.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top + gap
                instructionsBox.layoutParams = it
            }
            (topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top
                topBar.layoutParams = it
            }
            findViewById<View>(R.id.creatorCredit).let { credit ->
                (credit.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.bottomMargin = bars.bottom + gap
                    credit.layoutParams = it
                }
            }
            insets
        }
    }

    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    private fun canScan(): Boolean = isActivityAlive() && !isProcessingQRCode && !galleryPending

    private fun canAcceptQr(): Boolean = canScan() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun hasCameraExecutor(): Boolean = ::cameraExecutor.isInitialized && !cameraExecutor.isShutdown

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun checkPermissionsAndStartCamera() {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            showGalleryMode()
            if (!cameraPermissionRequested) {
                cameraPermissionRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showGalleryMode() {
        if (!::tvCameraHint.isInitialized || isProcessingQRCode) return
        viewFinder.visibility = View.GONE
        scannerOverlay.visibility = View.GONE
        tvCameraHint.visibility = View.VISIBLE
        bottomActionBar.visibility = View.VISIBLE
        btnFlash.isEnabled = false
        btnFlash.alpha = DISABLED_CONTROL_ALPHA
        isFlashOn = false
        camera?.cameraControl?.enableTorch(false)
        btnFlash.setImageResource(R.drawable.ic_flash_off)
    }

    private fun startCamera() {
        if (!canScan() || cameraStarting) return
        when {
            !hasCameraPermission() -> showGalleryMode()
            camera != null -> resumeScanning()
            else -> bindCamera()
        }
    }

    private fun bindCamera() {
        cameraStarting = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraStarting = false
            if (!canScan()) return@addListener
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalyzer = analyzer
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                resumeScanning()
            } catch (exc: Exception) {
                Log.w("QRScanner", "Camera unavailable; gallery remains available", exc)
                camera = null
                showGalleryMode()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** All QR callbacks are serialized on the main thread so two sources cannot dial twice. */
    private fun processQRCode(qrCode: String) {
        runOnUiThread {
            if (!canAcceptQr() || hasDialedUSSD) return@runOnUiThread
            isProcessingQRCode = true
            imageAnalyzer?.clearAnalyzer()
            when (val result = QRCodeParser.parse(qrCode)) {
                is QRCodeParser.ParseResult.Valid -> proceedWithPayment(result.data)
                is QRCodeParser.ParseResult.Invalid -> showInvalidQrCode(result.reason.messageFor(this))
            }
        }
    }

    private fun proceedWithPayment(upiData: UPIData) {
        if (upiData.vpa.isBlank()) {
            showInvalidQrCode(getString(R.string.error_qr_invalid_vpa))
            return
        }
        if (!beginQrPaymentSession(upiData)) return
        if (copyVpaToClipboard(upiData.vpa)) {
            dialUSSD()
        } else {
            failPendingPayment("Could not copy the payment recipient")
            showPaymentError(R.string.qr_clipboard_unavailable)
        }
    }

    private fun beginQrPaymentSession(upiData: UPIData): Boolean {
        val session = OffPayApplication.from(this)?.paymentSessionManager
        if (session == null) {
            showPaymentError(R.string.error_payment_init_failed)
            return false
        }
        automaticVerification = SmsConfirmation.isAvailableForQr(this, upiData.amount)
        return try {
            // Every new attempt replaces the old matching window, even when SMS cannot verify this QR.
            val detector = TransactionDetector.getInstance(this)
            detector.stopOperation()
            val sessionId = session.begin(
                phoneNumber = "",
                amount = upiData.amount ?: "",
                upiId = upiData.vpa,
                source = TransactionSource.QR,
                automaticVerification = automaticVerification
            )
            pendingSession = session
            if (automaticVerification) {
                detector.startOperation(
                    operationType = "QR_SCAN",
                    expectedAmount = upiData.amount,
                    sessionTxnId = sessionId
                )
            }
            true
        } catch (error: IllegalStateException) {
            Log.e("QRScanner", "Could not create the payment session", error)
            failPendingPayment("Could not prepare the payment session")
            showPaymentError(R.string.error_payment_init_failed)
            false
        }
    }

    private fun dialUSSD() {
        if (hasDialedUSSD) return
        val session = pendingSession ?: run {
            showPaymentError(R.string.error_payment_init_failed)
            return
        }
        hasDialedUSSD = true
        when (PaymentDialer(this).launch(Uri.fromParts("tel", "*99*1*3#", null))) {
            PaymentDialer.Result.DIRECT_CALL, PaymentDialer.Result.DIALER_OPENED -> {
                // A dialer handoff is not a receipt. Optional SMS may confirm the session later.
                hasHandedOff = true
                if (!automaticVerification) session.onExternalConfirmationRequired()
                pendingSession = null
                showPaymentGuide(confirmationGuideMessage())
            }
            PaymentDialer.Result.UNAVAILABLE -> {
                failPendingPayment("No compatible dialer could open the payment code")
                clearVpaClipboard()
                showPaymentError(R.string.qr_dialer_unavailable)
            }
        }
    }

    private fun failPendingPayment(reason: String) {
        if (automaticVerification) TransactionDetector.getInstance(this).stopOperation()
        pendingSession?.onDialFailed(reason)
        pendingSession = null
    }

    @StringRes
    private fun confirmationGuideMessage(): Int =
        if (automaticVerification) R.string.qr_optional_sms_confirmation else R.string.qr_external_confirmation

    private fun showPaymentError(@StringRes message: Int) {
        paymentError = true
        isProcessingQRCode = true
        showPaymentGuide(message)
    }

    private fun showPaymentGuide(@StringRes message: Int) {
        if (!isActivityAlive()) return
        imageAnalyzer?.clearAnalyzer()
        camera?.cameraControl?.enableTorch(false)
        viewFinder.visibility = View.GONE
        scannerOverlay.visibility = View.GONE
        bottomActionBar.visibility = View.GONE
        tvCameraHint.visibility = View.GONE
        tvScanError.visibility = View.GONE
        progressBar.visibility = View.GONE
        topBar.visibility = View.GONE
        instructionsBox.visibility = View.VISIBLE
        findViewById<View>(R.id.instructionsExpanded).visibility = if (paymentError) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.instructionsHeaderInitial).setText(
            if (paymentError) R.string.qr_handoff_error_title else R.string.qr_handoff_title
        )
        tvStatus.setText(message)
        btnTerminate.visibility = View.VISIBLE
        findViewById<View>(R.id.qrRoot).setBackgroundColor(savedColorScheme(this).background.toArgb())
    }

    /** Done closes only this guide. It cannot cancel a bank payment or confirm its result. */
    private fun closeScanner() {
        if (!hasHandedOff && pendingSession != null) failPendingPayment("Closed before the dialer opened")
        setResult(
            when {
                hasHandedOff -> RESULT_HANDOFF
                paymentError -> RESULT_ERROR
                else -> RESULT_CANCELLED
            }
        )
        finish()
    }

    private fun copyVpaToClipboard(vpa: String): Boolean =
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(CLIPBOARD_LABEL, vpa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            didCopyVpa = true
            true
        } catch (error: SecurityException) {
            Log.w("QRScanner", "Clipboard unavailable", error)
            false
        } catch (error: IllegalStateException) {
            Log.w("QRScanner", "Clipboard unavailable", error)
            false
        }

    private fun clearVpaClipboard() {
        if (!didCopyVpa) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // Do not erase a different item the user copied while working in their dialer.
            if (clipboard.primaryClipDescription?.label == CLIPBOARD_LABEL) clipboard.clearPrimaryClip()
            didCopyVpa = false
        } catch (error: SecurityException) {
            Log.w("QRScanner", "Could not clear the payee clipboard", error)
        } catch (error: IllegalStateException) {
            Log.w("QRScanner", "Could not clear the payee clipboard", error)
        }
    }

    private fun decodeQrFromGallery(uri: Uri) {
        pendingGalleryUri = uri
        lifecycleScope.launch {
            galleryPending = true
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > GALLERY_MAX_EDGE_PX) {
                            decoder.setTargetSampleSize(longest / GALLERY_MAX_EDGE_PX + 1)
                        }
                    }
                    try {
                        QRCodeAnalyzer.decodeBitmap(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }.onFailure { Log.w("QRScanner", "Gallery image could not be read", it) }
            }
            if (!isActivityAlive() || hasHandedOff || paymentError) return@launch
            // Picker results can arrive before onResume. Keep the URI until we can consume it safely.
            lifecycle.withResumed {
                galleryPending = false
                pendingGalleryUri = null
                when {
                    decoded.isFailure -> showInvalidQrCode(getString(R.string.qr_gallery_unreadable))
                    decoded.getOrNull() == null -> showInvalidQrCode(getString(R.string.qr_gallery_no_code))
                    else -> processQRCode(requireNotNull(decoded.getOrNull()))
                }
            }
        }
    }

    private fun showInvalidQrCode(reason: String) {
        isProcessingQRCode = false
        resumeScanning()
        tvScanError.text = getString(R.string.qr_invalid_banner, reason)
        tvScanError.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideScanErrorRunnable)
        mainHandler.postDelayed(hideScanErrorRunnable, SCAN_ERROR_VISIBLE_MS)
    }

    private val hideScanErrorRunnable = Runnable {
        if (isActivityAlive() && ::tvScanError.isInitialized) tvScanError.visibility = View.GONE
    }

    private fun resumeScanning() {
        if (!canScan() || !::viewFinder.isInitialized) return
        topBar.visibility = View.VISIBLE
        bottomActionBar.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        if (!hasCameraPermission() || camera == null) {
            showGalleryMode()
            if (hasCameraPermission()) startCamera()
            return
        }
        viewFinder.visibility = View.VISIBLE
        scannerOverlay.visibility = View.VISIBLE
        tvCameraHint.visibility = View.GONE
        btnFlash.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
        btnFlash.alpha = if (btnFlash.isEnabled) 1f else DISABLED_CONTROL_ALPHA
        if (hasCameraExecutor()) {
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer?.setAnalyzer(cameraExecutor, QRCodeAnalyzer(::processQRCode))
        }
    }

    override fun onPause() {
        imageAnalyzer?.clearAnalyzer()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraExecutor() || !canScan()) return
        // Returning from Settings may grant camera access; a denial never triggers another prompt here.
        if (hasCameraPermission()) startCamera() else showGalleryMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_HANDOFF, hasHandedOff)
        outState.putBoolean(STATE_CAMERA_REQUESTED, cameraPermissionRequested)
        outState.putBoolean(STATE_GALLERY_PENDING, galleryPending)
        outState.putString(STATE_GALLERY_URI, pendingGalleryUri?.toString())
        outState.putBoolean(STATE_CLIPBOARD_OWNED, didCopyVpa)
        outState.putBoolean(STATE_PAYMENT_ERROR, paymentError)
        outState.putBoolean(STATE_AUTOMATIC_VERIFICATION, automaticVerification)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && !hasHandedOff && pendingSession != null) {
            failPendingPayment("Closed before the dialer opened")
        }
        mainHandler.removeCallbacksAndMessages(null)
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        if (hasCameraExecutor()) cameraExecutor.shutdown()
        // A recreation preserves the paste target; finishing clears only the clip this flow created.
        if (isFinishing) clearVpaClipboard()
        super.onDestroy()
    }
}
