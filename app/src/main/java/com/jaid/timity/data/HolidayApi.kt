package com.jaid.timity.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Thin client for Calendarific's holidays endpoint. No third-party HTTP or JSON library needed. */
object HolidayApi {
    private const val BASE = "https://calendarific.com/api/v2/holidays"

    /** Types that are real days off and should count as overtime by default. Others are listed but switched off. */
    private val defaultOnTypes = setOf("national holiday", "public holiday")

    class ApiException(message: String) : Exception(message)

    suspend fun fetch(apiKey: String, country: String, year: Int): List<Holiday> = withContext(Dispatchers.IO) {
        val url = URL("$BASE?api_key=${URLEncoder.encode(apiKey, "UTF-8")}&country=$country&year=$year")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                val reason = runCatching { JSONObject(body).getJSONObject("meta").optString("error_detail") }
                    .getOrNull().takeUnless { it.isNullOrBlank() }
                throw ApiException(reason ?: "HTTP $code")
            }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(body: String): List<Holiday> {
        val holidays = JSONObject(body).getJSONObject("response").getJSONArray("holidays")
        val out = ArrayList<Holiday>(holidays.length())
        for (i in 0 until holidays.length()) {
            val h = holidays.getJSONObject(i)
            val iso = h.getJSONObject("date").getString("iso").take(10)
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: continue
            val type = h.optString("primary_type").ifBlank {
                h.optJSONArray("type")?.optString(0).orEmpty()
            }
            out += Holiday(
                epochDay = date.toEpochDay(),
                name = h.getString("name"),
                type = type,
                source = Holiday.SOURCE_API,
                enabled = type.lowercase() in defaultOnTypes,
            )
        }
        return out
    }
}
