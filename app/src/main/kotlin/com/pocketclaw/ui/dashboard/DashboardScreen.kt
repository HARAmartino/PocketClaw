package com.pocketclaw.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketclaw.R
import com.pocketclaw.core.data.db.entity.TaskType
import com.pocketclaw.core.data.db.entity.TimelineEntry
import java.util.Locale
import java.util.UUID

// ── Color palette for thermal states ─────────────────────────────────────────
private val ThermalGreen = Color(0xFF2E7D32)
private val ThermalAmber = Color(0xFFF57F17)
private val ThermalRed = Color(0xFFC62828)

/**
 * Main dashboard screen.
 *
 * Displays:
 * - Start / Stop agent toggle
 * - Real-time status card (task name, step, elapsed time, LLM provider, daily cost)
 * - Thermal & battery widget (green / amber / red)
 * - Continuous-charging advisory banner
 * - Auto-Pilot warning banner
 * - Scrollable timeline viewer
 * - Trigger management (add / edit / delete)
 *
 * @param onNavigateToTerminal callback for the top-bar "Terminal" toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTerminal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val triggers by viewModel.triggers.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Trigger dialog state
    var showTriggerDialog by rememberSaveable { mutableStateOf(false) }
    var editingTriggerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_open_settings),
                        )
                    }
                    TextButton(onClick = onNavigateToTerminal) {
                        Text(stringResource(R.string.terminal_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Banners ──────────────────────────────────────────────────────

            if (state.showChargingAdvisory) {
                BannerCard(
                    text = stringResource(R.string.dashboard_charging_advisory),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            if (state.isAutoPilotEnabled) {
                BannerCard(
                    text = stringResource(R.string.dashboard_auto_pilot_warning),
                    color = Color(0xFFFFF9C4),
                    textColor = Color(0xFF5D4037),
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = ThermalAmber) },
                )
            }

            if (state.budgetExhausted) {
                BannerCard(
                    text = stringResource(R.string.dashboard_budget_exhausted),
                    color = MaterialTheme.colorScheme.errorContainer,
                    textColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = ThermalRed) },
                )
            }

            if (state.loopDetected) {
                BannerCard(
                    text = stringResource(R.string.dashboard_loop_detected),
                    color = MaterialTheme.colorScheme.errorContainer,
                    textColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = ThermalRed) },
                )
            }

            // ── Start / Stop toggle ──────────────────────────────────────────

            StartStopCard(
                isRunning = state.isRunning,
                onStart = { viewModel.startAgent(context) },
                onStop = { viewModel.stopAgent(context) },
            )

            // ── Status card ──────────────────────────────────────────────────

            StatusCard(state = state)

            // ── Thermal & battery widget ─────────────────────────────────────

            ThermalBatteryCard(
                thermalColor = state.thermalColor,
                batteryPercent = state.batteryPercent,
            )

            // ── Auto-Pilot toggle ────────────────────────────────────────────

            AutoPilotRow(
                isEnabled = state.isAutoPilotEnabled,
                onToggle = { viewModel.setAutoPilot(it) },
            )

            // ── Timeline viewer ──────────────────────────────────────────────

            TimelineSection(entries = state.recentTimeline)

            // ── Trigger management ───────────────────────────────────────────

            TriggerSection(
                triggers = triggers,
                onAdd = {
                    editingTriggerIndex = null
                    showTriggerDialog = true
                },
                onEdit = { idx ->
                    editingTriggerIndex = idx
                    showTriggerDialog = true
                },
                onDelete = { idx -> viewModel.deleteTrigger(idx) },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Trigger add / edit dialog ────────────────────────────────────────────

    if (showTriggerDialog) {
        val initial = editingTriggerIndex?.let { triggers.getOrNull(it) }
        TriggerDialog(
            initial = initial,
            onDismiss = { showTriggerDialog = false },
            onConfirm = { trigger ->
                val idx = editingTriggerIndex
                if (idx != null) {
                    viewModel.updateTrigger(idx, trigger)
                } else {
                    viewModel.addTrigger(trigger)
                }
                showTriggerDialog = false
            },
        )
    }
}

// ── Component: banners ────────────────────────────────────────────────────────

@Composable
private fun BannerCard(
    text: String,
    color: Color,
    textColor: Color,
    icon: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.invoke()
            Text(text = text, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ── Component: Start/Stop card ────────────────────────────────────────────────

@Composable
private fun StartStopCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val targetColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "startStopColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = animatedColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isRunning) "Agent Running" else "Agent Stopped",
                style = MaterialTheme.typography.titleMedium,
            )
            if (isRunning) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_stop))
                }
            } else {
                Button(onClick = onStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_start))
                }
            }
        }
    }
}

// ── Component: Status card ────────────────────────────────────────────────────

@Composable
private fun StatusCard(state: DashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Status", style = MaterialTheme.typography.titleSmall)
            StatusRow("Task", state.taskTitle ?: "—")
            StatusRow("Step", if (state.taskTitle != null) "${state.taskStep} / ${state.taskMaxSteps}" else "—")
            StatusRow("Elapsed", formatElapsed(state.elapsedMs))
            StatusRow("Provider", state.llmProviderDisplayName.ifEmpty { "—" })
            StatusRow("Daily Cost", if (state.dailyCostUsd > 0) String.format(java.util.Locale.US, "$%.4f", state.dailyCostUsd) else "—")
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0L) return "—"
    val seconds = ms / 1_000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes % 60)
    else "%dm %02ds".format(minutes, seconds % 60)
}

// ── Component: Thermal & battery widget ───────────────────────────────────────

@Composable
private fun ThermalBatteryCard(thermalColor: ThermalColor, batteryPercent: Int) {
    val indicatorColor = when (thermalColor) {
        ThermalColor.GREEN -> ThermalGreen
        ThermalColor.AMBER -> ThermalAmber
        ThermalColor.RED -> ThermalRed
    }
    val animatedIndicator by animateColorAsState(
        targetValue = indicatorColor,
        animationSpec = tween(durationMillis = 600),
        label = "thermalColor",
    )
    val batteryColor = when {
        batteryPercent <= 20 -> ThermalRed
        batteryPercent <= 40 -> ThermalAmber
        else -> ThermalGreen
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Thermal indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Thermostat,
                    contentDescription = "Thermal",
                    tint = animatedIndicator,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = when (thermalColor) {
                        ThermalColor.GREEN -> "Normal"
                        ThermalColor.AMBER -> "Warm"
                        ThermalColor.RED -> "Hot"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = animatedIndicator,
                )
            }

            // Battery indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.BatteryAlert,
                    contentDescription = "Battery",
                    tint = batteryColor,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "$batteryPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = batteryColor,
                )
            }
        }
    }
}

// ── Component: Auto-Pilot toggle ──────────────────────────────────────────────

@Composable
private fun AutoPilotRow(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Auto-Pilot", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Bypasses HITL for standard actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    }
}

// ── Component: Timeline section ───────────────────────────────────────────────

@Composable
private fun TimelineSection(entries: List<TimelineEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Timeline", style = MaterialTheme.typography.titleSmall)
        if (entries.isEmpty()) {
            Text(
                "No timeline entries yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Show last 20 entries; LazyColumn inside a scrollable Column requires a fixed height
            Box(modifier = Modifier.height(240.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entries.takeLast(20).reversed()) { entry ->
                        TimelineEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryRow(entry: TimelineEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Step ${entry.stepIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.screenshotPath != null) {
                        Text(
                            "📷",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        entry.actionType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                entry.reasoning.take(120),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ── Component: Trigger section ────────────────────────────────────────────────

@Composable
private fun TriggerSection(
    triggers: List<TriggerConfig>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Triggers", style = MaterialTheme.typography.titleSmall)
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add trigger", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (triggers.isEmpty()) {
            Text(
                "No triggers configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            triggers.forEachIndexed { idx, trigger ->
                TriggerRow(
                    trigger = trigger,
                    onEdit = { onEdit(idx) },
                    onDelete = { onDelete(idx) },
                )
            }
        }
    }
}

@Composable
private fun TriggerRow(
    trigger: TriggerConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trigger.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    trigger.taskType.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit trigger")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete trigger", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Trigger add / edit dialog ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerDialog(
    initial: TriggerConfig?,
    onDismiss: () -> Unit,
    onConfirm: (TriggerConfig) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var goalPrompt by rememberSaveable { mutableStateOf(initial?.goalPrompt ?: "") }
    var taskType by rememberSaveable { mutableStateOf(initial?.taskType ?: TaskType.SCHEDULED) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Trigger" else "Edit Trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Task type dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = taskType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        TaskType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    taskType = type
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = goalPrompt,
                    onValueChange = { goalPrompt = it },
                    label = { Text("Goal prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && goalPrompt.isNotBlank()) {
                        onConfirm(
                            TriggerConfig(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                taskType = taskType,
                                title = title.trim(),
                                goalPrompt = goalPrompt.trim(),
                            ),
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
