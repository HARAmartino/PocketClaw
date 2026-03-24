package com.pocketclaw.agent.security

import com.pocketclaw.core.data.db.dao.WhitelistStoreDao
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that enforces the network domain whitelist.
 * Every outbound request's host is checked against [WhitelistStoreDao].
 * Blocked requests throw [SecurityException] — never bypassed.
 *
 * Applied to ALL Ktor/OkHttp clients used by the agent.
 */
class NetworkGateway @Inject constructor(
    private val whitelistDao: WhitelistStoreDao,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        val isAllowed = runBlocking {
            whitelistDao.isDomainAllowed(host) > 0
        }

        if (!isAllowed) {
            throw SecurityException(
                "NetworkGateway: Outbound request to '$host' is blocked. " +
                    "Domain not in whitelist. Request URL: ${request.url}",
            )
        }

        return chain.proceed(request)
    }
}
