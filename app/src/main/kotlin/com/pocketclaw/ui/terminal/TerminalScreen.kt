package com.pocketclaw.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketclaw.R
import com.pocketclaw.core.data.db.entity.TimelineEntry
import com.pocketclaw.ui.dashboard.DashboardViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Terminal colour scheme (dark-terminal aesthetic)
private val TerminalBackground = Color(0xFF1A1A2E)
private val TerminalText = Color(0xFF00FF9F)
private val TerminalMuted = Color(0xFF8892A4)
private val TerminalDivider = Color(0xFF2D2D44)

/**
 * Power-user terminal screen.
 *
 * Displays:
 * - Real-time JSON stream of [TimelineEntry] objects for the active task.
 * - Live [CompressedDomTree][com.pocketclaw.agent.llm.schema.CompressedDomTree] view.
 * - Manual command injection text field (bypasses SuspicionScorer, still subject to ActionValidator).
 * - Toggle button in the top bar to switch back to the Dashboard.
 *
 * @param onNavigateToDashboard callback for the top-bar toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val compressedDom by viewModel.compressedDom.collectAsStateWithLifecycle()
    val pendingCommand by viewModel.pendingCommand.collectAsStateWithLifecycle()
    val timelineEntries = uiState.recentTimeline

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terminal_title)) },
                actions = {
                    TextButton(onClick = onNavigateToDashboard) {
                        Text(stringResource(R.string.dashboard_title))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── JSON timeline stream ───────────────────────────────────────

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBackground),
            ) {
                if (timelineEntries.isEmpty()) {
                    Text(
                        text = "// No timeline entries",
                        color = TerminalMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    TimelineJsonStream(entries = timelineEntries)
                }
            }

            HorizontalDivider(color = TerminalDivider)

            // ── CompressedDomTree view ─────────────────────────────────────

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12122A)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "// DOM Tree",
                        color = TerminalMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = compressedDom.ifEmpty { "// No DOM snapshot available" },
                        color = TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            HorizontalDivider(color = TerminalDivider)

            // ── Command injection input ────────────────────────────────────

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pendingCommand,
                    onValueChange = { viewModel.updatePendingCommand(it) },
                    placeholder = { Text(stringResource(R.string.terminal_command_hint), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                IconButton(
                    onClick = { viewModel.injectCommand() },
                    enabled = pendingCommand.isNotBlank(),
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Inject command",
                    )
                }
            }
        }
    }
}

// ── JSON stream composable ────────────────────────────────────────────────────

@Composable
private fun TimelineJsonStream(entries: List<TimelineEntry>) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest entry
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries) { entry ->
            TimelineEntryJson(entry)
        }
    }
}

@Composable
private fun TimelineEntryJson(entry: TimelineEntry) {
    val json = remember(entry.id) { encodeEntryToJson(entry) }
    Text(
        text = json,
        color = TerminalText,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(2.dp))
}

private fun encodeEntryToJson(entry: TimelineEntry): String {
    val obj = buildJsonObject {
        put("id", entry.id)
        put("taskId", entry.taskId)
        put("stepIndex", entry.stepIndex)
        put("taskType", entry.taskType)
        put("reasoning", entry.reasoning)
        put("actionType", entry.actionType)
        put("validationResult", entry.validationResult)
        put("timestampMs", entry.timestampMs)
        entry.screenshotPath?.let { put("screenshotPath", it) }
    }
    return Json { prettyPrint = true }.encodeToString(obj)
}
