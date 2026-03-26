package com.pocketclaw.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketclaw.agent.orchestrator.AgentOrchestrator
import com.pocketclaw.agent.llm.provider.LlmProvider
import com.pocketclaw.core.data.db.dao.CostLedgerDao
import com.pocketclaw.core.data.db.dao.TaskJournalDao
import com.pocketclaw.core.data.db.dao.TimelineEntryDao
import com.pocketclaw.core.data.db.entity.TaskJournalEntry
import com.pocketclaw.core.data.db.entity.TaskStatus
import com.pocketclaw.core.data.db.entity.TaskType
import com.pocketclaw.core.data.db.entity.TimelineEntry
import com.pocketclaw.service.AgentForegroundService
import com.pocketclaw.service.AgentServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ── UI state models ────────────────────────────────────────────────────────────

/** Color category for the thermal indicator widget. */
enum class ThermalColor { GREEN, AMBER, RED }

/**
 * Snapshot of all state needed by [DashboardScreen].
 */
data class DashboardUiState(
    val isRunning: Boolean = false,
    val taskTitle: String? = null,
    val taskStep: Int = 0,
    val taskMaxSteps: Int = AgentTaskDefaults.MAX_ITERATIONS,
    val elapsedMs: Long = 0L,
    val llmProviderDisplayName: String = "",
    val dailyCostUsd: Double = 0.0,
    val thermalColor: ThermalColor = ThermalColor.GREEN,
    val batteryPercent: Int = 100,
    val showChargingAdvisory: Boolean = false,
    val isAutoPilotEnabled: Boolean = false,
    val recentTimeline: List<TimelineEntry> = emptyList(),
    val recentTasks: List<TaskJournalEntry> = emptyList(),
    val budgetExhausted: Boolean = false,
    val loopDetected: Boolean = false,
    /** Package name of the app currently in the foreground, or null if unknown. */
    val foregroundPackage: String? = null,
)

/** Configuration for a user-defined task trigger. */
data class TriggerConfig(
    val id: String = UUID.randomUUID().toString(),
    val taskType: TaskType,
    val title: String,
    val goalPrompt: String,
    val scheduleIntervalMs: Long? = null,
    val ipcAction: String? = null,
)

private object AgentTaskDefaults {
    const val MAX_ITERATIONS = 50
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskJournalDao: TaskJournalDao,
    private val costLedgerDao: CostLedgerDao,
    private val timelineEntryDao: TimelineEntryDao,
    val serviceState: AgentServiceState,
    private val llmProvider: LlmProvider,
    private val dataStore: DataStore<Preferences>,
    private val orchestrator: AgentOrchestrator,
) : ViewModel() {

    companion object {
        /** 38 °C — amber threshold (battery × 10 encoding). */
        internal const val THERMAL_WARM_TENTHS = 380

        /** 45 °C — red / auto-pause threshold. */
        internal const val THERMAL_HOT_TENTHS = 450

        /** Continuous-charging advisory after 2 h. */
        private const val CHARGING_ADVISORY_MS = 2L * 60L * 60L * 1_000L

        /** Milliseconds in one calendar day, used for daily cost aggregation. */
        private const val MILLISECONDS_PER_DAY = 86_400_000L

        /** DataStore key for Auto-Pilot preference. */
        internal val AUTO_PILOT_KEY = booleanPreferencesKey("auto_pilot_enabled")
    }

    // ── Derived UI state ──────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(DashboardUiState(llmProviderDisplayName = llmProvider.displayName))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // ── In-memory trigger list (future: persist to a Trigger Room entity) ─────
    private val _triggers = MutableStateFlow<List<TriggerConfig>>(emptyList())
    val triggers: StateFlow<List<TriggerConfig>> = _triggers.asStateFlow()

    // ── Terminal state ────────────────────────────────────────────────────────
    private val _compressedDom = MutableStateFlow("")
    val compressedDom: StateFlow<String> = _compressedDom.asStateFlow()

    private val _pendingCommand = MutableStateFlow("")
    val pendingCommand: StateFlow<String> = _pendingCommand.asStateFlow()

    init {
        observeRunning()
        observeThermal()
        observeBattery()
        observeCharging()
        observeBudget()
        observeLoop()
        observeForegroundPackage()
        observeRecentTasks()
        observeDailyCost()
        observeAutoPilot()
    }

    // ── Flow observers ────────────────────────────────────────────────────────

    private fun observeRunning() {
        viewModelScope.launch {
            serviceState.isRunning.collect { running ->
                _uiState.update { it.copy(isRunning = running) }
            }
        }
        // When the agent is running, follow the active task's timeline
        viewModelScope.launch {
            serviceState.isRunning
                .flatMapLatest { running ->
                    if (running) taskJournalDao.observeRecent(1) else flowOf(emptyList())
                }
                .flatMapLatest { tasks ->
                    val task = tasks.firstOrNull { it.status == TaskStatus.EXECUTING }
                        ?: tasks.firstOrNull()
                    if (task != null) {
                        _uiState.update {
                            it.copy(
                                taskTitle = task.title,
                                taskStep = task.iterationCount,
                                elapsedMs = System.currentTimeMillis() - task.createdAtMs,
                            )
                        }
                        timelineEntryDao.observeForTask(task.taskId)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { entries ->
                    _uiState.update { it.copy(recentTimeline = entries) }
                }
        }
    }

    private fun observeThermal() {
        viewModelScope.launch {
            serviceState.thermalCelsiusTenths.collect { tenths ->
                _uiState.update { it.copy(thermalColor = thermalColorFrom(tenths)) }
            }
        }
    }

    private fun observeBattery() {
        viewModelScope.launch {
            serviceState.batteryPercent.collect { pct ->
                _uiState.update { it.copy(batteryPercent = pct) }
            }
        }
    }

    private fun observeCharging() {
        viewModelScope.launch {
            combine(serviceState.isCharging, serviceState.chargingStartMs) { charging, startMs ->
                val advisory = charging &&
                    startMs != null &&
                    (System.currentTimeMillis() - startMs) >= CHARGING_ADVISORY_MS
                advisory
            }.collect { advisory ->
                _uiState.update { it.copy(showChargingAdvisory = advisory) }
            }
        }
    }

    private fun observeBudget() {
        viewModelScope.launch {
            serviceState.budgetExhausted.collect { exhausted ->
                _uiState.update { it.copy(budgetExhausted = exhausted) }
            }
        }
    }

    private fun observeLoop() {
        viewModelScope.launch {
            serviceState.loopDetected.collect { detected ->
                _uiState.update { it.copy(loopDetected = detected) }
            }
        }
    }

    private fun observeForegroundPackage() {
        viewModelScope.launch {
            serviceState.foregroundPackage.collect { pkg ->
                _uiState.update { it.copy(foregroundPackage = pkg) }
            }
        }
    }

    private fun observeRecentTasks() {
        viewModelScope.launch {
            taskJournalDao.observeRecent(20).collect { tasks ->
                _uiState.update { it.copy(recentTasks = tasks) }
            }
        }
    }

    private fun observeDailyCost() {
        viewModelScope.launch {
            costLedgerDao.observeRecent(1_000).collect { entries ->
                val startOfDay = System.currentTimeMillis() - MILLISECONDS_PER_DAY
                val dailyCost = entries
                    .filter { it.timestampMs >= startOfDay }
                    .sumOf { it.estimatedCostUsd }
                _uiState.update { it.copy(dailyCostUsd = dailyCost) }
            }
        }
    }

    private fun observeAutoPilot() {
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> prefs[AUTO_PILOT_KEY] ?: false }
                .collect { enabled ->
                    _uiState.update { it.copy(isAutoPilotEnabled = enabled) }
                }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun startAgent(context: Context) {
        orchestrator.resetScope()
        serviceState.setRunning(true)
        context.startForegroundService(
            Intent(context, AgentForegroundService::class.java).apply {
                action = AgentForegroundService.ACTION_START
            },
        )
    }

    fun stopAgent(context: Context) {
        serviceState.setRunning(false)
        context.startService(
            Intent(context, AgentForegroundService::class.java).apply {
                action = AgentForegroundService.ACTION_STOP
            },
        )
    }

    fun setAutoPilot(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[AUTO_PILOT_KEY] = enabled }
        }
    }

    // ── Trigger management ────────────────────────────────────────────────────

    fun addTrigger(trigger: TriggerConfig) {
        _triggers.update { it + trigger }
    }

    fun updateTrigger(index: Int, trigger: TriggerConfig) {
        _triggers.update { list ->
            list.toMutableList().apply { this[index] = trigger }
        }
    }

    fun deleteTrigger(index: Int) {
        _triggers.update { list ->
            list.toMutableList().apply { removeAt(index) }
        }
    }

    // ── Terminal API ──────────────────────────────────────────────────────────

    fun updatePendingCommand(cmd: String) {
        _pendingCommand.value = cmd
    }

    /**
     * Injects [pendingCommand] into the next LLM prompt, bypassing SuspicionScorer
     * (as allowed for power-user Terminal mode) but still subject to ActionValidator.
     */
    fun injectCommand() {
        val cmd = _pendingCommand.value.trim()
        if (cmd.isEmpty()) return
        // In a future phase the command is handed to AgentOrchestrator.runTask()
        // for the next iteration. For now, the state is cleared and the command
        // is available for the calling composable to act on.
        _pendingCommand.value = ""
    }

    fun updateCompressedDom(dom: String) {
        _compressedDom.value = dom
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Maps a raw temperature reading (°C × 10) to its [ThermalColor] category. */
    internal fun thermalColorFrom(celsiusTenths: Int): ThermalColor = when {
        celsiusTenths >= THERMAL_HOT_TENTHS -> ThermalColor.RED
        celsiusTenths >= THERMAL_WARM_TENTHS -> ThermalColor.AMBER
        else -> ThermalColor.GREEN
    }
}
