package com.akira.studio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                val ok = ExtensionManager.importZip(this, uri)
                Toast.makeText(this, if (ok) "Imported" else "Import failed", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)

        listView = findViewById(R.id.listExtensions)
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importLauncher.launch(intent)
        }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val checked = listView.checkedItemPositions
            val items = ExtensionManager.list(this)
            for (i in items.indices) {
                if (checked.get(i)) {
                    ExtensionManager.delete(this, items[i].id)
                }
            }
            listView.clearChoices()
            refresh()
        }

        refresh()
    }

    private fun refresh() {
        ExtensionManager.reload(this)
        val exts = ExtensionManager.list(this)
        val adapter = ExtensionAdapter(this, exts)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
    }

    class ExtensionAdapter(
        private val activity: ExtensionsActivity,
        private val items: List<ExtensionManager.Extension>
    ) : android.widget.BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = convertView ?: activity.layoutInflater.inflate(R.layout.item_extension, parent, false)
            val ext = items[position]
            val cb = view.findViewById<android.widget.CheckBox>(R.id.cbEnabled)
            val name = view.findViewById<android.widget.TextView>(R.id.tvName)
            val ver = view.findViewById<android.widget.TextView>(R.id.tvVersion)
            val icon = view.findViewById<ImageView>(R.id.ivIcon)
            val btnOpen = view.findViewById<Button>(R.id.btnOpen)
            name.text = ext.name
            ver.text = ext.version
            cb.isChecked = ext.enabled
            icon.setImageDrawable(ExtensionManager.iconDrawable(activity, ext))
            cb.setOnCheckedChangeListener { _, isChecked ->
                ExtensionManager.setEnabled(activity, ext.id, isChecked)
            }
            btnOpen.setOnClickListener {
                ExtensionPopup.show(activity, ext)
            }
            return view
        }
    }
}
