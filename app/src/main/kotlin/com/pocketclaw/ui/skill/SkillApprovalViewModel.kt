package com.pocketclaw.ui.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketclaw.agent.skill.DiscoveredSkill
import com.pocketclaw.agent.skill.SkillDiscoverer
import com.pocketclaw.core.data.db.dao.SkillTrustStoreDao
import com.pocketclaw.core.data.db.entity.SkillTrustEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the skill-approval flow.
 *
 * @param pendingSkills  Skills registered in the trust store but not yet approved.
 * @param isLoading      True while [SkillDiscoverer.discoverSkills] is running.
 */
data class SkillApprovalUiState(
    val pendingSkills: List<DiscoveredSkill> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * ViewModel backing [SkillApprovalDialog].
 *
 * Discovers installed skill apps via [SkillDiscoverer.discoverSkills] to trigger
 * logging of untrusted packages, then queries [SkillTrustStoreDao] for entries
 * that are present but not yet approved. The user can approve or reject each
 * pending skill; the decision is persisted via [SkillTrustStoreDao].
 */
@HiltViewModel
class SkillApprovalViewModel @Inject constructor(
    private val skillDiscoverer: SkillDiscoverer,
    private val skillTrustStoreDao: SkillTrustStoreDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillApprovalUiState())
    val uiState: StateFlow<SkillApprovalUiState> = _uiState.asStateFlow()

    init {
        loadPendingSkills()
    }

    /**
     * Loads skills that are registered in the trust store but not yet approved.
     * Triggers [SkillLoader.discoverSkills] first so that any newly installed
     * skill apps are logged and can be added to the trust store manually or via
     * future auto-discovery.
     */
    fun loadPendingSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            skillDiscoverer.discoverSkills()
            val unapproved = skillTrustStoreDao.getAllUnapproved()
            val pendingSkills = unapproved.map { entry ->
                DiscoveredSkill(
                    packageName = entry.packageName,
                    displayName = entry.packageName,
                    certSha256 = entry.certSha256,
                    capabilities = entry.capabilities,
                )
            }
            _uiState.update { it.copy(pendingSkills = pendingSkills, isLoading = false) }
        }
    }

    /**
     * Approves the skill identified by [packageName].
     * Updates the [SkillTrustEntry] to [SkillTrustEntry.isTrusted] = true.
     */
    fun approveSkill(packageName: String) {
        viewModelScope.launch {
            val entry = skillTrustStoreDao.get(packageName) ?: return@launch
            skillTrustStoreDao.upsert(
                entry.copy(
                    isTrusted = true,
                    approvedByUser = true,
                    approvedAtMs = System.currentTimeMillis(),
                ),
            )
            _uiState.update { state ->
                state.copy(pendingSkills = state.pendingSkills.filterNot { it.packageName == packageName })
            }
        }
    }

    /**
     * Rejects the skill identified by [packageName].
     * Sets [SkillTrustEntry.isTrusted] = false and [SkillTrustEntry.approvedByUser] = false,
     * making the explicit rejection durable across app restarts.
     */
    fun rejectSkill(packageName: String) {
        viewModelScope.launch {
            val entry = skillTrustStoreDao.get(packageName) ?: return@launch
            skillTrustStoreDao.upsert(
                entry.copy(
                    isTrusted = false,
                    approvedByUser = false,
                ),
            )
            _uiState.update { state ->
                state.copy(pendingSkills = state.pendingSkills.filterNot { it.packageName == packageName })
            }
        }
    }
}
