package com.pocketcli.data.opencode.api

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

class BasicAuthInterceptor(
    private val usernameProvider: () -> String,
    private val passwordProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val username = usernameProvider()
        val password = passwordProvider()

        val request = if (password.isNotEmpty()) {
            original.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}

class CleartextHttpPolicyInterceptor(
    private val allowCleartextProvider: () -> Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (!url.isHttps) {
            val host = url.host
            val isLocal = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "10.0.2.2"
            if (!isLocal && !allowCleartextProvider()) {
                throw SecurityException(
                    "Cleartext HTTP traffic to '$host' is disabled. Please enable 'Allow Cleartext HTTP' in connection profile settings."
                )
            }
        }
        return chain.proceed(request)
    }
}
