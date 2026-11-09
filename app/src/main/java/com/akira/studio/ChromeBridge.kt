package com.akira.studio

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class ChromeBridge(
    private val context: Context,
    private val ext: ExtensionManager.Extension,
    private val webView: WebView
) {
    private val prefs by lazy { context.getSharedPreferences("ext_storage_${ext.id}", Context.MODE_PRIVATE) }

    @JavascriptInterface
    fun getExtId(): String = ext.id

    @JavascriptInterface
    fun getURL(path: String): String {
        val p = path.trimStart('/')
        return "file://" + ext.dir.absolutePath + "/" + p
    }

    @JavascriptInterface
    fun storageGetAll(): String {
        val obj = JSONObject()
        prefs.all.forEach { (k, v) ->
            val s = v?.toString() ?: "null"
            val parsed = try { org.json.JSONTokener(s).nextValue() } catch (e: Exception) { s }
            obj.put(k, parsed)
        }
        return obj.toString()
    }

    @JavascriptInterface
    fun storageGet(key: String): String {
        return prefs.getString(key, "null") ?: "null"
    }

    @JavascriptInterface
    fun storageSet(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun sendMessage(message: String) {
        ExtensionRuntime.broadcast(ext.id, webView, message)
    }

    @JavascriptInterface
    fun addMessageListener(cbId: String) {
        ExtensionRuntime.registerListener(ext.id, webView, cbId)
    }

    @JavascriptInterface
    fun executeScript(code: String) {
        val main = ExtensionRuntime.mainWebView ?: return
        main.post { main.evaluateJavascript(code, null) }
    }

    @JavascriptInterface
    fun currentUrl(): String {
        return ExtensionRuntime.mainWebView?.url ?: ""
    }

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("AkiraExt", msg)
    }
}
