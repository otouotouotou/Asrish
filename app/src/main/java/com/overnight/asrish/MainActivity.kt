package com.overnight.asrish

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Toast

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

    // --- 一覧の並び替え (v0.10.10 rev4〜、アプリ側だけの表示設定) ---
    // ソート順 (名前 / 日付 / 大きさ) と「設定済みを上に」は独立。
    // 設定済み優先は選んだソートの上に重ねて効く (安定ソート)
    private var sortMode: Int = SORT_NAME
    private var configuredFirst: Boolean = true

    private fun uiPrefs() = getSharedPreferences("ui_prefs", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sortMode = uiPrefs().getInt("sort_mode", SORT_NAME)
        configuredFirst = uiPrefs().getBoolean("configured_first", true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            // 画面背景 (v0.10.10 rev8〜): ライトは薄グレー、ダークは純黒
            setBackgroundColor(getColor(R.color.window_bg))
        }
        applySystemBarInsets(root)

        // --- タイトル + ☰ メニュー (v0.10.10 rev4〜、rev5 で ☰ を右端に) ---
        // 書き出し / 読み込み / ソート順はタイトル行右端の ☰ から開く
        // 文字メニューに集約し、一覧の表示領域を広げた
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Asrish"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        titleRow.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 20f
            setPadding(dp(12), dp(4), dp(4), dp(4))
            isClickable = true
            setOnClickListener { showMainMenu(this) }
        }
        titleRow.addView(menuButton)
        root.addView(titleRow)

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

    // --- タイトル横 ▼ のメニュー (v0.10.10 rev4〜) ---
    // 書き出し / 読み込み / ソート順 (サブメニュー、ラジオ) /
    // 設定済みを上に (チェック、ソート順とは独立)
    private fun showMainMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        val menu = popup.menu
        // 並びは動線順 (v0.10.10 rev6〜): よく触る表示系を上に、
        // たまにしか使わないファイル操作を下に
        val sortSub = menu.addSubMenu(0, MENU_SORT, 0, R.string.menu_sort_order)
        sortSub.add(GROUP_SORT, SORT_NAME, 0, R.string.sort_name)
        sortSub.add(GROUP_SORT, SORT_DATE, 1, R.string.sort_date)
        sortSub.add(GROUP_SORT, SORT_SIZE, 2, R.string.sort_size)
        sortSub.setGroupCheckable(GROUP_SORT, true, true)
        sortSub.findItem(sortMode)?.isChecked = true
        val cf = menu.add(0, MENU_CONFIGURED_FIRST, 1, R.string.menu_configured_first)
        cf.isCheckable = true
        cf.isChecked = configuredFirst
        menu.add(0, MENU_EXPORT, 2, R.string.button_export)
        menu.add(0, MENU_IMPORT, 3, R.string.button_import)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EXPORT -> startExport()
                MENU_IMPORT -> startImport()
                SORT_NAME, SORT_DATE, SORT_SIZE -> {
                    sortMode = item.itemId
                    uiPrefs().edit().putInt("sort_mode", sortMode).apply()
                    applyFilter()
                }
                MENU_CONFIGURED_FIRST -> {
                    configuredFirst = !configuredFirst
                    uiPrefs().edit()
                        .putBoolean("configured_first", configuredFirst).apply()
                    applyFilter()
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    // --- 設定ファイルの書き出し / 読み込み ---

    private fun startExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "asrish-settings.json")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/plain")
            )
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_EXPORT -> {
                try {
                    val json = SettingsTransfer.buildExportJson(this)
                    val out = contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("openOutputStream returned null")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, R.string.toast_exported, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Toast.makeText(this, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
                }
            }
            REQ_IMPORT -> {
                val text = try {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                } catch (t: Throwable) {
                    null
                }
                val parsed = text?.let { SettingsTransfer.parseImportJson(it) }
                if (parsed == null) {
                    Toast.makeText(this, R.string.toast_import_failed, Toast.LENGTH_LONG).show()
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.import_confirm_title)
                    .setMessage(getString(R.string.import_confirm_message, parsed.size))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        SettingsTransfer.applyImport(this, parsed)
                        Toast.makeText(
                            this,
                            getString(R.string.toast_imported, parsed.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        applyFilter()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
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
        // 選んだソート順で並べる (v0.10.10 rev4〜)。
        // 日付 = インストール日時の新しい順、大きさ = APK サイズの大きい順
        val pm2 = packageManager
        val baseSorted = when (sortMode) {
            SORT_DATE -> filtered.sortedByDescending {
                try {
                    pm2.getPackageInfo(it.packageName, 0).firstInstallTime
                } catch (t: Throwable) {
                    0L
                }
            }
            SORT_SIZE -> filtered.sortedByDescending {
                try {
                    java.io.File(it.sourceDir).length()
                } catch (t: Throwable) {
                    0L
                }
            }
            else -> filtered.sortedBy {
                pm2.getApplicationLabel(it).toString().lowercase()
            }
        }
        // 「設定済みを上に」はソート順とは独立 (v0.10.10〜)。
        // sortedBy は安定ソートなので、ON の時は選んだソートの
        // 並びを保ったまま設定済みだけが上に寄る
        val sorted = if (configuredFirst) {
            baseSorted.sortedBy { if (isConfigured(it.packageName)) 0 else 1 }
        } else {
            baseSorted
        }
        adapter = AppListAdapter(sorted)
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
            // 設定済みの行は背景も変える (v0.10.10〜)。最上段ソートと
            // セットで、設定済みかどうかを一目で判別できるようにする。
            // 行はリサイクルされるので、未設定では透明に戻すこと
            row.setBackgroundColor(
                if (configured) {
                    this@MainActivity.getColor(R.color.configured_row_bg)
                } else {
                    android.graphics.Color.TRANSPARENT
                }
            )
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

    companion object {
        private const val REQ_EXPORT = 1001
        private const val REQ_IMPORT = 1002

        // ▼ メニューの項目 ID。ソートの 3 項目はそのまま sortMode の値
        private const val MENU_EXPORT = 2001
        private const val MENU_IMPORT = 2002
        private const val MENU_SORT = 2003
        private const val MENU_CONFIGURED_FIRST = 2004
        private const val GROUP_SORT = 2100
        private const val SORT_NAME = 0
        private const val SORT_DATE = 1
        private const val SORT_SIZE = 2
    }
}
