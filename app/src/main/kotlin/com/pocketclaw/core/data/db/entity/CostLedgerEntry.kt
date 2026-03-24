package com.pocketclaw.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Record of a single LLM API call for cost tracking and budget enforcement.
 * The daily token sum is checked by [com.pocketclaw.agent.orchestrator.AgentOrchestrator]
 * after every LLM call.
 */
@Entity(
    tableName = "cost_ledger",
    indices = [Index("timestampMs"), Index("taskId")],
)
data class CostLedgerEntry(
    @PrimaryKey val callId: String,
    val taskId: String,
    val providerId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val timestampMs: Long,
)
