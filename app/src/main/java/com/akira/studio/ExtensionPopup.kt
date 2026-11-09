package com.akira.studio

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object ExtensionPopup {

    fun show(context: Context, ext: ExtensionManager.Extension) {
        val popupFile = ExtensionManager.popupFile(ext) ?: return
        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        ExtensionRuntime.injectPolyfill(webView, ext)

        val dm = context.resources.displayMetrics
        val w = (dm.widthPixels * 0.92).toInt()
        val h = (dm.heightPixels * 0.72).toInt()

        dialog.setContentView(webView)
        dialog.window?.setLayout(w, h)
        dialog.setCanceledOnTouchOutside(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(ExtensionManager.CHROME_POLYFILL, null)
                ExtensionRuntime.registerEndpoint(ext.id, view!!)
            }
        }
        webView.loadUrl("file://" + popupFile.absolutePath)

        dialog.show()
    }
}
