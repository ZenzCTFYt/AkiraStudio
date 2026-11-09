package com.akira.studio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var splash: android.view.View
    private lateinit var homePanel: android.view.View

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { arrayOf(it) }
                ?: result.data?.clipData?.let { cd ->
                    (0 until cd.itemCount).map { cd.getItemAt(it).uri }.toTypedArray()
                }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        splash = findViewById(R.id.splash)
        homePanel = findViewById(R.id.homePanel)
        webView.setBackgroundColor(android.graphics.Color.parseColor("#F2F2F7"))

        ExtensionManager.init(this)
        setupWebView()

        findViewById<android.widget.Button>(R.id.btnOpenTikTok).setOnClickListener {
            animateClick(it); openTikTok()
        }
        findViewById<android.widget.Button>(R.id.btnOpenStudio).setOnClickListener {
            animateClick(it); openStudio()
        }
        findViewById<android.widget.Button>(R.id.btnExtensions).setOnClickListener {
            animateClick(it); startActivity(Intent(this, ExtensionsActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.navHome).setOnClickListener {
            animateClick(it); showHome()
        }
        findViewById<android.widget.Button>(R.id.navLogin).setOnClickListener {
            animateClick(it); openTikTok()
        }
        findViewById<android.widget.Button>(R.id.navStudio).setOnClickListener {
            animateClick(it); openStudio()
        }
        findViewById<android.widget.Button>(R.id.navExtensions).setOnClickListener {
            animateClick(it); startActivity(Intent(this, ExtensionsActivity::class.java))
        }

        requestRuntimePermissions()

        if (savedInstanceState != null) {
            homePanel.visibility = android.view.View.GONE
            webView.visibility = android.view.View.VISIBLE
            webView.restoreState(savedInstanceState)
        } else {
            homePanel.visibility = android.view.View.VISIBLE
            webView.visibility = android.view.View.GONE
            homePanel.alpha = 0f
            homePanel.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun animateClick(v: android.view.View) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
    }

    private fun showHome() {
        if (homePanel.visibility == android.view.View.VISIBLE) return
        webView.animate().alpha(0f).setDuration(140).withEndAction {
            webView.visibility = android.view.View.GONE
            webView.alpha = 1f
            homePanel.visibility = android.view.View.VISIBLE
            homePanel.alpha = 0f
            homePanel.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun openTikTok() {
        if (homePanel.visibility == android.view.View.VISIBLE) {
            homePanel.animate().alpha(0f).setDuration(140).withEndAction {
                homePanel.visibility = android.view.View.GONE
                homePanel.alpha = 1f
                webView.visibility = android.view.View.VISIBLE
                webView.alpha = 0f
                webView.loadUrl("https://www.tiktok.com/login")
                webView.animate().alpha(1f).setDuration(180).start()
            }.start()
        } else {
            webView.loadUrl("https://www.tiktok.com/login")
        }
    }

    private fun openStudio() {
        if (homePanel.visibility == android.view.View.VISIBLE) {
            homePanel.animate().alpha(0f).setDuration(140).withEndAction {
                homePanel.visibility = android.view.View.GONE
                homePanel.alpha = 1f
                webView.visibility = android.view.View.VISIBLE
                webView.alpha = 0f
                webView.loadUrl("https://www.tiktok.com/tiktokstudio/upload?from=creator_center&tab=video")
                webView.animate().alpha(1f).setDuration(180).start()
            }.start()
        } else {
            webView.loadUrl("https://www.tiktok.com/tiktokstudio/upload?from=creator_center&tab=video")
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_studio -> {
                openStudio()
                true
            }
            R.id.action_extensions -> {
                startActivity(Intent(this, ExtensionsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                if (webView.visibility == android.view.View.VISIBLE) webView.reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        webView.setInitialScale(0)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.tag = null
                splash.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && view != null) ExtensionManager.injectForUrl(this@MainActivity, view, url)
                splash.visibility = android.view.View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                splash.visibility = android.view.View.GONE
                if (request?.isForMainFrame == true) {
                    // avoid reload loops; only retry once
                    if (view?.tag != "retried") {
                        view?.tag = "retried"
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            view?.reload()
                        }, 1500)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host ?: return false
                if (isAllowedHost(host)) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: Exception) { }
                return true
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            splash.visibility = android.view.View.GONE
        }, 8000)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                filePicker.launch(intent)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    private fun isAllowedHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "tiktok.com" || h.endsWith(".tiktok.com")) return true
        val allowed = setOf(
            "accounts.google.com",
            "www.facebook.com",
            "m.facebook.com",
            "appleid.apple.com",
            "login.microsoftonline.com"
        )
        return h in allowed
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (homePanel.visibility == android.view.View.VISIBLE) {
            finish()
        } else if (webView.visibility == android.view.View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            showHome()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
