package de.pauseqr.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/** Liste aller startbaren Apps mit Häkchen zum Blockieren. */
class AppSelectActivity : AppCompatActivity() {

    data class AppItem(val pkg: String, val label: String, val icon: Drawable)

    private val selected = mutableSetOf<String>()
    private var items: List<AppItem> = emptyList()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Während der Pause darf die Auswahl nicht geändert werden.
        if (Prefs.isLocked(this)) { finish(); return }

        setContentView(R.layout.activity_app_select)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val listView = findViewById<ListView>(R.id.listApps)
        val progress = findViewById<ProgressBar>(R.id.progress)
        selected += Prefs.getBlockedApps(this)

        adapter = AppAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val pkg = items[pos].pkg
            if (!selected.add(pkg)) selected.remove(pkg)
            adapter.notifyDataSetChanged()
        }

        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val initiallySelected = selected.toSet()
            val list = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo }
                .filter { it.packageName != packageName }
                .distinctBy { it.packageName }
                .map { AppItem(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedWith(
                    compareByDescending<AppItem> { it.pkg in initiallySelected }
                        .thenBy { it.label.lowercase() }
                )
            runOnUiThread {
                items = list
                progress.visibility = View.GONE
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        if (!Prefs.isLocked(this)) Prefs.setBlockedApps(this, selected)
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val item = items[position]
            v.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(item.icon)
            v.findViewById<TextView>(R.id.tvLabel).text = item.label
            v.findViewById<CheckBox>(R.id.cbBlocked).isChecked = item.pkg in selected
            return v
        }
    }
}
