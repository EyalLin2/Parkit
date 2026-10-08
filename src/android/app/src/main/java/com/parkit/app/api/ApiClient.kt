package com.parkit.app.api

import com.parkit.app.ApiConfig
import com.parkit.app.auth.SessionStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Defaults to 10.0.2.2 — the Android emulator's alias for the host
 * machine's localhost, where `docker compose up` runs the backend.
 * Overridable at build time (see app/build.gradle.kts' apiBaseUrl
 * property) for a build meant to reach the backend from a real device —
 * either the host's LAN IP (same Wi-Fi) or a public tunnel URL.
 */
val BASE_URL = ApiConfig.BASE_URL

object ApiClient {
    fun create(sessionStore: SessionStore): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = sessionStore.token.value
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}
