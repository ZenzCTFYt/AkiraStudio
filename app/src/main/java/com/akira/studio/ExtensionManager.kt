package com.akira.studio

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ExtensionManager {

    data class ContentScript(
        val matches: List<String>,
        val js: List<String>,
        val css: List<String>,
        val runAt: String
    )

    data class Extension(
        val id: String,
        val name: String,
        val version: String,
        val dir: File,
        var enabled: Boolean,
        val manifestVersion: Int,
        val iconPath: String?,
        val actionPopup: String?,
        val backgroundScripts: List<String>,
        val serviceWorker: String?,
        val contentScripts: List<ContentScript>
    )

    private val extensions = mutableListOf<Extension>()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        load(context)
    }

    fun list(context: Context): List<Extension> {
        init(context)
        return extensions.toList()
    }

    fun reload(context: Context) {
        loaded = false
        load(context)
    }

    private fun load(context: Context) {
        extensions.clear()
        val root = File(context.filesDir, "extensions")
        root.mkdirs()
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            parseManifest(context, dir)?.let { extensions.add(it) }
        }
        loaded = true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("akira_extensions", Context.MODE_PRIVATE)

    private fun parseManifest(context: Context, dir: File): Extension? {
        val mf = File(dir, "manifest.json")
        if (!mf.exists()) return null
        return try {
            val json = JSONObject(mf.readText())
            val name = json.optString("name", dir.name)
            val version = json.optString("version", "0")
            val manifestVersion = json.optInt("manifest_version", 2)

            val action = json.optJSONObject("action")
                ?: json.optJSONObject("browser_action")
                ?: json.optJSONObject("page_action")
            val defaultPopup = action?.optString("default_popup")?.takeIf { it.isNotBlank() }

            val iconsObj = action?.optJSONObject("default_icon")
                ?: json.optJSONObject("icons")
            val iconPath = pickIcon(iconsObj, dir)

            val bg = json.optJSONObject("background")
            val bgScripts = bg?.optJSONArray("scripts")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: emptyList()
            val serviceWorker = bg?.optString("service_worker")?.takeIf { it.isNotBlank() }

            val csArr = json.optJSONArray("content_scripts") ?: JSONArray()
            val scripts = mutableListOf<ContentScript>()
            for (i in 0 until csArr.length()) {
                val o = csArr.getJSONObject(i)
                val matches = o.optJSONArray("matches")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: listOf("*://*/*")
                val js = o.optJSONArray("js")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList()
                val css = o.optJSONArray("css")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList()
                val runAt = o.optString("run_at", "document_idle")
                scripts.add(ContentScript(matches, js, css, runAt))
            }

            Extension(dir.name, name, version, dir, prefs(context).getBoolean(dir.name, true),
                manifestVersion, iconPath, defaultPopup, bgScripts, serviceWorker, scripts)
        } catch (e: Exception) {
            null
        }
    }

    private fun pickIcon(iconsObj: JSONObject?, dir: File): String? {
        if (iconsObj == null) return null
        // choose largest size
        var bestSize = 0
        var bestPath: String? = null
        val keys = iconsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val size = k.toIntOrNull() ?: 0
            val path = iconsObj.optString(k)
            if (size >= bestSize && path.isNotBlank()) {
                bestSize = size
                bestPath = path
            }
        }
        return bestPath
    }

    fun importZip(context: Context, uri: Uri): Boolean {
        init(context)
        val temp = File(context.cacheDir, "ext_import_${System.currentTimeMillis()}")
        temp.mkdirs()
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val out = File(temp, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return false

            val manifestFile = findManifest(temp) ?: run { temp.deleteRecursively(); return false }
            val base = manifestFile.parentFile!!
            val manifest = JSONObject(manifestFile.readText())
            val id = sanitize(manifest.optString("name", "extension")) + "_" + System.currentTimeMillis()
            val dest = File(context.filesDir, "extensions/$id")
            if (dest.exists()) dest.deleteRecursively()
            base.copyRecursively(dest, overwrite = true)

            val ext = parseManifest(context, dest) ?: run { dest.deleteRecursively(); return false }
            extensions.add(ext)
            prefs(context).edit().putBoolean(ext.id, true).apply()
            ext.enabled = true
            temp.deleteRecursively()
            true
        } catch (e: Exception) {
            temp.deleteRecursively()
            false
        }
    }

    private fun findManifest(dir: File, depth: Int = 0): File? {
        val f = File(dir, "manifest.json")
        if (f.exists()) return f
        if (depth > 4) return null
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                findManifest(child, depth + 1)?.let { return it }
            }
        }
        return null
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        init(context)
        val ext = extensions.find { it.id == id } ?: return
        ext.enabled = enabled
        prefs(context).edit().putBoolean(id, enabled).apply()
    }

    fun delete(context: Context, id: String) {
        init(context)
        val ext = extensions.find { it.id == id } ?: return
        ext.dir.deleteRecursively()
        extensions.remove(ext)
        prefs(context).edit().remove(id).apply()
    }

    fun iconDrawable(context: Context, ext: Extension): Drawable? {
        val path = ext.iconPath ?: return null
        val f = File(ext.dir, path)
        if (!f.exists()) return null
        return Drawable.createFromPath(f.absolutePath)
    }

    fun popupFile(ext: Extension): File? {
        val p = ext.actionPopup ?: return null
        val f = File(ext.dir, p)
        return if (f.exists()) f else null
    }

    fun injectForUrl(context: Context, webView: WebView, url: String) {
        init(context)
        for (ext in extensions.filter { it.enabled }) {
            for (cs in ext.contentScripts) {
                if (!cs.matches.any { match(it, url) }) continue
                // css
                val cssText = cs.css.mapNotNull { readFile(ext.dir, it) }.joinToString("\n")
                if (cssText.isNotBlank()) {
                    val escaped = cssText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    webView.evaluateJavascript(
                        "(function(){try{var s=document.createElement('style');s.textContent='$escaped';(document.head||document.documentElement).appendChild(s);}catch(e){}})();",
                        null
                    )
                }
                // js
                for (jsFile in cs.js) {
                    val js = readFile(ext.dir, jsFile) ?: continue
                    // inject polyfill first
                    webView.evaluateJavascript(CHROME_POLYFILL, null)
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

    fun readFilePublic(base: File, rel: String): String? = readFile(base, rel)

    private fun readFile(base: File, rel: String): String? {
        val f = File(base, rel)
        return if (f.exists() && f.isFile) f.readText() else null
    }

    private fun match(pattern: String, url: String): Boolean {
        if (pattern == "<all_urls>") return true
        val regex = buildString {
            append("^")
            for (c in pattern) {
                when (c) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.', '(', ')', '+', '[', ']', '{', '}', '^', '$', '|' , '\\' -> {
                        append("\\")
                        append(c)
                    }
                    else -> append(c)
                }
            }
            append("$")
        }
        return try { Regex(regex).matches(url) } catch (e: Exception) { false }
    }

    private fun sanitize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "ext" }

    const val CHROME_POLYFILL = """
(function(){
  if(window.chrome && window.chrome.runtime && window.chrome.__akira) return;
  window.chrome = window.chrome || {};
  window.chrome.__akira = true;
  const AC = window.AndroidChrome;
  function safeParse(s){ try { return JSON.parse(s); } catch(e){ return null; } }
  window.chrome.runtime = window.chrome.runtime || {};
  window.chrome.runtime.id = (AC && AC.getExtId) ? AC.getExtId() : 'akira_ext';
  window.chrome.runtime.getURL = function(p){ return AC ? AC.getURL(p) : p; };
  window.chrome.runtime.sendMessage = function(msg, cb){
    if(!AC) return;
    AC.sendMessage(JSON.stringify(msg==null?'':msg));
    if(cb) setTimeout(()=>cb(),0);
  };
  window.chrome.runtime.onMessage = window.chrome.runtime.onMessage || { addListener: function(cb){ if(AC) AC.addMessageListener('cb'); window.__chromeMsgCbs = window.__chromeMsgCbs||[]; window.__chromeMsgCbs.push(cb); } };
  window.chrome.runtime.__dispatchCallback = function(id,msg){ (window.__chromeMsgCbs||[]).forEach(function(cb){ try{cb(msg);}catch(e){} }); };
  window.chrome.storage = window.chrome.storage || {};
  window.chrome.storage.local = {
    get: function(keys, cb){
      if(!AC){ if(cb) cb({}); return; }
      let all = safeParse(AC.storageGetAll()) || {};
      let res = {};
      if(keys==null){ res = all; }
      else if(typeof keys === 'string'){ res[keys]=all[keys]; }
      else if(Array.isArray(keys)){ keys.forEach(k=>{ res[k]=all[k]; }); }
      else if(typeof keys === 'object'){ Object.keys(keys).forEach(k=>{ res[k] = (all[k]!==undefined?all[k]:keys[k]); }); }
      if(cb) cb(res);
      return Promise.resolve(res);
    },
    set: function(items, cb){
      if(AC){ Object.keys(items||{}).forEach(k=>{ AC.storageSet(k, JSON.stringify(items[k])); }); }
      if(cb) cb();
      return Promise.resolve();
    },
    remove: function(keys, cb){
      if(AC){ (Array.isArray(keys)?keys:[keys]).forEach(k=>AC.storageSet(k,'null')); }
      if(cb) cb();
      return Promise.resolve();
    }
  };
  window.chrome.tabs = window.chrome.tabs || {};
  window.chrome.tabs.query = function(q, cb){ if(cb) cb([{id:1,url: AC?AC.currentUrl():location.href,active:true}]); return Promise.resolve([{id:1}]); };
  window.chrome.tabs.sendMessage = function(tabId,msg,cb){ window.chrome.runtime.sendMessage(msg,cb); };
  window.chrome.scripting = window.chrome.scripting || {};
  window.chrome.scripting.executeScript = function(inj, cb){
    if(AC && inj && inj.func){ AC.executeScript(inj.func.toString()); }
    if(cb) cb([]);
  };
  window.chrome.action = window.chrome.action || { setIcon:function(){}, setTitle:function(){}, setPopup:function(){} };
})();
"""
}
