package com.apagones.habana.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apagones.habana.R
import com.apagones.habana.data.AppSettings
import com.apagones.habana.data.SettingsRepository
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla principal: gestión de circuitos monitoreados + estado del servicio.
 * Toda la persistencia pasa por [SettingsRepository] (DataStore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Texto del campo "nuevo circuito" y mensajes tipo Snackbar
    var newText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // ------ Permiso de notificaciones (Android 13+) ------
    var notificationsGranted by remember { mutableStateOf(true) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) {
                // Solicitar el permiso al entrar en Android 13+
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Bloque: agregar circuito ----------
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text(stringResource(R.string.label_add_circuit)) },
                        placeholder = { Text(stringResource(R.string.hint_add_circuit)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        val normalized = SettingsRepository.normalizeCircuit(newText)
                        if (normalized.length < 2) {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.snack_invalid_circuit)
                            )
                        } else {
                            viewModel.addCircuit(
                                normalized,
                                onAdded = { newText = "" },
                                onDuplicate = {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.snack_duplicate_circuit)
                                    )
                                }
                            )
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_add))
                    }
                }
            }

            // ---------- Bloque: estado del monitoreo ----------
            item {
                StatusCard(
                    settings = settings,
                    notificationsGranted = notificationsGranted,
                    onTogglePause = { paused -> viewModel.setPaused(paused) },
                    onOpenBatterySettings = { openAppBatterySettings(context) }
                )
            }

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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.switch_national),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.notifyNationalReport,
                            onCheckedChange = { viewModel.setNotifyNational(it) }
                        )
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
                            .padding(top = 32.dp),
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
                    CircuitRow(
                        circuit = circuit,
                        onDelete = { viewModel.removeCircuit(circuit) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Fila de un circuito con botón de eliminar. */
@Composable
private fun CircuitRow(circuit: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = circuit,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
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

    // Estado local que se re-evalúa cada vez que la app vuelve a primer plano
    // (ON_RESUME), para saber si el usuario ya excluyó la app de la
    // optimización de batería (Doze).
    var ignoringBattery by remember {
        mutableStateOf(SettingsRepository.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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

            Spacer(Modifier.height(8.dp))

            // Botón pausar / reanudar el WorkManager
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

            // Aviso: permiso de notificaciones denegado (Android 13+)
            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(4.dp))
                WarningBlock(
                    title = stringResource(R.string.notif_permission_title),
                    body = stringResource(R.string.notif_permission_body),
                    actionLabel = stringResource(R.string.notif_permission_action),
                    onAction = { openAppNotificationSettings(context) }
                )
            }

            // Sugerencia: excluir la app de la optimización de batería
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

/**
 * Abre los ajustes de la app para que el usuario desactive la optimización
 * de batería. Preferimos ACTION_APPLICATION_DETAILS_SETTINGS a
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (mal vista por Google Play).
 */
private fun openAppBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

/** Abre los ajustes de notificaciones de la app (para conceder el permiso). */
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
