package com.pocketclaw.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder of runtime state emitted by [AgentForegroundService].
 * Observed by [com.pocketclaw.ui.dashboard.DashboardViewModel] to reflect live
 * battery, thermal, charging, and agent-event state in the Dashboard UI.
 *
 * All StateFlows are updated by [AgentForegroundService] on the system broadcast
 * thread and consumed by the ViewModel on the main dispatcher.
 */
@Singleton
class AgentServiceState @Inject constructor() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Battery temperature multiplied by 10 (e.g. 370 = 37.0°C). */
    private val _thermalCelsiusTenths = MutableStateFlow(0)
    val thermalCelsiusTenths: StateFlow<Int> = _thermalCelsiusTenths.asStateFlow()

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    /** Epoch-ms at which continuous charging started; null when not charging. */
    private val _chargingStartMs = MutableStateFlow<Long?>(null)
    val chargingStartMs: StateFlow<Long?> = _chargingStartMs.asStateFlow()

    /** Set to true by [com.pocketclaw.agent.orchestrator.AgentOrchestrator] when the daily token budget is exhausted. */
    private val _budgetExhausted = MutableStateFlow(false)
    val budgetExhausted: StateFlow<Boolean> = _budgetExhausted.asStateFlow()

    /** Set to true by [com.pocketclaw.agent.orchestrator.AgentOrchestrator] when a task-execution loop is detected. */
    private val _loopDetected = MutableStateFlow(false)
    val loopDetected: StateFlow<Boolean> = _loopDetected.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setThermal(celsiusTenths: Int) {
        _thermalCelsiusTenths.value = celsiusTenths
    }

    fun setBattery(percent: Int) {
        _batteryPercent.value = percent
    }

    fun setCharging(charging: Boolean) {
        _isCharging.value = charging
    }

    fun setChargingStartMs(ms: Long?) {
        _chargingStartMs.value = ms
    }

    fun setBudgetExhausted(exhausted: Boolean) {
        _budgetExhausted.value = exhausted
    }

    fun setLoopDetected(detected: Boolean) {
        _loopDetected.value = detected
    }
}
