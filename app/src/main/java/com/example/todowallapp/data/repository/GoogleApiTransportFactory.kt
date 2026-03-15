package com.example.todowallapp.data.repository

import com.google.api.client.http.javanet.NetHttpTransport
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Shared SSL transport factory for Google API clients.
 */
object GoogleApiTransportFactory {
    const val APP_NAME = "Ledger"

    fun createTransport(): NetHttpTransport {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        return NetHttpTransport.Builder()
            .setSslSocketFactory(sslContext.socketFactory)
            .build()
    }
}
