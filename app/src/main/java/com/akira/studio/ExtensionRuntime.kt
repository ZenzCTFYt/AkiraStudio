package com.akira.studio

import android.webkit.WebView
import org.json.JSONObject

object ExtensionRuntime {

    var mainWebView: WebView? = null

    data class Endpoint(
        val extId: String,
        val webView: WebView,
        val callbackIds: MutableSet<String> = mutableSetOf()
    )

    private val endpoints = mutableListOf<Endpoint>()

    fun registerEndpoint(extId: String, webView: WebView) {
        if (endpoints.none { it.webView === webView }) {
            endpoints.add(Endpoint(extId, webView))
        }
    }

    fun unregisterEndpoint(webView: WebView) {
        endpoints.removeAll { it.webView === webView }
    }

    fun registerListener(extId: String, webView: WebView, callbackId: String) {
        val ep = endpoints.find { it.webView === webView } ?: run {
            registerEndpoint(extId, webView)
            endpoints.find { it.webView === webView }!!
        }
        ep.callbackIds.add(callbackId)
    }

    fun broadcast(senderExtId: String, senderWebView: WebView, message: String) {
        val safeMsg = try { JSONObject(message); message } catch (e: Exception) { JSONObject.quote(message) }
        for (ep in endpoints.toList()) {
            if (ep.webView === senderWebView) continue
            for (cb in ep.callbackIds.toList()) {
                ep.webView.post {
                    ep.webView.evaluateJavascript("window.chrome.runtime.__dispatchCallback('$cb', $safeMsg)", null)
                }
            }
        }
        // also dispatch to content script endpoints? they are registered separately
    }

    fun injectPolyfill(webView: WebView, ext: ExtensionManager.Extension) {
        val bridge = ChromeBridge(webView.context, ext, webView)
        webView.addJavascriptInterface(bridge, "AndroidChrome")
    }

    fun startBackground(context: android.content.Context, container: android.view.ViewGroup, ext: ExtensionManager.Extension) {
        if (backgroundWebViews.containsKey(ext.id)) return
        if (ext.backgroundScripts.isEmpty() && ext.serviceWorker == null) return
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        injectPolyfill(webView, ext)
        container.addView(webView, android.view.ViewGroup.LayoutParams(0, 0))
        webView.visibility = android.view.View.GONE
        backgroundWebViews[ext.id] = webView
        val scripts = ext.backgroundScripts.ifEmpty { listOfNotNull(ext.serviceWorker) }
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(ExtensionManager.CHROME_POLYFILL, null)
                for (s in scripts) {
                    val code = ExtensionManager.readFilePublic(ext.dir, s)
                    if (code != null) view?.evaluateJavascript(code, null)
                }
                registerEndpoint(ext.id, view!!)
            }
        }
        // load a blank page to trigger onPageFinished
        webView.loadDataWithBaseURL("file://" + ext.dir.absolutePath + "/", "<html><body></body></html>", "text/html", "UTF-8", null)
    }

    private val backgroundWebViews = mutableMapOf<String, WebView>()
}
