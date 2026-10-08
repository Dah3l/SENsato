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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.apagones.habana.parser.MentionMatcher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Help
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.apagones.habana.data.CircuitStatusInfo
import com.apagones.habana.data.SettingsRepository
import com.apagones.habana.notification.NotificationHelper
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pantalla principal con 3 pestañas (Monitoreo, Historial y Preferencias) y gestión de circuitos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredNotifications by viewModel.filteredNotifications.collectAsStateWithLifecycle()

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

    // Mostrar onboarding automáticamente la primera vez solo cuando DataStore haya cargado
    LaunchedEffect(settings.isLoaded, settings.onboardingCompleted) {
        if (settings.isLoaded && !settings.onboardingCompleted) {
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
                if (!settings.onboardingCompleted) {
                    viewModel.setOnboardingCompleted(true)
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
                        // Botón de ayuda (?) en la pantalla principal para mostrar el onboarding bajo demanda
                        IconButton(onClick = { showOnboarding = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Help,
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
                searchQuery = searchQuery,
                filteredNotifications = filteredNotifications,
                onSearchQueryChanged = { viewModel.setSearchQuery(it) },
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

/** Diálogo de tutorial / Onboarding de bienvenida con tamaño fijo basado en el viewport y sin cierre al pulsar fuera. */
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

    // Obtener dimensiones del viewport para un tamaño fijo proporcional (sin hardcodear altura reducida)
    val configuration = LocalConfiguration.current
    val dialogWidth = configuration.screenWidthDp.dp * 0.9f
    val dialogHeight = configuration.screenHeightDp.dp * 0.45f

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false // No se cierra al pulsar fuera del modal
        )
    ) {
        Card(
            modifier = Modifier
                .width(dialogWidth)
                .height(dialogHeight),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Título y contenido con scroll interno
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = titles[step],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = descriptions[step],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Indicadores de pasos y botones de navegación
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (step > 0) {
                            TextButton(onClick = { step-- }) {
                                Text(stringResource(R.string.onboarding_prev))
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        TextButton(
                            onClick = {
                                if (step < titles.size - 1) {
                                    step++
                                } else {
                                    onDismiss()
                                }
                            }
                        ) {
                            Text(
                                if (step < titles.size - 1)
                                    stringResource(R.string.onboarding_next)
                                else
                                    stringResource(R.string.onboarding_finish)
                            )
                        }
                    }
                }
            }
        }
    }
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
    var selectedCircuitForStats by remember { mutableStateOf<String?>(null) }

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

    // Diálogo de estadísticas de la última semana para el circuito seleccionado con tamaño fijo basado en viewport
    if (selectedCircuitForStats != null) {
        val circuit = selectedCircuitForStats!!
        val stats = remember(circuit, settings.notifications) {
            calculateCircuitStats(circuit, settings.notifications)
        }

        // Obtener dimensiones del viewport para un tamaño fijo proporcional (sin hardcodear)
        val configuration = LocalConfiguration.current
        val dialogWidth = configuration.screenWidthDp.dp * 0.9f
        val dialogHeight = configuration.screenHeightDp.dp * 0.65f

        // Combinar y ordenar todos los períodos cronológicamente de más reciente a más antiguo
        val allIntervals = remember(stats) {
            buildList {
                addAll(stats.outageIntervals.map { UnifiedInterval(it.startMillis, it.endMillis, it.isOngoing, true) })
                addAll(stats.operationalIntervals.map { UnifiedInterval(it.startMillis, it.endMillis, it.isOngoing, false) })
            }.sortedByDescending { it.startMillis }
        }

        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false // No se cierra al pulsar fuera del modal
            )
        ) {
            Card(
                modifier = Modifier
                    .width(dialogWidth)
                    .height(dialogHeight),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Zona superior FIJA (Título, Resumen general y Título de la lista), siempre visibles
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stats_dialog_title, circuit),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 1) Resumen general compacto arriba con ambos estados
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🔴 Sin servicio",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${stats.outageIntervals.size} afectaciones",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatDuration(stats.outageMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🟢 Operativo",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${stats.operationalIntervals.size} períodos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatDuration(stats.operationalMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 2) Título de la lista cronológica unificada (fijo)
                        Text(
                            text = "Historial de períodos (última semana)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Zona central con SCROLL ÚNICAMENTE para los elementos del Historial de períodos
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (allIntervals.isEmpty()) {
                            Text(
                                text = "No hay registros en la última semana.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (interval in allIntervals) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (interval.isAffected) "🔴" else "🟢",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formatUnifiedInterval(interval),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (interval.isAffected) "Sin servicio" else "Operativo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (interval.isAffected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Zona inferior FIJA (Botón Cerrar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { selectedCircuitForStats = null }) {
                            Text(stringResource(R.string.stats_dialog_close))
                        }
                    }
                }
            }
        }
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
                val statusInfo = settings.circuitStatuses[normalized]

                CircuitRow(
                    circuit = circuit,
                    isKnown = isKnown,
                    isAffected = isAffected,
                    statusInfo = statusInfo,
                    onDelete = { circuitToDelete = circuit },
                    onClick = { selectedCircuitForStats = circuit }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Pestaña 1: Historial de avisos con barra de búsqueda y filtrado en tiempo real. */
@Composable
private fun HistoryTab(
    paddingValues: PaddingValues,
    settings: AppSettings,
    searchQuery: String,
    filteredNotifications: List<AppNotification>,
    onSearchQueryChanged: (String) -> Unit,
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

        // Barra de búsqueda Material 3 sobre la lista para filtrar en tiempo real (principalmente por circuito)
        if (settings.notifications.isNotEmpty() || searchQuery.isNotBlank()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.history_search_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }

        when {
            settings.notifications.isEmpty() -> {
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
            }
            filteredNotifications.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_no_search_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                items(filteredNotifications, key = { it.id }) { notif ->
                    NotificationHistoryCard(notification = notif)
                }
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

/** Fila de un circuito con indicador de estado y estadísticas al pulsar. */
@Composable
private fun CircuitRow(
    circuit: String,
    isKnown: Boolean,
    isAffected: Boolean,
    statusInfo: CircuitStatusInfo?,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val containerColor = when {
        !isKnown -> MaterialTheme.colorScheme.surfaceVariant
        isAffected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val baseStatusText = when {
        !isKnown -> stringResource(R.string.circuit_status_waiting)
        isAffected -> stringResource(R.string.circuit_status_affected)
        else -> stringResource(R.string.circuit_status_normal)
    }

    val timeAgo = if (isKnown && statusInfo != null) {
        formatElapsedTime(statusInfo.timestamp)
    } else {
        ""
    }

    val statusText = if (timeAgo.isNotBlank() && isKnown) {
        "$baseStatusText $timeAgo"
    } else {
        baseStatusText
    }

    val statusColor = when {
        !isKnown -> MaterialTheme.colorScheme.onSurfaceVariant
        isAffected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

private fun formatElapsedTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val diffMillis = System.currentTimeMillis() - timestamp
    if (diffMillis < 0) return ""
    val minutes = diffMillis / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "desde hace $days ${if (days == 1L) "día" else "días"}"
        hours > 0 -> {
            val remMins = minutes % 60
            if (remMins > 0) "desde hace $hours ${if (hours == 1L) "hora" else "horas"} y $remMins min"
            else "desde hace $hours ${if (hours == 1L) "hora" else "horas"}"
        }
        minutes > 0 -> "desde hace $minutes ${if (minutes == 1L) "minuto" else "minutos"}"
        else -> "hace un momento"
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
                val intervalMillis = 2 * 60 * 1000L
                val now = System.currentTimeMillis()
                val nextScanTarget = if (settings.lastCheckMillis > 0) {
                    var target = settings.lastCheckMillis + intervalMillis
                    while (target <= now) {
                        target += intervalMillis
                    }
                    target
                } else {
                    now + intervalMillis
                }
                val nextScanStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(nextScanTarget))
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
    // Formatear la fecha y hora usando la zona horaria America/Havana
    val timeFormatted = remember(notification.timestamp) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Havana")
        }
        sdf.format(Date(notification.timestamp))
    }
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

/** Representa un intervalo de tiempo (afectación u operación) con fecha de inicio y fin. */
data class TimeInterval(
    val startMillis: Long,
    val endMillis: Long,
    val isOngoing: Boolean
)

/** Estructura para almacenar las estadísticas de un circuito en la última semana. */
data class CircuitStats(
    val outageIntervals: List<TimeInterval>,
    val operationalIntervals: List<TimeInterval>,
    val outageMillis: Long,
    val operationalMillis: Long
)

/**
 * Calcula las estadísticas de la última semana (intervalos de afectación con sus fechas,
 * tiempo total sin luz, intervalos operativos con sus fechas y tiempo total con servicio)
 * para un circuito basándose estrictamente en las notificaciones del historial.
 */
private fun calculateCircuitStats(
    circuit: String,
    notifications: List<AppNotification>
): CircuitStats {
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000L

    val allCircuitNotifs = notifications.filter { notif ->
        MentionMatcher.findMatchingCircuits(notif.text, listOf(circuit)).isNotEmpty()
    }.sortedBy { it.timestamp }

    val outageIntervals = mutableListOf<TimeInterval>()
    val operationalIntervals = mutableListOf<TimeInterval>()

    if (allCircuitNotifs.isEmpty()) {
        return CircuitStats(outageIntervals, operationalIntervals, 0L, 0L)
    }

    val events = allCircuitNotifs.mapNotNull { notif ->
        val statuses = MentionMatcher.detectCircuitStatuses(notif.text, listOf(circuit))
        val statusType = statuses[circuit.uppercase()] ?: return@mapNotNull null
        Pair(notif.timestamp, statusType)
    }

    if (events.isEmpty()) {
        return CircuitStats(outageIntervals, operationalIntervals, 0L, 0L)
    }

    val priorEvents = events.filter { it.first < weekAgo }
    val recentEvents = events.filter { it.first >= weekAgo }

    var isCurrentlyAffected: Boolean? = null
    var lastStateTime: Long = weekAgo

    if (priorEvents.isNotEmpty()) {
        val lastPrior = priorEvents.last()
        isCurrentlyAffected = (lastPrior.second == MentionMatcher.StatusType.AFFECTED)
    } else if (recentEvents.isNotEmpty()) {
        val firstRecent = recentEvents.first()
        isCurrentlyAffected = (firstRecent.second == MentionMatcher.StatusType.AFFECTED)
        lastStateTime = firstRecent.first.coerceAtLeast(weekAgo)
    }

    var outageMillis = 0L
    var operationalMillis = 0L

    val eventsToProcess = if (priorEvents.isNotEmpty()) recentEvents else recentEvents.drop(1)
    var currentState = isCurrentlyAffected
    var currentIntervalStart = lastStateTime

    for ((eventTime, statusType) in eventsToProcess) {
        val boundedTime = eventTime.coerceAtLeast(weekAgo)
        val duration = boundedTime - lastStateTime

        if (duration > 0 && currentState != null) {
            if (currentState) {
                outageMillis += duration
            } else {
                operationalMillis += duration
            }
        }

        val newState = (statusType == MentionMatcher.StatusType.AFFECTED)
        if (currentState != null && currentState != newState) {
            if (currentState) {
                outageIntervals.add(TimeInterval(currentIntervalStart, boundedTime, false))
            } else {
                operationalIntervals.add(TimeInterval(currentIntervalStart, boundedTime, false))
            }
            currentIntervalStart = boundedTime
        } else if (currentState == null) {
            currentIntervalStart = boundedTime
        }

        currentState = newState
        lastStateTime = boundedTime
    }

    // Intervalo final hasta 'now'
    val finalDuration = now - lastStateTime
    if (finalDuration > 0 && currentState != null) {
        if (currentState) {
            outageMillis += finalDuration
            outageIntervals.add(TimeInterval(currentIntervalStart, now, true))
        } else {
            operationalMillis += finalDuration
            operationalIntervals.add(TimeInterval(currentIntervalStart, now, true))
        }
    } else if (currentState != null && currentIntervalStart < now) {
        if (currentState) {
            outageIntervals.add(TimeInterval(currentIntervalStart, now, true))
        } else {
            operationalIntervals.add(TimeInterval(currentIntervalStart, now, true))
        }
    }

    return CircuitStats(outageIntervals, operationalIntervals, outageMillis, operationalMillis)
}

/** Formatea una duración en milisegundos a formato legible en español (horas y minutos). */
private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours ${if (hours == 1L) "hora" else "horas"}${if (minutes > 0) " y $minutes min" else ""}"
    } else {
        "$minutes ${if (minutes == 1L) "minuto" else "minutos"}"
    }
}

/** Representa un intervalo unificado para la lista cronológica. */
data class UnifiedInterval(
    val startMillis: Long,
    val endMillis: Long,
    val isOngoing: Boolean,
    val isAffected: Boolean // true = sin servicio (🔴), false = operativo (🟢)
)

/** Formatea un intervalo unificado con fecha y hora en la zona horaria America/Havana. */
private fun formatUnifiedInterval(interval: UnifiedInterval): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Havana")
    }
    val startStr = sdf.format(Date(interval.startMillis))
    val endStr = if (interval.isOngoing) "Actualidad" else sdf.format(Date(interval.endMillis))
    return "$startStr → $endStr"
}
