package com.real.earn.paypal.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("/api/app/auth/request-code")
    suspend fun requestCode(@Body body: RequestCodeBody): Response<RequestCodeResponse>

    @POST("/api/app/auth/verify-code")
    suspend fun verifyCode(@Body body: VerifyCodeBody): Response<VerifyCodeResponse>

    @GET("/api/app/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("/api/app/logout")
    suspend fun logout(): Response<LogoutResponse>

    @GET("/api/app/services")
    suspend fun getServices(): Response<ServicesResponse>

    @GET("/api/app/countries")
    suspend fun getCountries(@Query("service") service: String): Response<CountriesResponse>

    @POST("/api/app/get-number")
    suspend fun getNumbers(@Body body: GetNumberBody): Response<GetNumberResponse>

    @POST("/api/app/release-number")
    suspend fun releaseNumbers(@Body body: ReleaseNumberBody): Response<ReleaseNumberResponse>
}
