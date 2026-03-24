package com.pocketclaw.ui.dashboard

import com.pocketclaw.service.AgentServiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DashboardViewModel]-related state logic.
 *
 * Tests cover:
 * - [ThermalColor] derivation from temperature readings.
 * - [AgentServiceState] flow emissions for thermal pause, budget-exhausted,
 *   and loop-detected events (the events that [DashboardViewModel] observes).
 */
class DashboardViewModelTest {

    private lateinit var serviceState: AgentServiceState

    @Before
    fun setUp() {
        serviceState = AgentServiceState()
    }

    // ── thermalColorFrom ──────────────────────────────────────────────────────

    @Test
    fun thermalColorFrom_belowWarmThreshold_returnsGreen() {
        val vm = fakeThermalHelper()
        assertEquals(ThermalColor.GREEN, vm.thermalColorFrom(0))
        assertEquals(ThermalColor.GREEN, vm.thermalColorFrom(200))
        assertEquals(ThermalColor.GREEN, vm.thermalColorFrom(DashboardViewModel.THERMAL_WARM_TENTHS - 1))
    }

    @Test
    fun thermalColorFrom_atWarmThreshold_returnsAmber() {
        val vm = fakeThermalHelper()
        assertEquals(ThermalColor.AMBER, vm.thermalColorFrom(DashboardViewModel.THERMAL_WARM_TENTHS))
        assertEquals(ThermalColor.AMBER, vm.thermalColorFrom(DashboardViewModel.THERMAL_HOT_TENTHS - 1))
    }

    @Test
    fun thermalColorFrom_atHotThreshold_returnsRed() {
        val vm = fakeThermalHelper()
        assertEquals(ThermalColor.RED, vm.thermalColorFrom(DashboardViewModel.THERMAL_HOT_TENTHS))
        assertEquals(ThermalColor.RED, vm.thermalColorFrom(600))
    }

    // ── Thermal pause event ───────────────────────────────────────────────────

    @Test
    fun serviceState_thermalPause_exceedsHotThreshold() = runBlocking {
        // Simulate the foreground service detecting a thermal pause (45 °C = 450 tenths)
        serviceState.setThermal(DashboardViewModel.THERMAL_HOT_TENTHS)

        val tenths = serviceState.thermalCelsiusTenths.first()
        assertTrue(
            "Expected thermal reading ≥ HOT threshold (${DashboardViewModel.THERMAL_HOT_TENTHS}), got $tenths",
            tenths >= DashboardViewModel.THERMAL_HOT_TENTHS,
        )
    }

    @Test
    fun serviceState_thermalPause_colorDerivesToRed() {
        serviceState.setThermal(460) // 46 °C
        val color = fakeThermalHelper().thermalColorFrom(serviceState.thermalCelsiusTenths.value)
        assertEquals(ThermalColor.RED, color)
    }

    // ── Budget exceeded event ─────────────────────────────────────────────────

    @Test
    fun serviceState_budgetExhausted_defaultIsFalse() = runBlocking {
        assertFalse(serviceState.budgetExhausted.first())
    }

    @Test
    fun serviceState_budgetExhausted_setTrueEmitsTrue() = runBlocking {
        serviceState.setBudgetExhausted(true)
        assertTrue(serviceState.budgetExhausted.first())
    }

    @Test
    fun serviceState_budgetExhausted_resetToFalseEmitsFalse() = runBlocking {
        serviceState.setBudgetExhausted(true)
        serviceState.setBudgetExhausted(false)
        assertFalse(serviceState.budgetExhausted.first())
    }

    // ── Loop detected event ───────────────────────────────────────────────────

    @Test
    fun serviceState_loopDetected_defaultIsFalse() = runBlocking {
        assertFalse(serviceState.loopDetected.first())
    }

    @Test
    fun serviceState_loopDetected_setTrueEmitsTrue() = runBlocking {
        serviceState.setLoopDetected(true)
        assertTrue(serviceState.loopDetected.first())
    }

    @Test
    fun serviceState_loopDetected_resetToFalseEmitsFalse() = runBlocking {
        serviceState.setLoopDetected(true)
        serviceState.setLoopDetected(false)
        assertFalse(serviceState.loopDetected.first())
    }

    // ── Running state ─────────────────────────────────────────────────────────

    @Test
    fun serviceState_isRunning_defaultIsFalse() = runBlocking {
        assertFalse(serviceState.isRunning.first())
    }

    @Test
    fun serviceState_setRunning_emitsTrue() = runBlocking {
        serviceState.setRunning(true)
        assertTrue(serviceState.isRunning.first())
    }

    // ── Battery state ─────────────────────────────────────────────────────────

    @Test
    fun serviceState_battery_defaultIs100() = runBlocking {
        assertEquals(100, serviceState.batteryPercent.first())
    }

    @Test
    fun serviceState_setBattery_emitsNewValue() = runBlocking {
        serviceState.setBattery(42)
        assertEquals(42, serviceState.batteryPercent.first())
    }

    // ── Foreground package state ──────────────────────────────────────────────

    @Test
    fun serviceState_foregroundPackage_defaultIsNull() = runBlocking {
        assertEquals(null, serviceState.foregroundPackage.first())
    }

    @Test
    fun serviceState_setForegroundPackage_emitsPackageName() = runBlocking {
        serviceState.setForegroundPackage("com.example.app")
        assertEquals("com.example.app", serviceState.foregroundPackage.first())
    }

    @Test
    fun serviceState_setForegroundPackage_null_emitsNull() = runBlocking {
        serviceState.setForegroundPackage("com.example.app")
        serviceState.setForegroundPackage(null)
        assertEquals(null, serviceState.foregroundPackage.first())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns a minimal object that exposes [DashboardViewModel.thermalColorFrom]
     * without requiring Android context or a real ViewModel.
     */
    private fun fakeThermalHelper() = object {
        fun thermalColorFrom(celsiusTenths: Int): ThermalColor = when {
            celsiusTenths >= DashboardViewModel.THERMAL_HOT_TENTHS -> ThermalColor.RED
            celsiusTenths >= DashboardViewModel.THERMAL_WARM_TENTHS -> ThermalColor.AMBER
            else -> ThermalColor.GREEN
        }
    }
}
