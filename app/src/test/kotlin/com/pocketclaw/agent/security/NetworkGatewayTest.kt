package com.pocketclaw.agent.security

import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import com.pocketclaw.core.data.db.entity.WhitelistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

private class FakeWhitelistStoreDao(
    private val domains: List<String> = emptyList(),
) : WhitelistStoreDao {
    private val _flow = MutableStateFlow(domains.map { FakeWhitelistStoreDao.entry(it) })

    override suspend fun add(entry: WhitelistEntry) = Unit
    override suspend fun remove(entry: WhitelistEntry) = Unit
    override suspend fun isDomainAllowed(domain: String): Int =
        if (domain in domains) 1 else 0
    override fun observeAll(): Flow<List<WhitelistEntry>> = _flow

    companion object {
        fun entry(domain: String) =
            WhitelistEntry(domain = domain, addedAtMs = 0L, addedBy = "TEST", note = "")
    }
}

private class FakeChain(private val request: Request) : Interceptor.Chain {
    override fun request(): Request = request
    override fun proceed(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

    // Unused methods from Interceptor.Chain
    override fun connection() = null
    override fun call(): okhttp3.Call = throw UnsupportedOperationException()
    override fun connectTimeoutMillis() = 0
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun readTimeoutMillis() = 0
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun writeTimeoutMillis() = 0
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
}

/**
 * Tests for [NetworkGateway].
 *
 * Verifies that the in-memory domain cache correctly gates outbound requests
 * without requiring [kotlinx.coroutines.runBlocking] on the calling thread.
 */
class NetworkGatewayTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun intercept_allowedDomain_proceeds() = runTest(dispatcher) {
        val dao = FakeWhitelistStoreDao(listOf("api.openai.com"))
        val gateway = NetworkGateway(dao, TestScope(dispatcher))

        val request = Request.Builder().url("https://api.openai.com/v1/chat").build()
        val response = gateway.intercept(FakeChain(request))

        assertNotNull(response)
    }

    @Test
    fun intercept_blockedDomain_throwsSecurityException() = runTest(dispatcher) {
        val dao = FakeWhitelistStoreDao(listOf("api.openai.com"))
        val gateway = NetworkGateway(dao, TestScope(dispatcher))

        val request = Request.Builder().url("https://evil.example.com/steal").build()

        assertThrows(SecurityException::class.java) {
            gateway.intercept(FakeChain(request))
        }
    }

    @Test
    fun intercept_emptyWhitelist_throwsSecurityException() = runTest(dispatcher) {
        val dao = FakeWhitelistStoreDao(emptyList())
        val gateway = NetworkGateway(dao, TestScope(dispatcher))

        val request = Request.Builder().url("https://api.anthropic.com/v1/messages").build()

        assertThrows(SecurityException::class.java) {
            gateway.intercept(FakeChain(request))
        }
    }

    @Test
    fun intercept_multipleAllowedDomains_allProceed() = runTest(dispatcher) {
        val domains = listOf("api.openai.com", "api.anthropic.com", "api.telegram.org")
        val dao = FakeWhitelistStoreDao(domains)
        val gateway = NetworkGateway(dao, TestScope(dispatcher))

        for (domain in domains) {
            val request = Request.Builder().url("https://$domain/path").build()
            val response = gateway.intercept(FakeChain(request))
            assertNotNull(response)
        }
    }
}
