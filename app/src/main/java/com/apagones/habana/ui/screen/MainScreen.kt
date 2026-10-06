package com.apagones.habana.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apagones.habana.R
import com.apagones.habana.data.AppNotification
import com.apagones.habana.data.AppSettings
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla principal con 3 pestañas (Monitoreo, Historial y Preferencias) y gestión de circuitos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var newText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var showOnboarding by remember { mutableStateOf(false) }

    // ------ Permiso de notificaciones (Android 13+) ------
    var notificationsGranted by remember { mutableStateOf(true) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Mostrar onboarding automáticamente la primera vez
    LaunchedEffect(settings.firstRunCompleted) {
        if (!settings.firstRunCompleted) {
            showOnboarding = true
        }
    }

    // Actualiza la notificación permanente cuando cambien los ajustes
    LaunchedEffect(settings.paused, settings.showPersistentNotification, settings.lastCheckMillis) {
        NotificationHelper.updateStatusNotification(context, settings)
    }

    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = {
                showOnboarding = false
                if (!settings.firstRunCompleted) {
                    viewModel.setFirstRunCompleted(true)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { showOnboarding = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.onboarding_help_cd)
                            )
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_monitoring)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val count = settings.notifications.size
                            val title = stringResource(R.string.tab_history)
                            Text(if (count > 0) "$title ($count)" else title)
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.tab_preferences)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> MonitoringTab(
                paddingValues = paddingValues,
                settings = settings,
                newText = newText,
                onNewTextChanged = { newText = it },
                onAddCircuit = { normalized ->
                    viewModel.addCircuit(
                        normalized,
                        onAdded = { newText = "" },
                        onDuplicate = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snack_duplicate_circuit)
                                )
                            }
                        }
                    )
                },
                onInvalidCircuit = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.snack_invalid_circuit)
                        )
                    }
                },
                onDeleteCircuit = { viewModel.removeCircuit(it) },
                onTogglePause = { viewModel.setPaused(it) },
                notificationsGranted = notificationsGranted,
                onOpenBatterySettings = { openAppBatterySettings(context) }
            )
            1 -> HistoryTab(
                paddingValues = paddingValues,
                settings = settings,
                onClearHistory = { viewModel.clearNotifications() }
            )
            2 -> PreferencesTab(
                paddingValues = paddingValues,
                settings = settings,
                onSetNotifyNational = { viewModel.setNotifyNational(it) },
                onSetPersistentNotif = { viewModel.setShowPersistentNotification(it) }
            )
        }
    }
}

/** Diálogo de tutorial / Onboarding de bienvenida. */
@Composable
private fun OnboardingDialog(
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val titles = listOf(
        stringResource(R.string.onboarding_title_1),
        stringResource(R.string.onboarding_title_2),
        stringResource(R.string.onboarding_title_3),
        stringResource(R.string.onboarding_title_4)
    )
    val descriptions = listOf(
        stringResource(R.string.onboarding_desc_1),
        stringResource(R.string.onboarding_desc_2),
        stringResource(R.string.onboarding_desc_3),
        stringResource(R.string.onboarding_desc_4)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titles[step], fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text(descriptions[step], style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(titles.size) { index ->
                        val active = index == step
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step < titles.size - 1) {
                        step++
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (step < titles.size - 1) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_finish))
            }
        },
        dismissButton = {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text(stringResource(R.string.onboarding_prev))
                }
            }
        }
    )
}

/** Pestaña 0: Monitoreo (Panel de estado y Circuitos). */
@Composable
private fun MonitoringTab(
    paddingValues: PaddingValues,
    settings: AppSettings,
    newText: String,
    onNewTextChanged: (String) -> Unit,
    onAddCircuit: (String) -> Unit,
    onInvalidCircuit: () -> Unit,
    onDeleteCircuit: (String) -> Unit,
    onTogglePause: (Boolean) -> Unit,
    notificationsGranted: Boolean,
    onOpenBatterySettings: () -> Unit
) {
    var circuitToDelete by remember { mutableStateOf<String?>(null) }

    // Diálogo de confirmación para eliminar circuito
    if (circuitToDelete != null) {
        val circuit = circuitToDelete!!
        AlertDialog(
            onDismissRequest = { circuitToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_circuit_title)) },
            text = { Text(stringResource(R.string.dialog_delete_circuit_msg, circuit)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCircuit(circuit)
                        circuitToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { circuitToDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ---------- Bloque: panel de monitoreo (estado y pausa) ----------
        item {
            StatusCard(
                settings = settings,
                notificationsGranted = notificationsGranted,
                onTogglePause = onTogglePause,
                onOpenBatterySettings = onOpenBatterySettings
            )
        }

        // ---------- Bloque: agregar circuito ----------
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newText,
                    onValueChange = onNewTextChanged,
                    label = { Text(stringResource(R.string.label_add_circuit)) },
                    placeholder = { Text(stringResource(R.string.hint_add_circuit)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    val normalized = SettingsRepository.normalizeCircuit(newText)
                    if (normalized.length < 2) {
                        onInvalidCircuit()
                    } else {
                        onAddCircuit(normalized)
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_add))
                }
            }
        }

        // ---------- Bloque: lista de circuitos ----------
        item {
            Text(
                text = stringResource(R.string.title_circuits),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (settings.circuits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_circuits),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(settings.circuits, key = { it }) { circuit ->
                val normalized = SettingsRepository.normalizeCircuit(circuit)
                val isKnown = settings.knownCircuits.contains(normalized)
                val isAffected = settings.affectedCircuits.contains(normalized)

                CircuitRow(
                    circuit = circuit,
                    isKnown = isKnown,
                    isAffected = isAffected,
                    onDelete = { circuitToDelete = circuit }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Pestaña 1: Historial de avisos. */
@Composable
private fun HistoryTab(
    paddingValues: PaddingValues,
    settings: AppSettings,
    onClearHistory: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_confirm_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.title_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (settings.notifications.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.action_clear_history))
                    }
                }
            }
        }

        if (settings.notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(settings.notifications, key = { it.id }) { notif ->
                NotificationHistoryCard(notification = notif)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Pestaña 2: Preferencias (Parte nacional y Notificación permanente). */
@Composable
private fun PreferencesTab(
    paddingValues: PaddingValues,
    settings: AppSettings,
    onSetNotifyNational: (Boolean) -> Unit,
    onSetPersistentNotif: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // ---------- Bloque: opción parte nacional ----------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.switch_national),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.notifyNationalReport,
                        onCheckedChange = onSetNotifyNational
                    )
                }
            }
        }

        // ---------- Bloque: opción notificación permanente ----------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.switch_persistent_notif),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.switch_persistent_notif_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.showPersistentNotification,
                        onCheckedChange = onSetPersistentNotif
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Fila de un circuito con indicador de estado (En espera, Afectado u Operativo) y botón de eliminar. */
@Composable
private fun CircuitRow(
    circuit: String,
    isKnown: Boolean,
    isAffected: Boolean,
    onDelete: () -> Unit
) {
    val containerColor = when {
        !isKnown -> MaterialTheme.colorScheme.surfaceVariant
        isAffected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val statusText = when {
        !isKnown -> stringResource(R.string.circuit_status_waiting)
        isAffected -> stringResource(R.string.circuit_status_affected)
        else -> stringResource(R.string.circuit_status_normal)
    }

    val statusColor = when {
        !isKnown -> MaterialTheme.colorScheme.onSurfaceVariant
        isAffected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = circuit,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Tarjeta con el estado del servicio: intervalo, última revisión, pausa y avisos. */
@Composable
private fun StatusCard(
    settings: AppSettings,
    notificationsGranted: Boolean,
    onTogglePause: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current

    var ignoringBattery by remember {
        mutableStateOf(SettingsRepository.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoringBattery = SettingsRepository.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (settings.paused)
                    stringResource(R.string.status_paused)
                else
                    stringResource(R.string.status_active),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (settings.lastCheckMillis > 0)
                    "Última revisión: " + DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(settings.lastCheckMillis))
                else stringResource(R.string.last_check_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!settings.paused) {
                val nextScanTarget = if (settings.lastCheckMillis > 0) settings.lastCheckMillis + 2 * 60 * 1000 else System.currentTimeMillis() + 2 * 60 * 1000
                val effectiveTarget = if (nextScanTarget > System.currentTimeMillis()) nextScanTarget else System.currentTimeMillis() + 2 * 60 * 1000
                val nextScanStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(effectiveTarget))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.next_scan_time, nextScanStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { onTogglePause(!settings.paused) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (settings.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (settings.paused) R.string.action_resume else R.string.action_pause
                    )
                )
            }

            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(4.dp))
                WarningBlock(
                    title = stringResource(R.string.notif_permission_title),
                    body = stringResource(R.string.notif_permission_body),
                    actionLabel = stringResource(R.string.notif_permission_action),
                    onAction = { openAppNotificationSettings(context) }
                )
            }

            if (!ignoringBattery) {
                Spacer(Modifier.height(4.dp))
                WarningBlock(
                    title = stringResource(R.string.battery_optimization_title),
                    body = stringResource(R.string.battery_optimization_body),
                    actionLabel = stringResource(R.string.battery_optimization_action),
                    onAction = onOpenBatterySettings
                )
            }
        }
    }
}

/** Bloque de advertencia con título, cuerpo y acción. */
@Composable
private fun WarningBlock(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel)
            }
        }
    }
}

/** Tarjeta para un ítem del historial de notificaciones. */
@Composable
private fun NotificationHistoryCard(notification: AppNotification) {
    val context = LocalContext.current
    val timeFormatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(notification.timestamp))
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = notification.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Ver menos" else "Ver completo...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (notification.postUrl.isNotBlank()) {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(notification.postUrl))
                        context.startActivity(intent)
                    }) {
                        Text(
                            text = stringResource(R.string.action_view_post),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

private fun openAppBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { context.startActivity(intent) }
}
