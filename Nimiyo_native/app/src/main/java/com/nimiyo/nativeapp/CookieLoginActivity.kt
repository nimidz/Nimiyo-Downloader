package com.nimiyo.nativeapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nimiyo.nativeapp.core.CookieVaultManager
import com.nimiyo.nativeapp.core.HttpClientFactory
import com.nimiyo.nativeapp.core.StringsI18n
import com.nimiyo.nativeapp.ui.theme.NimiyoTheme
import java.util.Locale

class CookieLoginActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_DOMAIN = "extra_domain"

        fun createIntent(context: Context, domain: String): Intent =
            Intent(context, CookieLoginActivity::class.java).putExtra(EXTRA_DOMAIN, domain)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = NimiyoAppContainer.get(this)
        val rawDomain = intent?.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val domain = CookieVaultManager.normalizeDomain(rawDomain).ifBlank { "instagram.com" }
        val startUrl = initialLoginUrlFor(domain)

        setContent {
            val settings by container.settings.state.collectAsState()
            val strings = StringsI18n.get(settings.language)
            var pageProgress by remember { mutableIntStateOf(0) }
            var currentUrl by remember { mutableStateOf(startUrl) }
            var detectedCookieCount by remember { mutableIntStateOf(0) }
            var webViewRef by remember { mutableStateOf<WebView?>(null) }

            fun collectCookies(): Map<String, String> {
                val cm = CookieManager.getInstance()
                cm.flush()
                val probeUrls = buildProbeUrls(domain, currentUrl)
                val map = LinkedHashMap<String, String>()
                for (u in probeUrls) {
                    val host = CookieVaultManager.extractHost(u)
                    if (host.isBlank() || !CookieVaultManager.matchesRelatedDomain(host, domain)) continue
                    val raw = cm.getCookie(u).orEmpty()
                    if (raw.isNotBlank()) {
                        map[u] = raw
                    }
                }
                return map
            }

            fun refreshCookieCount() {
                val rawMap = collectCookies()
                val uniqueNames = LinkedHashSet<String>()
                rawMap.values.forEach { hdr ->
                    hdr.split(';').forEach { part ->
                        val name = part.substringBefore('=').trim()
                        if (name.isNotEmpty()) uniqueNames.add(name)
                    }
                }
                detectedCookieCount = uniqueNames.size
            }

            fun saveAndFinish() {
                val rawMap = collectCookies()
                val saved = container.cookieVault.saveCookiesFromWebView(domain, rawMap)
                if (saved != null && saved.cookieCount > 0) {
                    container.settings.update { it.copy(cookiesEnabled = true) }
                    Toast.makeText(
                        this,
                        "$domain: ${strings.cookie.savedToastMsg} (${saved.cookieCount})",
                        Toast.LENGTH_SHORT,
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        strings.cookie.noCookieCapturedToast,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            BackHandler {
                val wv = webViewRef
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    finish()
                }
            }

            // Lightweight periodic polling handles SPA logins that complete via XHR without full page reload
            LaunchedEffect(webViewRef) {
                while (true) {
                    delay(2500L)
                    runCatching { refreshCookieCount() }
                }
            }

            NimiyoTheme(
                uiStyle = settings.uiStyle,
                darkModePref = settings.darkMode,
                accentColor = settings.accentColor,
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.viewer.backCd)
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        text = "${strings.cookie.loginTitlePrefix} $domain",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "$detectedCookieCount ${strings.cookie.cookiesDetectedSuffix}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (detectedCookieCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { webViewRef?.reload() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = strings.cookie.reloadCd)
                                }
                                Button(
                                    onClick = { saveAndFinish() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.cookie.saveBtn, fontWeight = FontWeight.Bold)
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = strings.cookie.loginWebBanner,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            DisposableEffect(Unit) {
                                onDispose {
                                    webViewRef?.apply {
                                        stopLoading()
                                        loadUrl("about:blank")
                                        removeAllViews()
                                        destroy()
                                    }
                                    webViewRef = null
                                }
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        this.settings.javaScriptEnabled = true
                                        this.settings.domStorageEnabled = true
                                        this.settings.databaseEnabled = true
                                        this.settings.allowFileAccess = false
                                        this.settings.allowContentAccess = false
                                        this.settings.useWideViewPort = true
                                        this.settings.loadWithOverviewMode = true
                                        this.settings.userAgentString = HttpClientFactory.CHROME_DESKTOP_UA
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                pageProgress = newProgress
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val scheme = request?.url?.scheme?.lowercase(Locale.US) ?: return true
                                                return scheme != "http" && scheme != "https"
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                if (!url.isNullOrBlank()) currentUrl = url
                                                refreshCookieCount()
                                            }
                                        }
                                        loadUrl(startUrl)
                                        webViewRef = this
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initialLoginUrlFor(domain: String): String = when {
        domain.contains("instagram.com") -> "https://www.instagram.com/accounts/login/"
        domain.contains("pixiv.net") -> "https://accounts.pixiv.net/login"
        domain.contains("xiaohongshu.com") || domain.contains("rednote.com") -> "https://www.rednote.com/"
        domain.contains("tiktok.com") -> "https://www.tiktok.com/login"
        domain == "x.com" || domain.contains("twitter.com") -> "https://x.com/i/flow/login"
        domain.contains("bilibili.com") -> "https://passport.bilibili.com/login"
        domain.contains("facebook.com") -> "https://m.facebook.com/login/"
        else -> "https://$domain/"
    }

    private fun buildProbeUrls(domain: String, currentUrl: String): List<String> {
        val list = linkedSetOf(
            currentUrl,
            "https://$domain/",
            "https://www.$domain/",
            "https://m.$domain/",
        )
        val lower = domain.lowercase(Locale.US)
        if (lower.contains("rednote.com") || lower.contains("xiaohongshu.com")) {
            list.add("https://www.rednote.com/")
            list.add("https://www.xiaohongshu.com/")
        }
        if (lower == "x.com" || lower.contains("twitter.com")) {
            list.add("https://x.com/")
            list.add("https://twitter.com/")
        }
        return list.toList()
    }
}

