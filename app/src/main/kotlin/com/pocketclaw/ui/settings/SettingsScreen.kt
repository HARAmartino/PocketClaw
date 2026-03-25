package com.pocketclaw.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketclaw.R
import com.pocketclaw.agent.scheduler.HeartbeatManager
import com.pocketclaw.util.DeepLinkHelper
import kotlin.math.roundToInt

/**
 * Settings screen for PocketClaw.
 *
 * Covers:
 * - LLM provider selection (OpenAI / Anthropic / Custom) with API key entry
 * - HITL provider selection (Telegram / Discord) with credential entry
 * - Daily token budget slider
 * - Auto-Pilot toggle
 * - Max iterations slider
 * - OEM battery settings deep-link
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_nav_back),
                        )
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

            // ── LLM Provider ──────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_llm_section)) {
                LlmProviderSelector(
                    selectedId = state.llmProviderId,
                    onSelect = viewModel::setLlmProvider,
                )
                Spacer(modifier = Modifier.height(8.dp))
                MaskedTextField(
                    label = stringResource(R.string.settings_api_key_label),
                    value = state.apiKey,
                    onValueChange = viewModel::setApiKey,
                    error = state.apiKeyError,
                )
                if (state.llmProviderId == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.customEndpointUrl,
                        onValueChange = viewModel::setCustomEndpointUrl,
                        label = { Text(stringResource(R.string.settings_custom_endpoint_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::saveApiKey,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                    FilledTonalButton(
                        onClick = viewModel::testConnection,
                        enabled = !state.isTestingConnection,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (state.isTestingConnection) stringResource(R.string.settings_testing)
                            else stringResource(R.string.settings_test_connection),
                        )
                    }
                }
                state.connectionTestResult?.let { result ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(result, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ── HITL Provider ─────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_hitl_section)) {
                HitlProviderSelector(
                    selectedId = state.hitlProviderId,
                    onSelect = viewModel::setHitlProvider,
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (state.hitlProviderId) {
                    "telegram" -> {
                        MaskedTextField(
                            label = stringResource(R.string.settings_bot_token_label),
                            value = state.botToken,
                            onValueChange = viewModel::setBotToken,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.telegramChatId,
                            onValueChange = viewModel::setTelegramChatId,
                            label = { Text(stringResource(R.string.settings_chat_id_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::saveBotToken, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                    "discord" -> {
                        MaskedTextField(
                            label = stringResource(R.string.settings_webhook_url_label),
                            value = state.discordWebhookUrl,
                            onValueChange = viewModel::setDiscordWebhookUrl,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::saveDiscordWebhookUrl,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                }
            }

            // ── Agent behaviour ───────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_agent_section)) {
                LabeledSlider(
                    label = stringResource(
                        R.string.settings_token_budget_label,
                        state.dailyTokenBudget,
                    ),
                    value = state.dailyTokenBudget.toFloat(),
                    onValueChange = { viewModel.setDailyTokenBudget(it.roundToInt()) },
                    valueRange = SettingsDefaults.BUDGET_MIN.toFloat()..SettingsDefaults.BUDGET_MAX.toFloat(),
                    steps = 0,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledSlider(
                    label = stringResource(
                        R.string.settings_max_iterations_label,
                        state.maxIterations,
                    ),
                    value = state.maxIterations.toFloat(),
                    onValueChange = { viewModel.setMaxIterations(it.roundToInt()) },
                    valueRange = SettingsDefaults.ITERATIONS_MIN.toFloat()..SettingsDefaults.ITERATIONS_MAX.toFloat(),
                    steps = SettingsDefaults.ITERATIONS_MAX - SettingsDefaults.ITERATIONS_MIN - 1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_auto_pilot_label))
                    Switch(
                        checked = state.isAutoPilotEnabled,
                        onCheckedChange = viewModel::setAutoPilot,
                    )
                }
            }

            // ── Battery ───────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_battery_section)) {
                FilledTonalButton(
                    onClick = { DeepLinkHelper.openOemBatterySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_oem_battery_button))
                }
            }

            // ── Notification Triggers ─────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_notification_triggers_section)) {
                NotificationTriggerSection(
                    packages = state.notificationTriggerPackages,
                    input = state.notificationTriggerInput,
                    onInputChange = viewModel::setNotificationTriggerInput,
                    onAdd = viewModel::addNotificationTriggerPackage,
                    onRemove = viewModel::removeNotificationTriggerPackage,
                )
            }

            // ── Heartbeat ─────────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.settings_heartbeat_section)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_heartbeat_enabled_label))
                    Switch(
                        checked = state.isHeartbeatEnabled,
                        onCheckedChange = viewModel::setHeartbeatEnabled,
                    )
                }
                if (state.isHeartbeatEnabled) {
                    LabeledSlider(
                        label = stringResource(
                            R.string.settings_heartbeat_interval_label,
                            state.heartbeatIntervalMinutes,
                        ),
                        value = state.heartbeatIntervalMinutes.toFloat(),
                        onValueChange = { viewModel.setHeartbeatIntervalMinutes(it.roundToInt()) },
                        valueRange = HeartbeatManager.MIN_INTERVAL_MINUTES.toFloat()..
                            HeartbeatManager.MAX_INTERVAL_MINUTES.toFloat(),
                        steps = ((HeartbeatManager.MAX_INTERVAL_MINUTES -
                            HeartbeatManager.MIN_INTERVAL_MINUTES) / 15) - 1,
                    )
                    OutlinedTextField(
                        value = state.heartbeatPrompt,
                        onValueChange = viewModel::setHeartbeatPrompt,
                        label = { Text(stringResource(R.string.settings_heartbeat_prompt_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmProviderSelector(selectedId: String, onSelect: (String) -> Unit) {
    ProviderDropdown(
        label = stringResource(R.string.settings_llm_provider_label),
        options = LLM_PROVIDER_OPTIONS.map { it.id to it.displayName },
        selectedId = selectedId,
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HitlProviderSelector(selectedId: String, onSelect: (String) -> Unit) {
    ProviderDropdown(
        label = stringResource(R.string.settings_hitl_provider_label),
        options = HITL_PROVIDER_OPTIONS.map { it.id to it.displayName },
        selectedId = selectedId,
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedDisplay = options.firstOrNull { it.first == selectedId }?.second ?: selectedId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedDisplay,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MaskedTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotificationTriggerSection(
    packages: Set<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.settings_notification_triggers_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAdd) {
                Text(stringResource(R.string.settings_notification_triggers_add))
            }
        }
        if (packages.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_notification_triggers_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            packages.sorted().forEach { pkg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(pkg) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(
                                R.string.settings_notification_triggers_remove_cd,
                                pkg,
                            ),
                        )
                    }
                }
            }
        }
    }
}
