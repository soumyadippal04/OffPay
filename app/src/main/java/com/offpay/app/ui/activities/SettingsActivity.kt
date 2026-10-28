// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

// SettingsActivity.kt
package com.offpay.app.ui.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offpay.app.BuildConfig
import com.offpay.app.OffPayApplication
import com.offpay.app.R
import com.offpay.app.SetupActivity
import com.offpay.app.constants.AppConstants
import com.offpay.app.constants.PermissionConstants
import com.offpay.app.data.Bank
import com.offpay.app.data.OfflineBankCatalog
import com.offpay.app.data.SettingsRepository
import com.offpay.app.managers.FirstLaunchPermissions
import com.offpay.app.repository.TransactionRepository
import com.offpay.app.ui.components.BankSelectionDialog
import com.offpay.app.ui.components.CreatorCredit
import com.offpay.app.ui.theme.BlueAccentTheme
import com.offpay.app.ui.theme.LocalOffPayAccentTheme
import com.offpay.app.ui.theme.OffPayDarkGray
import com.offpay.app.ui.theme.OffPayDisabledGray
import com.offpay.app.ui.theme.OffPayMediumGray
import com.offpay.app.ui.theme.OffPayStatusError
import com.offpay.app.ui.theme.OffPaySurfaceDim
import com.offpay.app.ui.theme.OffPayTextGray
import com.offpay.app.ui.theme.OffPayTextLightGray
import com.offpay.app.ui.theme.OffPayTextPale
import com.offpay.app.ui.theme.OffPayTextWhite
import com.offpay.app.ui.theme.OffPayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private var refreshTrigger = mutableIntStateOf(0)
    private val permissionHistory by lazy { FirstLaunchPermissions(this) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTrigger.intValue++
        if (!granted) {
            Toast.makeText(this, R.string.optional_sms_notifications_declined, Toast.LENGTH_LONG).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        refreshTrigger.intValue++
        if (permissions[Manifest.permission.RECEIVE_SMS] == true) {
            requestResultNotificationsIfNeeded()
        }
    }

    private fun requestResultNotificationsIfNeeded() {
        if (!BuildConfig.SMS_CONFIRMATION_AVAILABLE || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, false)) {
            prefs.edit { putBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, true) }
            permissionHistory.recordRequest(arrayOf(permission))
            notificationPermissionLauncher.launch(permission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? OffPayApplication
        val settingsRepository = app?.settingsRepository ?: SettingsRepository(applicationContext)
        setTheme(R.style.Theme_OffPay)
        setContent {
            CompositionLocalProvider(LocalOffPayAccentTheme provides BlueAccentTheme) {
                OffPayTheme {
                    SettingsScreen(
                        onBackPressed = { finish() },
                        settingsRepository = settingsRepository,
                        onRequestPermissions = { permissions ->
                            if (permissionHistory.shouldOpenSettings(this, permissions)) {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            } else {
                                permissionHistory.recordRequest(permissions)
                                permissionLauncher.launch(permissions)
                            }
                        },
                        refreshTrigger = refreshTrigger
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
    }
}

data class SettingsState(
    val selectedBank: Bank = OfflineBankCatalog.findById("hdfc") ?: OfflineBankCatalog.banks.first(),
    val permissions: Map<String, Boolean> = emptyMap()
)

// ViewModel
class SettingsViewModel : androidx.lifecycle.ViewModel() {
    var state by mutableStateOf(SettingsState())
        private set

    fun updateBank(bank: Bank) {
        state = state.copy(selectedBank = bank)
    }

    fun refreshPermissions(context: Context) {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val perms = mapOf(
            "phone" to granted(Manifest.permission.CALL_PHONE),
            "camera" to granted(Manifest.permission.CAMERA),
            "contacts" to granted(Manifest.permission.READ_CONTACTS),
            "sms" to (BuildConfig.SMS_CONFIRMATION_AVAILABLE && granted(Manifest.permission.RECEIVE_SMS))
        )
        state = state.copy(permissions = perms)
    }

    fun loadFromRepository(settingsRepository: SettingsRepository) {
        val saved = settingsRepository.settingsFlow.value
        val bank = OfflineBankCatalog.findById(saved.bankId) ?: Bank(saved.bankId, saved.bankId)
        state = state.copy(selectedBank = bank)
    }
}

// ═══════════════════════════════════════════
// Main Settings Screen
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackPressed: () -> Unit,
    settingsRepository: SettingsRepository? = null,
    onRequestPermissions: (Array<String>) -> Unit = {},
    refreshTrigger: MutableIntState = mutableIntStateOf(0)
) {
    val context = LocalContext.current
    val accent = LocalOffPayAccentTheme.current
    val state = viewModel.state
    val savedSettings = settingsRepository?.settingsFlow?.collectAsState()?.value

    // Load settings from repository on first composition
    LaunchedEffect(Unit) {
        settingsRepository?.let { viewModel.loadFromRepository(it) }
        viewModel.refreshPermissions(context)
    }

    // Refresh permissions when trigger changes
    val trigger by refreshTrigger
    LaunchedEffect(trigger) {
        viewModel.refreshPermissions(context)
    }

    // Get primary SIM name from shared prefs (reactive)
    var primarySimId by remember {
        val prefs = context.getSharedPreferences("OffPayPrefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("selected_primary_sim", "") ?: "")
    }
    val primarySim = when (primarySimId) {
        "jio" -> "Jio"
        "airtel" -> "Airtel"
        "vodafone" -> "Vodafone"
        "bsnl" -> "BSNL"
        else -> stringResource(R.string.settings_not_set)
    }

    // Dialog states
    var showBankPicker by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffPaySurfaceDim)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OffPayTextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = OffPayTextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OffPaySurfaceDim
                )
            )

            // Settings Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ═══ CONFIGURATION ═══
                item { SectionHeader("CONFIGURATION") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.AccountBalance,
                            title = "Bank",
                            value = state.selectedBank.name,
                            onClick = { showBankPicker = true }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.SimCard,
                            title = "Primary SIM",
                            value = primarySim,
                            onClick = { showSimPicker = true }
                        )
                    }
                }

                // Appearance updates the shared preference observed by every Compose screen.
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_appearance)) }
                item {
                    GroupCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = savedSettings?.darkModeEnabled ?: true,
                                    role = Role.Switch,
                                    onValueChange = { dark ->
                                        settingsRepository?.let { repository ->
                                            repository.saveSettings(
                                                repository.settingsFlow.value.copy(darkModeEnabled = dark)
                                            )
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness6,
                                contentDescription = null,
                                tint = accent.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_dark_mode), color = OffPayTextWhite)
                                Text(
                                    stringResource(
                                        if (savedSettings?.darkModeEnabled != false) {
                                            R.string.settings_mode_dark
                                        } else {
                                            R.string.settings_mode_light
                                        }
                                    ),
                                    color = OffPayTextLightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = savedSettings?.darkModeEnabled ?: true, onCheckedChange = null)
                        }
                    }
                }

                // ═══ PERMISSIONS ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_optional_permissions)) }
                item {
                    GroupCard {
                        PermissionRow(
                            icon = Icons.Default.Phone,
                            title = stringResource(R.string.settings_phone_permission),
                            subtitle = stringResource(R.string.settings_phone_permission_detail),
                            granted = state.permissions["phone"] ?: false,
                            onRequest = {
                                onRequestPermissions(
                                    PermissionConstants.PHONE_PERMISSIONS
                                )
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.settings_camera_permission),
                            subtitle = stringResource(R.string.settings_camera_permission_detail),
                            granted = state.permissions["camera"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.CAMERA))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Contacts,
                            title = stringResource(R.string.settings_contacts_permission),
                            subtitle = stringResource(R.string.settings_contacts_permission_detail),
                            granted = state.permissions["contacts"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS))
                            }
                        )
                        if (BuildConfig.SMS_CONFIRMATION_AVAILABLE) {
                            GroupDivider()
                            PermissionRow(
                                icon = Icons.Default.Sms,
                                title = stringResource(R.string.optional_sms_permission_title),
                                subtitle = stringResource(R.string.optional_sms_permission_detail),
                                granted = state.permissions["sms"] ?: false,
                                onRequest = { onRequestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS)) }
                            )
                        }
                    }
                }
                item { BankConfirmationInfo() }

                // ═══ ACTIONS ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("ACTIONS") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.DeleteForever,
                            title = "Clear App Data",
                            value = "Reset all settings",
                            destructive = true,
                            onClick = { showClearDataConfirm = true }
                        )
                    }
                }

                // ═══ ABOUT ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("ABOUT") }
                item {
                    GroupCard {
                        AboutSettingsRow(
                            onClick = { context.startActivity(Intent(context, AboutActivity::class.java)) }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "Version",
                            // Read from the build, never hardcoded: this row
                            // once said "1.0.0" while the app shipped as 2.1.0.
                            value = BuildConfig.VERSION_NAME
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Android",
                            value = Build.VERSION.RELEASE
                        )
                    }
                }

                item { CreatorCredit() }
            }
        }
    }

    // Bank Picker Dialog
    if (showBankPicker) {
        BankSelectionDialog(
            selectedBankId = state.selectedBank.id,
            onBankSelected = { bank ->
                viewModel.updateBank(bank)
                settingsRepository?.saveSettings(
                    settingsRepository.settingsFlow.value.copy(bankId = bank.id)
                )
                // Also sync to OffPayPrefs so the main screen picks it up
                context.getSharedPreferences("OffPayPrefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_bank", bank.id).apply()
                showBankPicker = false
            },
            onDismiss = { showBankPicker = false }
        )
    }

    // SIM Picker Dialog
    if (showSimPicker) {
        SimPickerDialog(
            selectedSimId = primarySimId,
            onSimSelected = { simId ->
                primarySimId = simId
                context.getSharedPreferences("OffPayPrefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_primary_sim", simId).apply()
                showSimPicker = false
            },
            onDismiss = { showSimPicker = false }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            containerColor = OffPayDarkGray,
            titleContentColor = OffPayTextWhite,
            textContentColor = OffPayTextPale,
            title = {
                Text(
                    stringResource(R.string.settings_clear_data_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This permanently deletes your entire transaction history and " +
                        "resets all settings, then returns you to the setup screen. " +
                        "This cannot be undone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    // Wipe the encrypted transaction DB before anything else, on
                    // the process scope so the delete survives the CLEAR_TASK
                    // relaunch below; only then reset prefs and restart into Setup.
                    val app = OffPayApplication.from(context)
                    val relaunch = {
                        settingsRepository?.clearAllData()
                        context.getSharedPreferences("OffPayPrefs", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        context.startActivity(
                            Intent(context, SetupActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }
                    if (app != null) {
                        app.appScope.launch {
                            runCatching {
                                TransactionRepository.getInstance(context).deleteAllTransactions()
                            }
                            withContext(Dispatchers.Main) { relaunch() }
                        }
                    } else {
                        relaunch()
                    }
                }) {
                    Text(
                        stringResource(R.string.action_clear),
                        color = OffPayStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = OffPayTextLightGray)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════
// Reusable Components
// ═══════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = OffPayTextGray,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OffPayDarkGray
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = OffPayMediumGray,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun AboutSettingsRow(onClick: () -> Unit) {
    val accent = LocalOffPayAccentTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                text = stringResource(R.string.about_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OffPayTextWhite
            )
            Text(
                text = stringResource(R.string.about_settings_summary),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 12.sp,
                color = OffPayTextLightGray
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OffPayDisabledGray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalOffPayAccentTheme.current
    val iconColor = if (destructive) OffPayStatusError else accent.primary
    val iconBg = if (destructive) OffPayStatusError.copy(alpha = 0.12f) else accent.primary.copy(alpha = 0.12f)
    val titleColor = if (destructive) OffPayStatusError else OffPayTextWhite

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )

        // Value or chevron
        if (onClick != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = OffPayTextLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OffPayDisabledGray,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = value,
                fontSize = 14.sp,
                color = OffPayTextLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    val accent = LocalOffPayAccentTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OffPayTextWhite)
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = OffPayTextLightGray
            )
            if (granted) {
                Text(
                    text = stringResource(R.string.settings_permission_allowed),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                TextButton(onClick = onRequest, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.settings_permission_allow))
                }
            }
        }
    }
}

@Composable
private fun BankConfirmationInfo() {
    GroupCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_bank_confirmation),
                style = MaterialTheme.typography.titleSmall,
                color = OffPayTextWhite
            )
            Text(
                text = stringResource(
                    if (BuildConfig.SMS_CONFIRMATION_AVAILABLE) {
                        R.string.optional_sms_confirmation_summary
                    } else {
                        R.string.settings_bank_confirmation_detail
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = OffPayTextLightGray
            )
        }
    }
}

@Composable
private fun SimPickerDialog(
    selectedSimId: String,
    onSimSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalOffPayAccentTheme.current
    val sims = listOf(
        "jio" to "Jio",
        "airtel" to "Airtel",
        "vodafone" to "Vodafone",
        "bsnl" to "BSNL"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = OffPayDarkGray
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    text = "Select Primary SIM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OffPayTextWhite,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                sims.forEachIndexed { index, (id, name) ->
                    val isSelected = id == selectedSimId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSimSelected(id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) accent.primary else OffPayTextWhite,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = accent.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < sims.lastIndex) {
                        HorizontalDivider(
                            color = OffPayMediumGray,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), color = OffPayTextLightGray)
                }
            }
        }
    }
}
