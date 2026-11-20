package com.medora.paymentsms.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeviceApi {
    @POST("api/v1/device/pair")
    suspend fun pair(@Body request: PairRequest): Response<ApiEnvelope<PairData>>

    @POST("api/v1/device/heartbeat")
    suspend fun heartbeat(@Header("Authorization") bearer: String): Response<ApiEnvelope<HeartbeatData>>

    @POST("api/v1/device/events")
    suspend fun sendEvent(
        @Header("Authorization") bearer: String,
        @Body event: EventRequest,
    ): Response<ApiEnvelope<EventData>>
}
