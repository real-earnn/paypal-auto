package com.real.earn.paypal.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    // ── Auth ──────────────────────────────────────────────
    @POST("/api/app/auth/request-code")
    suspend fun requestCode(
        @Body body: RequestCodeBody
    ): Response<RequestCodeResponse>

    @POST("/api/app/auth/verify-code")
    suspend fun verifyCode(
        @Body body: VerifyCodeBody
    ): Response<VerifyCodeResponse>

    // ── Protected ─────────────────────────────────────────
    @GET("/api/app/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("/api/app/logout")
    suspend fun logout(): Response<LogoutResponse>
}
