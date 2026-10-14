package com.real.earn.paypal.api

import com.real.earn.paypal.BuildConfig
import com.real.earn.paypal.auth.AuthManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TIMEOUT_SEC = 30L

    @Volatile
    private var retrofit: Retrofit? = null

    fun service(authManager: AuthManager): ApiService {
        val existing = retrofit
        if (existing != null) return existing.create(ApiService::class.java)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = authManager.getToken()
                val request = if (!token.isNullOrEmpty()) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .build()
                } else {
                    original.newBuilder()
                        .header("Accept", "application/json")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        val r = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit = r
        return r.create(ApiService::class.java)
    }
}
