package org.lolicode.ncmapilitekt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.lolicode.ncmapilitekt.crypto.EApiCrypto
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.flatMap
import java.net.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Main client for the Netease Cloud Music API.
 *
 * Maintains any server-issued request cookies internally across requests.
 *
 * Usage:
 * ```kotlin
 * val client = NeteaseClient()
 * val urls = client.getSongUrl(listOf("123456"), SongLevel.ExHigh)
 * ```
 */
public class NeteaseClient(
    /** Optional proxy for the underlying java.net.http.HttpClient */
    proxy: ProxySelector? = null,
    /** Connect/read/write timeout in milliseconds */
    timeoutMs: Long = 30_000L
) {
    /** Thread-safe mutable cookie store for server-issued request cookies. */
    private val cookies: MutableMap<String, String> = ConcurrentHashMap()

    /** Custom X-Real-IP header value (optional) */
    public var xRealIp: String? = null

    internal val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    internal val http: HttpClient = HttpClient.newBuilder()
        .apply {
            if (proxy != null) proxy(proxy)
            connectTimeout(Duration.ofMillis(timeoutMs))
        }
        .build()

    // Make multi-cookie apply and import (clear+putAll) atomic as a batch.
    private val cookieMutationLock: ReentrantLock = ReentrantLock()

    // ─── Internal request helpers ────────────────────────────────────────────

    /**
     * Send an EApi request.
     *
     * @param url       Full HTTPS URL (e.g. `https://interface.music.163.com/eapi/v3/song/detail`)
     * @param apiPath   API path segment used for the EApi signature (e.g. `/api/v3/song/detail`)
     * @param params    Request body parameters (the `header` field is added automatically)
     */
    internal suspend fun eApiRequest(
        url: String,
        apiPath: String,
        params: Map<String, Any?>
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val eapiUrl = url.replace(Regex("""\w*api"""), "eapi")
        val allCookies = snapshotCookies()
        val header = buildEApiHeader(allCookies)
        val fullParams = buildMap {
            putAll(params)
            put("header", header.toJsonString())
            put("e_r", true)
        }
        val jsonStr = fullParams.toJsonString()
        val encrypted = EApiCrypto.encrypt(apiPath, jsonStr)

        val body = buildFormBody("params" to encrypted)

        val request = buildRequest(eapiUrl, body)
        // Read raw bytes to avoid UTF-8 corruption of encrypted binary payloads
        executeAndSaveCookiesAsBytes(request).flatMap { rawBytes ->
            decryptEApiResponseBytes(rawBytes)
        }
    }

    // ─── Response parsing helper ──────────────────────────────────────────────

    internal inline fun <reified T : BaseResponse> parseResponse(jsonStr: String): ApiResult<T> {
        val obj = try {
            json.decodeFromString<T>(jsonStr)
        } catch (e: Exception) {
            return ApiResult.Error.Parse(jsonStr, e)
        }
        return if (obj.code == 200) {
            ApiResult.Success(obj)
        } else {
            ApiResult.Error.Business(obj.code, obj.errorMessage)
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun snapshotCookies(): Map<String, String> = cookieMutationLock.withLock { cookies.toMap() }

    private fun buildEApiHeader(allCookies: Map<String, String>): Map<String, String> {
        val now = System.currentTimeMillis()
        val requestId = "${now}_${(Math.random() * 10000).toInt().toString().padStart(4, '0')}"
        return buildMap {
            put("osver", allCookies["osver"] ?: "")
            put("deviceId", allCookies["deviceId"] ?: "")
            put("appver", allCookies["appver"] ?: "8.10.10")
            put("versioncode", allCookies["versioncode"] ?: "140")
            put("mobilename", allCookies["mobilename"] ?: "")
            put("buildver", allCookies["buildver"] ?: (now / 1000).toString())
            put("resolution", allCookies["resolution"] ?: "1920x1080")
            put("__csrf", allCookies["__csrf"] ?: "")
            put("os", allCookies["os"] ?: "android")
            put("channel", allCookies["channel"] ?: "")
            put("requestId", requestId)
            allCookies["MUSIC_U"]?.takeIf { it.isNotEmpty() }?.let { put("MUSIC_U", it) }
            allCookies["MUSIC_A"]?.takeIf { it.isNotEmpty() }?.let { put("MUSIC_A", it) }
        }
    }

    private fun buildFormBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

    private fun buildRequest(
        url: String,
        body: String
    ): HttpRequest {
        val allCookies = snapshotCookies()
        val cookieHeader = allCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val ua = resolveUserAgent()

        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", ua)
            .apply {
                if (cookieHeader.isNotEmpty()) header("Cookie", cookieHeader)
                if (!xRealIp.isNullOrEmpty()) header("X-Real-IP", xRealIp!!)
                if (url.contains("music.163.com")) header("Referer", "https://music.163.com")
            }
            .build()
    }

    private fun executeAndSaveCookiesAsString(request: HttpRequest): ApiResult<String> =
        try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            saveCookies(response)
            ApiResult.Success(response.body())
        } catch (e: Exception) {
            ApiResult.Error.Transport(e)
        }

    private fun executeAndSaveCookiesAsBytes(request: HttpRequest): ApiResult<ByteArray> =
        try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            saveCookies(response)
            ApiResult.Success(response.body())
        } catch (e: Exception) {
            ApiResult.Error.Transport(e)
        }

    private fun saveCookies(response: HttpResponse<*>) {
        val cookieHeaders = response.headers().allValues("Set-Cookie")
        if (cookieHeaders.isEmpty()) return
        
        cookieMutationLock.withLock {
            cookieHeaders.forEach { header ->
                HttpCookie.parse(header).forEach { cookie ->
                    cookies[cookie.name] = cookie.value
                }
            }
        }
    }

    private fun decryptEApiResponseBytes(bytes: ByteArray): ApiResult<String> {
        // If the response starts with '{"' it is already plain JSON
        if (bytes.size >= 2 && bytes[0] == 0x7B.toByte() && bytes[1] == 0x22.toByte()) {
            return ApiResult.Success(bytes.toString(Charsets.UTF_8))
        }
        return try {
            ApiResult.Success(EApiCrypto.decrypt(bytes).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            ApiResult.Success(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun resolveUserAgent(): String = (USER_AGENTS_MOBILE + USER_AGENTS_PC).random()

    private companion object {
        val USER_AGENTS_MOBILE = listOf(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.",
            "Mozilla/5.0 (Linux; Android 9; PCT-AL10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.64 HuaweiBrowser/10.0.3.311 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; U; Android 9; zh-cn; Redmi Note 8 Build/PKQ1.190616.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser/12.5.22"
        )
        val USER_AGENTS_PC = listOf(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:80.0) Gecko/20100101 Firefox/80.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.30 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:80.0) Gecko/20100101 Firefox/80.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.30 Safari/537.36"
        )
    }
}

// ─── Private JSON serialisation helper ───────────────────────────────────────

/** Converts a [Map<String, Any?>] to a compact JSON string without a full serialiser chain. */
private fun Map<String, Any?>.toJsonString(): String =
    JsonObject(mapValues { (_, v) -> v.toJsonElement() }).toString()

private fun Any?.toJsonElement() = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}
