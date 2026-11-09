package com.akira.studio

import android.content.Context
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
            Extension(dir.name, name, version, dir, prefs(context).getBoolean(dir.name, true), scripts)
        } catch (e: Exception) {
            null
        }
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

    fun injectForUrl(context: Context, webView: WebView, url: String) {
        init(context)
        for (ext in extensions.filter { it.enabled }) {
            for (cs in ext.contentScripts) {
                if (!cs.matches.any { match(it, url) }) continue
                val cssText = cs.css.mapNotNull { readFile(ext.dir, it) }.joinToString("\n")
                if (cssText.isNotBlank()) {
                    val escaped = cssText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    webView.evaluateJavascript(
                        "(function(){try{var s=document.createElement('style');s.textContent='$escaped';(document.head||document.documentElement).appendChild(s);}catch(e){}})();",
                        null
                    )
                }
                for (jsFile in cs.js) {
                    val js = readFile(ext.dir, jsFile) ?: continue
                    webView.evaluateJavascript(js, null)
                }
            }
        }
    }

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
}
