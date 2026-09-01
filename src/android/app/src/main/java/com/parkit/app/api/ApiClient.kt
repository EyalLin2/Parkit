package com.parkit.app.api

import com.parkit.app.auth.SessionStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * 10.0.2.2 is the Android emulator's alias for the host machine's
 * localhost — that's where `docker compose up` runs the backend. Point
 * this at your machine's LAN IP instead if you're running on a real
 * device (both need to be on the same network).
 */
const val BASE_URL = "http://10.0.2.2:8000/"

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
