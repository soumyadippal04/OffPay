// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay
//
// Home screen: the entry point to both payment rails (Scan QR and Pay
// Contact), recent payments, and settings. UI only — the transfer
// orchestration and permission gating live in MainActivityHelper.

package com.offpay.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offpay.app.constants.AppConstants
import com.offpay.app.data.OfflineBankCatalog
import com.offpay.app.data.PaymentDetails
import com.offpay.app.data.PaymentStatus
import com.offpay.app.data.TestResultsManager
import com.offpay.app.helpers.MainActivityHelper
import com.offpay.app.managers.PermissionManager
import com.offpay.app.payment.Upi123CallStringBuilder
import com.offpay.app.payment.messageFor
import com.offpay.app.ui.activities.SettingsActivity
import com.offpay.app.ui.activities.TransactionHistoryActivity
import com.offpay.app.ui.activities.statusLabelRes
import com.offpay.app.ui.components.BankConfirmationNote
import com.offpay.app.ui.components.CreatorCredit
import com.offpay.app.ui.components.GlassActionButton
import com.offpay.app.ui.components.TransactionDetailDialog
import com.offpay.app.ui.components.glassButton
import com.offpay.app.ui.dialogs.ContactPickerDialog
import com.offpay.app.ui.theme.BlueAccentTheme
import com.offpay.app.ui.theme.LocalOffPayAccentTheme
import com.offpay.app.ui.theme.OffPayDarkGray
import com.offpay.app.ui.theme.OffPayLightGray
import com.offpay.app.ui.theme.OffPayMediumGray
import com.offpay.app.ui.theme.OffPayOnGradient
import com.offpay.app.ui.theme.OffPayOutlineGray
import com.offpay.app.ui.theme.OffPayStatusError
import com.offpay.app.ui.theme.OffPaySurfaceDim
import com.offpay.app.ui.theme.OffPayTextGray
import com.offpay.app.ui.theme.OffPayTextLightGray
import com.offpay.app.ui.theme.OffPayTextPale
import com.offpay.app.ui.theme.OffPayTextWhite
import com.offpay.app.ui.theme.OffPayTheme
import com.offpay.app.ui.theme.applyWindowTheme
import com.offpay.app.utils.CurrencyFormat
import com.offpay.app.utils.findComponentActivity
import com.offpay.app.viewmodel.MainUiEvent
import com.offpay.app.viewmodel.MainViewModel
import com.offpay.app.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OffPay"
    }

    // Helper for all business logic
    private lateinit var helper: MainActivityHelper

    // Shared with MainScreen (same instance via Compose viewModel()); carries
    // one-shot Activity -> Compose events, replacing the old static callbacks.
    private val mainViewModel: MainViewModel by viewModels()

    // Launches QRScannerActivity and, on return, un-sticks the QR button's
    // "Opening..." state via a QrScannerClosed event.
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.onQrScannerClosed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_OffPay)

        // Draw edge-to-edge so Compose's statusBarsPadding()/navigationBarsPadding()
        // are the single source of inset padding. The theme previously also set
        // android:fitsSystemWindows=true, which made the decor pad the content as
        // well — a double inset that, depending on inset-dispatch timing, showed
        // intermittent black bars at the top and bottom.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        applyWindowTheme(this)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Initialize helper with UI callbacks (matches the 6-method UICallback)
        helper = MainActivityHelper(
            this,
            object : MainActivityHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                }

                override fun updatePaymentState(paymentState: com.offpay.app.states.PaymentState) {
                    Log.d(TAG, "Payment state updated: ${paymentState::class.simpleName}")
                }

                override fun navigateToSetup() {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                    finish()
                }

                override fun navigateToTestConfiguration() {
                    startActivity(Intent(this@MainActivity, TestConfigurationActivity::class.java))
                    finish()
                }

                override fun finishActivity() {
                    finish()
                }

                override fun launchQRScanner(intent: Intent) {
                    qrScannerLauncher.launch(intent)
                }
            }
        )

        helper.initialize()

        // The launch gate must know which screen to show before rendering, so
        // these two SharedPreferences flags are read synchronously. Reading
        // them faults the prefs file in once (a small, one-time disk read);
        // annotate it as permitted so the debug StrictMode tripwire stays
        // sharp for genuinely unexpected main-thread disk I/O instead of
        // crying wolf on this known-safe read every launch.
        val setupCompleted: Boolean
        val testCompleted: Boolean
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            setupCompleted = helper.isSetupCompleted()
            testCompleted = helper.isTestCompleted()
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }

        if (!setupCompleted) {
            helper.navigateToSetup()
            return
        }
        if (!testCompleted) {
            helper.navigateToTestConfiguration()
            return
        }

        setContent {
            CompositionLocalProvider(LocalOffPayAccentTheme provides BlueAccentTheme) {
                OffPayTheme {
                    MainScreen(
                        onInitiateTransfer = { phoneNumber, amount ->
                            helper.initiateTransfer(phoneNumber, amount)
                        },
                        onQRScanClick = {
                            helper.startQRScanning()
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        helper.onPause()
    }

    override fun onResume() {
        super.onResume()
        helper.onResume()
        applyWindowTheme(this)
    }

    override fun onStop() {
        super.onStop()
        helper.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

// Payment Action Buttons - QR scan + Pay Contact.
//
// Each rail is locked until its own connectivity test has passed: Scan QR
// dials *99#, Pay Contact dials the UPI 123 IVR. One composable per button,
// because the locked/unlocked branches on both pushed the combined function
// past detekt's complexity and parameter limits.
@Composable
fun PaymentActionButtons(
    onQRScanClick: () -> Unit,
    onPayContactClick: () -> Unit,
    isUpi123Ready: Boolean,
    isUssdReady: Boolean,
    isScanning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScanQrButton(onQRScanClick, isUssdReady, isScanning)
        PayContactButton(onPayContactClick, isUpi123Ready)
    }
}

/** Scan QR — locked until the *99# connectivity test has passed. */
@Composable
private fun ScanQrButton(
    onQRScanClick: () -> Unit,
    isUssdReady: Boolean,
    isScanning: Boolean
) {
    GlassActionButton(
        label = when {
            !isUssdReady -> stringResource(R.string.home_setup_ussd)
            isScanning -> stringResource(R.string.home_scan_opening)
            else -> stringResource(R.string.home_scan_qr)
        },
        icon = when {
            !isUssdReady -> Icons.Default.Lock
            isScanning -> Icons.Default.QrCode
            else -> Icons.Default.QrCodeScanner
        },
        enabled = !isScanning,
        vibrant = isUssdReady,
        onClick = onQRScanClick
    )
}

/** Pay Contact — locked until the UPI 123 IVR connectivity test has passed. */
@Composable
private fun PayContactButton(
    onPayContactClick: () -> Unit,
    isUpi123Ready: Boolean
) {
    GlassActionButton(
        label = stringResource(if (isUpi123Ready) R.string.home_pay_contact else R.string.home_setup_upi123),
        icon = if (isUpi123Ready) Icons.Default.Person else Icons.Default.Lock,
        vibrant = isUpi123Ready,
        onClick = onPayContactClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onInitiateTransfer: (String, String) -> Unit,
    onQRScanClick: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var savedBank by remember {
        mutableStateOf(sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc")
    }

    // Pay Contact dials the UPI 123 IVR, so it stays inactive until the
    // UPI 123 configuration test has passed (re-checked on every resume so
    // completing the test activates it immediately).
    val testResultsManager = remember { TestResultsManager(context) }
    var isUpi123Ready by remember {
        mutableStateOf(testResultsManager.getTestResults()?.upi123Enabled == true)
    }

    // Scan QR dials *99#, so it stays locked until the *99# test has passed —
    // the same gate Pay Contact has always had for the UPI 123 IVR. Without
    // it the scanner opened, read a QR, and only then dialled a rail this SIM
    // may not support.
    var isUssdReady by remember {
        mutableStateOf(testResultsManager.getTestResults()?.ussdEnabled == true)
    }

    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                savedBank = sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "hdfc") ?: "hdfc"
                isUpi123Ready = testResultsManager.getTestResults()?.upi123Enabled == true
                isUssdReady = testResultsManager.getTestResults()?.ussdEnabled == true
            }
        }
    }

    val transactionViewModel: TransactionViewModel = viewModel()
    val recentPayments by transactionViewModel.recentTransactions.collectAsState()
    val selectedTransaction by transactionViewModel.selectedTransaction.collectAsState()
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    var showPayContact by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    // One-shot events from MainActivity (launchers + business-logic helper),
    // replacing the former static @Volatile callbacks on its companion.
    val mainViewModel: MainViewModel = viewModel()
    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            when (event) {
                MainUiEvent.QrScannerClosed -> isScanning = false
            }
        }
    }

    // Reset scanning + refresh list whenever the app resumes
    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                isScanning = false
                transactionViewModel.refresh()
            }
        }
    }

    // Safety: never leave the QR button stuck in "Opening..."
    LaunchedEffect(isScanning) {
        if (isScanning) {
            kotlinx.coroutines.delay(AppConstants.USSD_SESSION_TIMEOUT)
            if (isScanning) isScanning = false
        }
    }

    val selectedBankName = OfflineBankCatalog.nameFor(savedBank)
        ?: stringResource(R.string.compatibility_choose_bank)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(220.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            ambientColor = Color.Black.copy(alpha = 0.15f),
                            spotColor = Color.Black.copy(alpha = 0.15f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        LocalOffPayAccentTheme.current.headerGradientStart,
                                        LocalOffPayAccentTheme.current.headerGradientEnd
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "OffPay",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OffPayOnGradient,
                                        style = TextStyle(
                                            shadow = Shadow(Color.Black.copy(alpha = 0.15f), Offset(0f, 2f), 6f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Payments Without Internet",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = OffPayOnGradient.copy(alpha = 0.95f)
                                    )
                                }

                                // Settings Button
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(OffPayOnGradient.copy(alpha = 0.22f))
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            context.startActivity(Intent(context, SettingsActivity::class.java))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings",
                                        tint = OffPayOnGradient,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Bank Info Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(LocalOffPayAccentTheme.current.headerGradientEnd)
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Connected Bank",
                                            fontSize = 12.sp,
                                            color = OffPayOnGradient.copy(alpha = 0.85f),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = selectedBankName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OffPayOnGradient,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(OffPayOnGradient.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Wallet",
                                            tint = OffPayOnGradient,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PaymentActionButtons(
                    onQRScanClick = {
                        if (!isUssdReady) {
                            context.startActivity(Intent(context, TestConfigurationActivity::class.java))
                        } else {
                            isScanning = true
                            onQRScanClick()
                        }
                    },
                    onPayContactClick = {
                        if (!isUpi123Ready) {
                            context.startActivity(Intent(context, TestConfigurationActivity::class.java))
                        } else {
                            showPayContact = true
                        }
                    },
                    isUpi123Ready = isUpi123Ready,
                    isUssdReady = isUssdReady,
                    isScanning = isScanning
                )

                BankConfirmationNote()

                // Recent Transactions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = OffPaySurfaceDim),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(OffPaySurfaceDim, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        modifier = Modifier.size(20.dp),
                                        tint = LocalOffPayAccentTheme.current.headerGradientStart
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Recent Payments",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OffPayTextWhite
                                    )
                                    Text(
                                        text = "Your latest transactions",
                                        fontSize = 12.sp,
                                        color = OffPayTextLightGray
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                                }
                            ) {
                                Text(
                                    text = "View All",
                                    fontSize = 12.sp,
                                    color = LocalOffPayAccentTheme.current.accent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            isLoading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = LocalOffPayAccentTheme.current.headerGradientStart,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "Loading transactions...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OffPayTextLightGray
                                    )
                                }
                            }

                            error != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Failed to load transactions",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OffPayTextWhite
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = error ?: "Unknown error",
                                        fontSize = 13.sp,
                                        color = OffPayTextLightGray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextButton(onClick = { transactionViewModel.refresh() }) {
                                        Text(
                                            text = "Retry",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = LocalOffPayAccentTheme.current.headerGradientStart
                                        )
                                    }
                                }
                            }

                            recentPayments.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .background(OffPayMediumGray, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "No transactions",
                                            modifier = Modifier.size(32.dp),
                                            tint = OffPayTextLightGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "No transactions yet",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OffPayTextWhite
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Your payment history will appear here",
                                        fontSize = 13.sp,
                                        color = OffPayTextLightGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier.height(280.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(recentPayments) { payment ->
                                        TransactionItem(
                                            payment = payment,
                                            onClick = {
                                                transactionViewModel.selectTransaction(payment.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                CreatorCredit(modifier = Modifier.padding(top = 8.dp))
            }

            // Detail dialog for a tapped Recent Payments row — the same
            // surface the history screen shows.
            selectedTransaction?.let { transaction ->
                TransactionDetailDialog(
                    transaction = transaction,
                    onDismiss = { transactionViewModel.clearSelectedTransaction() },
                    onDelete = {
                        transactionViewModel.deleteTransaction(transaction)
                        transactionViewModel.clearSelectedTransaction()
                    }
                )
            }

            // Dialogs
            if (showPayContact) {
                PayContactDialog(
                    onDismiss = { showPayContact = false },
                    onConfirm = { phone, amt ->
                        showPayContact = false
                        onInitiateTransfer(phone, amt)
                    }
                )
            }
        }
    }
}

@Composable
fun TransactionItem(payment: PaymentDetails, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffPaySurfaceDim),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = payment.recipientName ?: payment.phoneNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OffPayTextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDate(payment.timestamp),
                    fontSize = 14.sp,
                    color = OffPayTextPale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }

            TransactionItemAmount(amount = payment.amount, status = payment.status)
        }
    }
}

@Composable
private fun TransactionItemAmount(amount: Double?, status: PaymentStatus) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp)
    ) {
        Text(
            text = if (amount == null) {
                stringResource(R.string.payment_amount_not_entered)
            } else {
                stringResource(R.string.amount_rupees, CurrencyFormat.inr(amount))
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = OffPayTextWhite,
            maxLines = 2,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(statusLabelRes(status.name)),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PayContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    // UPI 123Pay's IVR will not accept 5000 or more (AppConstants
    // .UPI123PAY_MAX_AMOUNT = 4999), so the cap is enforced before dialling.
    val isOverCap = (amount.toLongOrNull() ?: 0L) > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showContactPicker = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OffPayDarkGray,
        title = {
            Text(
                text = "Pay Contact",
                color = OffPayTextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selectedContactName?.let { name ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalOffPayAccentTheme.current.accent.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = LocalOffPayAccentTheme.current.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sending to: $name",
                                color = LocalOffPayAccentTheme.current.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 10) {
                                phoneNumber = it
                                selectedContactName = null
                            }
                        },
                        label = {
                            Text(stringResource(R.string.home_field_mobile_label), color = OffPayTextLightGray)
                        },
                        placeholder = {
                            Text(stringResource(R.string.home_field_mobile_hint), color = OffPayTextGray)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OffPayTextWhite,
                            unfocusedTextColor = OffPayTextWhite,
                            focusedBorderColor = OffPayOutlineGray,
                            unfocusedBorderColor = OffPayLightGray,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            val pm = permissionManager
                            if (pm == null) {
                                Toast.makeText(
                                    context.applicationContext,
                                    "Unable to open contacts from this screen.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            if (pm.hasContactPermission()) {
                                showContactPicker = true
                            } else {
                                showContactPermissionDialog = true
                            }
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(48.dp)
                            .background(
                                color = LocalOffPayAccentTheme.current.accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermContactCalendar,
                            contentDescription = "Select Contact",
                            tint = LocalOffPayAccentTheme.current.accent
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 6) {
                            amount = it
                        }
                    },
                    label = {
                        Text(stringResource(R.string.home_field_amount_label), color = OffPayTextLightGray)
                    },
                    placeholder = {
                        Text(stringResource(R.string.home_field_amount_hint), color = OffPayTextGray)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isOverCap,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OffPayTextWhite,
                        unfocusedTextColor = OffPayTextWhite,
                        focusedBorderColor = if (isOverCap) OffPayStatusError else OffPayOutlineGray,
                        unfocusedBorderColor = if (isOverCap) OffPayStatusError else OffPayLightGray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                Text(
                    text = stringResource(R.string.compatibility_check_bank),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                // Told here, in the dialog, while the number can still be
                // corrected. The IVR itself only rejects an over-cap amount
                // mid-call, after the user has already dialled.
                if (isOverCap) {
                    Text(
                        text = Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context),
                        color = OffPayStatusError,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            val canTransfer = phoneNumber.length == 10 && amount.isNotEmpty() &&
                amount != "0" && !isOverCap
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .glassButton(enabled = canTransfer) { onConfirm(phoneNumber, amount) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.compatibility_continue_dialer),
                    color = if (canTransfer) OffPayOnGradient else OffPayTextGray,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = OffPayTextLightGray)
            }
        }
    )

    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                phoneNumber = contact.phoneNumber
                selectedContactName = contact.name
                showContactPicker = false
            }
        )
    }

    if (showContactPermissionDialog) {
        PermissionExplanationDialog(
            title = "Contacts Permission",
            message = "OffPay needs access to your contacts so you can pick a recipient by name instead of typing their number.",
            confirmButtonText = "Grant",
            onConfirm = {
                showContactPermissionDialog = false
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showContactPermissionDialog = false }
        )
    }
}

@Composable
fun PermissionExplanationDialog(
    title: String,
    message: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OffPayDarkGray,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OffPayTextWhite
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = OffPayTextPale,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalOffPayAccentTheme.current.accent
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now), color = OffPayTextLightGray)
            }
        }
    )
}

// Utility functions
fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
