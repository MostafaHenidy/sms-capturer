package com.medora.paymentsms.data.api

import com.google.gson.GsonBuilder
import com.medora.paymentsms.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    /** Base URL comes from build type only; never from user input or APK constants. */
    fun create(baseUrl: String = BuildConfig.API_BASE_URL): DeviceApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            // Deliberately no logging interceptor: auth headers and payloads must never be logged.
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(DeviceApi::class.java)
    }
}
