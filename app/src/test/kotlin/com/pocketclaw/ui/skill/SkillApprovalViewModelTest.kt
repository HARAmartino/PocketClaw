package com.pocketclaw.ui.skill

import com.pocketclaw.agent.skill.DiscoveredSkill
import com.pocketclaw.agent.skill.SkillDiscoverer
import com.pocketclaw.core.data.db.dao.SkillTrustStoreDao
import com.pocketclaw.core.data.db.entity.SkillTrustEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [SkillApprovalViewModel] covering:
 *
 * - [SkillApprovalViewModel.loadPendingSkills]: surfaces unapproved DAO entries.
 * - [SkillApprovalViewModel.approveSkill]: sets isTrusted=true, removes from pending list.
 * - [SkillApprovalViewModel.rejectSkill]: sets isTrusted=false, removes from pending list.
 * - Approving/rejecting a package not in the DAO is a no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SkillApprovalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSkillTrustStoreDao : SkillTrustStoreDao {
        val store = mutableMapOf<String, SkillTrustEntry>()

        override suspend fun upsert(entry: SkillTrustEntry) {
            store[entry.packageName] = entry
        }

        override suspend fun get(packageName: String): SkillTrustEntry? = store[packageName]

        override fun observeTrusted(): Flow<List<SkillTrustEntry>> =
            flowOf(store.values.filter { it.isTrusted }.toList())

        override suspend fun getAllUnapproved(): List<SkillTrustEntry> =
            store.values.filter { !it.isTrusted }.toList()
    }

    private class FakeSkillDiscoverer(
        private val result: List<DiscoveredSkill> = emptyList(),
    ) : SkillDiscoverer {
        override suspend fun discoverSkills(): List<DiscoveredSkill> = result
    }

    private fun buildEntry(
        packageName: String,
        isTrusted: Boolean = false,
    ) = SkillTrustEntry(
        packageName = packageName,
        certSha256 = "abc123",
        approvedAtMs = 0L,
        approvedByUser = false,
        capabilities = "[]",
        isTrusted = isTrusted,
    )

    private fun buildViewModel(dao: FakeSkillTrustStoreDao): SkillApprovalViewModel =
        SkillApprovalViewModel(
            skillDiscoverer = FakeSkillDiscoverer(),
            skillTrustStoreDao = dao,
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun loadPendingSkills_noUnapprovedEntries_pendingListIsEmpty() = runTest {
        val dao = FakeSkillTrustStoreDao()
        dao.upsert(buildEntry("com.trusted.skill", isTrusted = true))

        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingSkills.isEmpty())
    }

    @Test
    fun loadPendingSkills_unapprovedEntry_appearsInPendingList() = runTest {
        val dao = FakeSkillTrustStoreDao()
        dao.upsert(buildEntry("com.pending.skill", isTrusted = false))

        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.pendingSkills.size)
        assertEquals("com.pending.skill", state.pendingSkills.first().packageName)
    }

    @Test
    fun approveSkill_setsTrustedAndRemovesFromPendingList() = runTest {
        val dao = FakeSkillTrustStoreDao()
        dao.upsert(buildEntry("com.skill.a", isTrusted = false))

        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.pendingSkills.size)

        viewModel.approveSkill("com.skill.a")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("isTrusted should be true", dao.store["com.skill.a"]!!.isTrusted)
        assertTrue("approvedByUser should be true", dao.store["com.skill.a"]!!.approvedByUser)
        assertTrue(viewModel.uiState.value.pendingSkills.isEmpty())
    }

    @Test
    fun rejectSkill_keepsTrustedFalseAndRemovesFromPendingList() = runTest {
        val dao = FakeSkillTrustStoreDao()
        dao.upsert(buildEntry("com.skill.b", isTrusted = false))

        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.pendingSkills.size)

        viewModel.rejectSkill("com.skill.b")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("isTrusted should remain false", dao.store["com.skill.b"]!!.isTrusted)
        assertFalse("approvedByUser should be false", dao.store["com.skill.b"]!!.approvedByUser)
        assertTrue(viewModel.uiState.value.pendingSkills.isEmpty())
    }

    @Test
    fun approveSkill_unknownPackage_isNoOp() = runTest {
        val dao = FakeSkillTrustStoreDao()
        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.approveSkill("com.unknown.skill")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingSkills.isEmpty())
    }

    @Test
    fun rejectSkill_unknownPackage_isNoOp() = runTest {
        val dao = FakeSkillTrustStoreDao()
        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.rejectSkill("com.unknown.skill")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingSkills.isEmpty())
    }

    @Test
    fun multiplePendingSkills_approveOne_othersRemain() = runTest {
        val dao = FakeSkillTrustStoreDao()
        dao.upsert(buildEntry("com.skill.1", isTrusted = false))
        dao.upsert(buildEntry("com.skill.2", isTrusted = false))
        dao.upsert(buildEntry("com.skill.3", isTrusted = false))

        val viewModel = buildViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.pendingSkills.size)

        viewModel.approveSkill("com.skill.2")
        testDispatcher.scheduler.advanceUntilIdle()

        val remaining = viewModel.uiState.value.pendingSkills.map { it.packageName }
        assertEquals(2, remaining.size)
        assertFalse(remaining.contains("com.skill.2"))
        assertTrue(remaining.contains("com.skill.1"))
        assertTrue(remaining.contains("com.skill.3"))
    }

    @Test
    fun uiState_initialIsLoadingFalse() {
        val dao = FakeSkillTrustStoreDao()
        // Snapshot before init coroutine runs
        val viewModel = buildViewModel(dao)
        // After testDispatcher.advanceUntilIdle() the isLoading should be false
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
