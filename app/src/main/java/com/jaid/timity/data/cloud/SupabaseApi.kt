package com.jaid.timity.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Supabase client over HttpURLConnection and org.json, in the same spirit as HolidayApi:
 * anonymous auth, token refresh, and PostgREST select / upsert. The publishable key identifies the
 * project; the user's access token decides which rows Row Level Security lets through.
 */
class SupabaseApi(baseUrl: String, private val apiKey: String) {
    private val base = baseUrl.trimEnd('/')

    class HttpException(val code: Int, message: String) : Exception(message)

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        /** Epoch millis after which [accessToken] is no longer accepted. */
        val expiresAt: Long,
        val userId: String,
    ) {
        fun expiresSoon(now: Long = System.currentTimeMillis()): Boolean = expiresAt - now < 60_000
    }

    /** Creates a fresh anonymous user. Requires anonymous sign-ins to be enabled on the project. */
    suspend fun signUpAnonymously(): Session =
        parseSession(request("POST", "$base/auth/v1/signup", JSONObject().toString(), token = null))

    suspend fun refresh(refreshToken: String): Session = parseSession(
        request(
            "POST",
            "$base/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken).toString(),
            token = null,
        ),
    )

    /** `GET /rest/v1/{table}?{query}`; [query] is a PostgREST filter string such as `select=*&order=id`. */
    suspend fun select(table: String, query: String, token: String): JSONArray =
        JSONArray(request("GET", "$base/rest/v1/$table?$query", body = null, token = token))

    /** Insert-or-update on the primary key. */
    suspend fun upsert(table: String, rows: JSONArray, token: String) {
        if (rows.length() == 0) return
        request(
            "POST", "$base/rest/v1/$table", rows.toString(), token,
            prefer = "resolution=merge-duplicates,return=minimal",
        )
    }

    private suspend fun request(
        method: String,
        url: String,
        body: String?,
        token: String?,
        prefer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = method
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw HttpException(code, errorMessage(text) ?: "HTTP $code")
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        val o = JSONObject(body)
        listOf("msg", "message", "error_description", "error").firstNotNullOfOrNull { k ->
            o.optString(k).takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun parseSession(body: String): Session {
        val o = JSONObject(body)
        return Session(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            userId = o.getJSONObject("user").getString("id"),
        )
    }
}
