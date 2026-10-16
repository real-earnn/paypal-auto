package com.real.earn.paypal.api

import com.google.gson.annotations.SerializedName

// ── Auth: Request Code ────────────────────────────────────
data class RequestCodeBody(
    @SerializedName("telegram_id") val telegramId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("owner_id") val ownerId: Long? = null   // 🟢 NEW: client mode ke liye
)

data class RequestCodeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("user_id") val userId: Long = 0,
    @SerializedName("client_id") val clientId: Long = 0,    // 🟢 NEW: server returns client_id
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("not_found") val notFound: Boolean = false
)

data class VerifyCodeBody(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("code") val code: String,
    @SerializedName("client_id") val clientId: Long = 0     // 🟢 NEW: optional safety
)

data class VerifyCodeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String? = null,
    @SerializedName("client_id") val clientId: Long = 0,    // 🟢 NEW
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

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

data class MeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("error") val error: String? = null
)

data class LogoutResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)

// ── Services List ─────────────────────────────────────────
data class ServicesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("services") val services: List<ServiceItem>? = null
)

data class ServiceItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("icon_id") val iconId: String = "",
    @SerializedName("countries") val countries: List<CountryItem>? = null,
    @SerializedName("is_unpaid") val isUnpaid: Boolean = false
) {
    val countriesSafe: List<CountryItem>
        get() = countries ?: emptyList()
}

data class CountryItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("code") val code: String = "",
    @SerializedName("dialing_code") val dialingCode: String = "",
    @SerializedName("count") val count: Int = 0,
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("file") val file: String = ""
)

data class CountriesResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("service") val service: String = "",
    @SerializedName("is_unpaid") val isUnpaid: Boolean = false,
    @SerializedName("countries") val countries: List<CountryItem>? = null
)

// ── Get Numbers (existing) ────────────────────────────────
data class GetNumberBody(
    @SerializedName("service") val service: String,
    @SerializedName("country") val country: String,
    @SerializedName("count") val count: Int = 5
)

data class GetNumberResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("numbers") val numbers: List<String>? = null,
    @SerializedName("service") val service: String = "",
    @SerializedName("country") val country: String = "",
    @SerializedName("country_code") val countryCode: String = "",
    @SerializedName("dialing_code") val dialingCode: String = "",
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

// ── Release Numbers ───────────────────────────────────────
data class ReleaseNumberBody(
    @SerializedName("numbers") val numbers: List<String>,
    @SerializedName("reason") val reason: String = "otp_failed"
)

data class ReleaseNumberResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("released") val released: Int = 0
)

// ══════════════════════════════════════════════════════════════════════════
//  🟢 PREFIX MODULE MODELS (NEW)
// ══════════════════════════════════════════════════════════════════════════

// ── GET /api/app/prefixes (list mode) ─────────────────────
data class PrefixesListResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("mode") val mode: String = "",
    @SerializedName("prefixes") val prefixes: List<PrefixEntry>? = null
)

data class PrefixEntry(
    @SerializedName("prefix") val prefix: String = "",
    @SerializedName("files") val files: List<PrefixFileInfo>? = null
) {
    val filesSafe: List<PrefixFileInfo>
        get() = files ?: emptyList()
}

data class PrefixFileInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)

// ── GET /api/app/prefixes?prefix=X (search mode) ──────────
data class PrefixSearchResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("mode") val mode: String = "",
    @SerializedName("searching_prefix") val searchingPrefix: String = "",
    @SerializedName("files") val files: List<PrefixFileInfo>? = null
) {
    val filesSafe: List<PrefixFileInfo>
        get() = files ?: emptyList()
}

// ── POST /api/app/get-number-by-prefix ────────────────────
data class GetNumberByPrefixBody(
    @SerializedName("service") val service: String,
    @SerializedName("prefix") val prefix: String? = null,
    @SerializedName("file") val file: String? = null,
    @SerializedName("auto") val auto: Boolean = false,
    @SerializedName("count") val count: Int = 5
)

data class GetNumberByPrefixResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("numbers") val numbers: List<String>? = null,
    @SerializedName("raw_numbers") val rawNumbers: List<String>? = null,
    @SerializedName("service") val service: String = "",
    @SerializedName("prefix") val prefix: String = "",
    @SerializedName("file") val file: String = "",
    @SerializedName("country_code") val countryCode: String = "",
    @SerializedName("dialing_code") val dialingCode: String = "",
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)
