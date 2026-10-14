package com.real.earn.paypal.api

import com.google.gson.annotations.SerializedName

// ── Generic Response Wrapper ──────────────────────────────
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
)

// ── Auth: Request Code ────────────────────────────────────
data class RequestCodeBody(
    @SerializedName("telegram_id") val telegramId: Long? = null,
    @SerializedName("username") val username: String? = null
)

data class RequestCodeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("user_id") val userId: Long = 0,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("not_found") val notFound: Boolean = false
)

// ── Auth: Verify Code ─────────────────────────────────────
data class VerifyCodeBody(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("code") val code: String
)

data class VerifyCodeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

// ── User Info ─────────────────────────────────────────────
data class UserInfo(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String = "",
    @SerializedName("first_name") val firstName: String = "",
    @SerializedName("balance") val balance: Double = 0.0,
    @SerializedName("total_otps") val totalOtps: Long = 0,
    @SerializedName("today_otps") val todayOtps: Long = 0,
    @SerializedName("total_earned") val totalEarned: Double = 0.0,
    @SerializedName("currency") val currency: String = "PKR"
)

// ── Me Response (protected) ───────────────────────────────
data class MeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("error") val error: String? = null
)

// ── Logout Response ───────────────────────────────────────
data class LogoutResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)

// ── Error body (for error parsing) ────────────────────────
data class ErrorBody(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)
