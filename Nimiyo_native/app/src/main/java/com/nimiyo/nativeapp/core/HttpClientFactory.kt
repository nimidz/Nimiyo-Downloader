package com.nimiyo.nativeapp.core

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class HttpResponseData(
    val status: Int,
    val body: String,
    val finalUrl: String,
    val headers: Map<String, List<String>>,
) {
    fun cookieString(): String {
        val setCookies = headers["set-cookie"] ?: headers["Set-Cookie"] ?: emptyList()
        return setCookies.mapNotNull { it.split(";").firstOrNull()?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
    }
}

class InMemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(store) {
            store.removeAll { it.expiresAt < now }
            cookies.forEach { cookie ->
                store.removeAll {
                    it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                }
                store.add(cookie)
            }
            if (store.size > 256) {
                val excess = store.size - 256
                repeat(excess) { store.removeAt(0) }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(store) {
            store.removeAll { it.expiresAt < now }
            return store.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(store) { store.clear() }
    }
}

class HttpClientFactory(
    private val settings: AppSettings,
    private val cookieProvider: ((String) -> String?)? = null,
) {
    companion object {
        const val CHROME_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        const val SAFARI_MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        const val WHATSAPP_UA = "WhatsApp/2.21.19.21 A"
        private const val MAX_TEXT_BODY_BYTES = 16L * 1024L * 1024L
    }

    val cookieJar = InMemoryCookieJar()

    // Shared root client so derived clients reuse ConnectionPool, Dispatcher threads, and sockets.
    private val rootClient: OkHttpClient = OkHttpClient.Builder().build()

    private val dohClient: OkHttpClient = rootClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private data class ClientConfigKey(
        val timeoutSec: Long,
        val followRedirects: Boolean,
        val forceIpv4: Boolean,
        val customDoh: String,
        val bypassSsl: Boolean,
        val isDownload: Boolean,
    )

    @Volatile
    private var cachedFollowClient: Pair<ClientConfigKey, OkHttpClient>? = null

    @Volatile
    private var cachedNoFollowClient: Pair<ClientConfigKey, OkHttpClient>? = null

    @Volatile
    private var cachedDownloadClient: Pair<ClientConfigKey, OkHttpClient>? = null

    private fun buildClientForConfig(key: ClientConfigKey): OkHttpClient {
        val builder = rootClient.newBuilder()
            .connectTimeout(if (key.isDownload) key.timeoutSec.coerceAtLeast(30L) else key.timeoutSec, TimeUnit.SECONDS)
            .readTimeout(if (key.isDownload) 120L else key.timeoutSec, TimeUnit.SECONDS)
            .writeTimeout(key.timeoutSec, TimeUnit.SECONDS)
            .followRedirects(key.followRedirects)
            .followSslRedirects(key.followRedirects)
            .cookieJar(cookieJar)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                if (req.header("Cookie").isNullOrBlank() && req.url.isHttps) {
                    val vaultCookie = cookieProvider?.invoke(req.url.toString())
                    if (!vaultCookie.isNullOrBlank()) {
                        return@addNetworkInterceptor chain.proceed(
                            req.newBuilder().header("Cookie", vaultCookie).build(),
                        )
                    }
                }
                chain.proceed(req)
            }

        if (key.forceIpv4 || key.customDoh.isNotBlank()) {
            builder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (key.customDoh.startsWith("https://", ignoreCase = true)) {
                        try {
                            val dohRes = resolveOverDoh(key.customDoh, hostname)
                            if (dohRes.isNotEmpty()) return dohRes
                        } catch (_: Exception) {
                        }
                    }
                    val all = Dns.SYSTEM.lookup(hostname)
                    return if (key.forceIpv4) {
                        val v4 = all.filterIsInstance<Inet4Address>()
                        v4.ifEmpty { all }
                    } else {
                        all
                    }
                }
            })
        }

        if (key.bypassSsl) {
            try {
                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (_: Exception) {
            }
        }

        return builder.build()
    }

    private fun resolveOverDoh(dohBase: String, hostname: String): List<InetAddress> {
        val cleanBase = dohBase.trim()
        if (!cleanBase.startsWith("https://", ignoreCase = true)) return emptyList()
        val url = cleanBase.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("name", hostname)
            ?.addQueryParameter("type", "A")
            ?.build()
            ?: return emptyList()
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()
        dohClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val bodyStr = res.body?.string() ?: return emptyList()
            val json = JSONObject(bodyStr)
            val ans = json.optJSONArray("Answer") ?: return emptyList()
            val ips = mutableListOf<InetAddress>()
            for (i in 0 until ans.length()) {
                val obj = ans.optJSONObject(i) ?: continue
                if (obj.optInt("type") == 1) {
                    val data = obj.optString("data")
                    if (data.isNotBlank()) ips.add(InetAddress.getByName(data))
                }
            }
            return ips
        }
    }

    private fun currentKey(followRedirects: Boolean, isDownload: Boolean): ClientConfigKey {
        val cfg = settings.current
        return ClientConfigKey(
            timeoutSec = cfg.timeoutSeconds.toLong().coerceIn(5L, 180L),
            followRedirects = followRedirects,
            forceIpv4 = cfg.forceIpv4,
            customDoh = cfg.customDoh.trim(),
            bypassSsl = cfg.bypassSsl,
            isDownload = isDownload,
        )
    }

    fun scraperClient(followRedirects: Boolean = true): OkHttpClient {
        val key = currentKey(followRedirects = followRedirects, isDownload = false)
        val cached = if (followRedirects) cachedFollowClient else cachedNoFollowClient
        if (cached != null && cached.first == key) return cached.second
        val created = buildClientForConfig(key)
        if (followRedirects) cachedFollowClient = key to created else cachedNoFollowClient = key to created
        return created
    }

    fun downloadClient(): OkHttpClient {
        val key = currentKey(followRedirects = true, isDownload = true)
        val cached = cachedDownloadClient
        if (cached != null && cached.first == key) return cached.second
        val created = buildClientForConfig(key)
        cachedDownloadClient = key to created
        return created
    }

    fun effectiveUserAgent(explicitUa: String? = null): String {
        val custom = settings.current.customUserAgent.trim()
        if (!explicitUa.isNullOrBlank()) return explicitUa
        if (custom.isNotBlank()) return custom
        return CHROME_DESKTOP_UA
    }

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): HttpResponseData {
        val reqBuilder = Request.Builder().url(url).get()
        applyHeaders(reqBuilder, headers)
        scraperClient(followRedirects).newCall(reqBuilder.build()).execute().use { res ->
            return toResponseData(res)
        }
    }

    fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): HttpResponseData {
        val bodyBuilder = FormBody.Builder()
        form.forEach { (k, v) -> bodyBuilder.add(k, v) }
        val reqBuilder = Request.Builder().url(url).post(bodyBuilder.build())
        applyHeaders(reqBuilder, headers)
        scraperClient(followRedirects).newCall(reqBuilder.build()).execute().use { res ->
            return toResponseData(res)
        }
    }

    fun postRaw(
        url: String,
        rawBody: String,
        contentType: String = "application/json; charset=utf-8",
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): HttpResponseData {
        val mediaType = contentType.toMediaType()
        val reqBuilder = Request.Builder().url(url).post(rawBody.toRequestBody(mediaType))
        applyHeaders(reqBuilder, headers)
        scraperClient(followRedirects).newCall(reqBuilder.build()).execute().use { res ->
            return toResponseData(res)
        }
    }

    private fun applyHeaders(builder: Request.Builder, headers: Map<String, String>) {
        val spoof = settings.current.enableHeaderSpoofing
        var hasUa = false
        var hasAccept = false
        headers.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
            if (k.equals("Accept", ignoreCase = true)) hasAccept = true
            builder.header(k, v)
        }
        if (!hasUa) builder.header("User-Agent", effectiveUserAgent())
        if (spoof && !hasAccept) {
            builder.header("Accept", "*/*")
        }
    }

    private fun toResponseData(res: Response): HttpResponseData {
        val body = res.body?.let { b ->
            if (b.contentLength() > MAX_TEXT_BODY_BYTES) {
                throw IllegalStateException("Response too large (${b.contentLength()} bytes)")
            }
            res.peekBody(MAX_TEXT_BODY_BYTES).string()
        }.orEmpty()
        val hdrMap = mutableMapOf<String, List<String>>()
        for (name in res.headers.names()) {
            hdrMap[name.lowercase()] = res.headers.values(name)
        }
        return HttpResponseData(
            status = res.code,
            body = body,
            finalUrl = res.request.url.toString(),
            headers = hdrMap,
        )
    }
}

