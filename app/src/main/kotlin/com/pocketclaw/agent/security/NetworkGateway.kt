package com.pocketclaw.agent.security

import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import com.pocketclaw.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that enforces the network domain whitelist.
 * Every outbound request's host is checked against an in-memory cache of
 * [WhitelistStoreDao] entries that is kept up-to-date via a Flow collector.
 * Using an in-memory cache avoids [runBlocking] on OkHttp's thread pool, which
 * would risk deadlock when the coroutine IO dispatcher shares threads.
 *
 * Blocked requests throw [SecurityException] — never bypassed.
 *
 * Applied to ALL Ktor/OkHttp clients used by the agent.
 */
@Singleton
class NetworkGateway @Inject constructor(
    private val whitelistDao: WhitelistStoreDao,
    @ApplicationScope private val scope: CoroutineScope,
) : Interceptor {

    @Volatile
    private var allowedDomains: Set<String> = emptySet()

    init {
        scope.launch {
            whitelistDao.observeAll().collect { entries ->
                allowedDomains = entries.map { it.domain }.toHashSet()
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (host !in allowedDomains) {
            throw SecurityException(
                "NetworkGateway: Outbound request to '$host' is blocked. " +
                    "Domain not in whitelist. Request URL: ${chain.request().url}",
            )
        }
        return chain.proceed(chain.request())
    }
}
