package com.overnight.asrish

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * アプリ一覧画面。
 * - アプリをタップするとアプリ別設定画面 (PerAppActivity) が開く
 * - 設定済みのアプリには印が付く
 * 設定の保存・受け渡しは ConfigStore + Remote Preferences
 * (フレームワーク経由) のため、特別な許可は不要 (v0.3.0〜)。
 * v0.3.0 でアプリ名・パッケージ名の検索窓を追加。
 */
class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var adapter: AppListAdapter
    private lateinit var searchBox: EditText
    private var allApps: List<ApplicationInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        applySystemBarInsets(root)

        val title = TextView(this).apply {
            text = "Asrish"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        val desc = TextView(this).apply {
            text = getString(R.string.app_desc)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(desc)

        // --- 検索窓 (v0.3.0〜): アプリ名・パッケージ名で絞り込み ---
        searchBox = EditText(this).apply {
            hint = getString(R.string.search_hint)
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = applyFilter()
            })
        }
        root.addView(searchBox)

        listView = ListView(this)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        listView.setOnItemClickListener { _, _, position, _ ->
            val info = adapter.getItem(position)
            startActivity(
                Intent(this, PerAppActivity::class.java)
                    .putExtra(PerAppActivity.EXTRA_PACKAGE, info.packageName)
            )
        }

        setContentView(root)
        root.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        allApps = loadApps()
        applyFilter()
    }

    /** 検索窓の文字列でアプリ一覧を絞り込む */
    private fun applyFilter() {
        val q = searchBox.text.toString().trim().lowercase()
        val pm = packageManager
        val filtered = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                pm.getApplicationLabel(it).toString().lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }
        }
        adapter = AppListAdapter(filtered)
        listView.adapter = adapter
    }

    private fun loadApps(): List<ApplicationInfo> {
        val pm = packageManager
        val all = pm.getInstalledApplications(0)
        return all.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }

    /** 設定済み (いずれかの機能がデフォルト以外) かどうか */
    private fun isConfigured(packageName: String): Boolean =
        ConfigStore.isConfigured(this, packageName)

    private inner class AppListAdapter(val apps: List<ApplicationInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val pm = packageManager
            val info = apps[position]
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                addView(ImageView(this@MainActivity), LinearLayout.LayoutParams(dp(40), dp(40)))
                val texts = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                    addView(TextView(this@MainActivity).apply { textSize = 16f; tag = "label" })
                    addView(
                        TextView(this@MainActivity).apply {
                            textSize = 12f
                            setTextColor(this@MainActivity.getColor(R.color.sub_text))
                            tag = "sub"
                        }
                    )
                }
                addView(texts)
            }
            (row.getChildAt(0) as ImageView).setImageDrawable(pm.getApplicationIcon(info))
            val texts = row.getChildAt(1) as LinearLayout
            (texts.findViewWithTag("label") as TextView).text = pm.getApplicationLabel(info)
            val configured = isConfigured(info.packageName)
            (texts.findViewWithTag("sub") as TextView).text =
                info.packageName + if (configured) {
                    this@MainActivity.getString(R.string.configured_suffix)
                } else {
                    ""
                }
            return row
        }
    }

    /**
     * ステータスバー / ナビゲーションバーの高さ分だけ余白を足す。
     * HyperOS 3 (targetSdk 35) ではコンテンツがバーの下に潜り込み、
     * 画面上部が隠れてしまうため (v0.1.2 で発生)。
     */
    private fun applySystemBarInsets(root: View) {
        val base = dp(16)
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                base,
                base + insets.systemWindowInsetTop,
                base,
                base + insets.systemWindowInsetBottom
            )
            insets
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
