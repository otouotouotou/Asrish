package com.overnight.asrish

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * アプリ別設定画面。
 * 変更は即モジュール内蔵の設定保存 (ConfigStore) に保存される。
 * 反映は対象アプリの次回起動 (Activity の再開) から。
 */
class PerAppActivity : Activity() {

    private lateinit var targetPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        val pm = packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(targetPackage, 0)).toString()
        } catch (t: Throwable) {
            targetPackage
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        applySystemBarInsets(root)

        root.addView(TextView(this).apply {
            text = label
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = targetPackage
            textSize = 12f
            setPadding(0, dp(4), 0, dp(16))
        })

        // --- 設定行 (名前 左詰め / 項目 右詰め、1 行表示) ---
        // 向き
        root.addView(settingRow(getString(R.string.label_orientation), spinner(
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_portrait),
                getString(R.string.opt_landscape),
                getString(R.string.opt_sensor)
            ),
            read(ConfigStore.FEATURE_ORIENTATION)
        ) { save(ConfigStore.FEATURE_ORIENTATION, it) }))
        root.addView(divider())

        // 全画面
        root.addView(settingRow(getString(R.string.label_fullscreen), spinner(
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_fullscreen_on),
                getString(R.string.opt_fullscreen_immersive),
                getString(R.string.opt_fullscreen_off)
            ),
            read(ConfigStore.FEATURE_FULLSCREEN)
        ) { save(ConfigStore.FEATURE_FULLSCREEN, it) }))
        root.addView(divider())

        // フォント倍率
        val fontPercents = listOf(0, 85, 90, 95, 100, 105, 110, 115, 130)
        val fontLabels = listOf(
            getString(R.string.opt_default), "85%", "90%", "95%",
            getString(R.string.opt_font_standard),
            "105%", "110%", "115%", "130%"
        )
        val currentPercent = read(ConfigStore.FEATURE_FONTSIZE)
        root.addView(settingRow(getString(R.string.label_fontsize), spinner(
            fontLabels,
            fontPercents.indexOf(currentPercent).coerceAtLeast(0)
        ) { save(ConfigStore.FEATURE_FONTSIZE, fontPercents[it]) }))
        root.addView(divider())

        // DPI (密度の絶対値を選ぶ。テスト対象の現在値は端末標準 = 480)
        val dpiValues = listOf(0, 240, 280, 320, 360, 400, 440, 480, 520, 560, 600, 640)
        val dpiLabels = dpiValues.map {
            if (it == 0) getString(R.string.opt_default) else it.toString()
        }
        val currentDpi = read(ConfigStore.FEATURE_DPI)
        root.addView(settingRow(getString(R.string.label_dpi), spinner(
            dpiLabels,
            dpiValues.indexOf(currentDpi).coerceAtLeast(0)
        ) { save(ConfigStore.FEATURE_DPI, dpiValues[it]) }))
        root.addView(divider())

        // スクリーンショット (行レイアウトに合わせて選択肢は短い表記に)
        root.addView(settingRow(getString(R.string.label_screenshot), spinner(
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_allow),
                getString(R.string.opt_deny)
            ),
            read(ConfigStore.FEATURE_SCREENSHOT)
        ) { save(ConfigStore.FEATURE_SCREENSHOT, it) }))
        root.addView(divider())

        // 画面ON維持
        val screenOn = Switch(this).apply {
            isChecked = read(ConfigStore.FEATURE_SCREEN_ON) == 1
            setOnCheckedChangeListener { _, checked ->
                save(ConfigStore.FEATURE_SCREEN_ON, if (checked) 1 else 0)
            }
        }
        root.addView(settingRow(getString(R.string.label_screen_on), screenOn))
        root.addView(divider())

        // ミュート
        val mute = Switch(this).apply {
            isChecked = read(ConfigStore.FEATURE_MUTE) == 1
            setOnCheckedChangeListener { _, checked ->
                save(ConfigStore.FEATURE_MUTE, if (checked) 1 else 0)
            }
        }
        root.addView(settingRow(getString(R.string.label_mute), mute))

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_note)
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(root)
        root.requestApplyInsets()
    }

    /**
     * 設定 1 件分を 1 行に組む: 設定名を左詰め、操作項目 (スピナー /
     * スイッチ) を右詰めで同じ行に置く (v0.4.1〜)。
     */
    private fun settingRow(label: String, control: View): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        row.addView(control)
        return row
    }

    /** 設定行の間の区切り線。色はテーマ (ライト/ダーク) で切り替わる */
    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
        setBackgroundColor(getColor(R.color.divider))
    }

    private fun spinner(options: List<String>, initial: Int, onSelect: (Int) -> Unit): Spinner {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(initial.coerceIn(0, options.size - 1))
        // 初期選択でのコールバックを無視するためのフラグ
        var first = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) {
                    first = false
                    return
                }
                onSelect(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return spinner
    }

    private fun read(feature: String): Int =
        ConfigStore.getInt(this, feature, targetPackage)

    private fun save(feature: String, value: Int) {
        try {
            ConfigStore.putInt(this, feature, targetPackage, value)
            Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.toast_save_failed, e.javaClass.simpleName, e.message),
                Toast.LENGTH_LONG
            ).show()
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
        const val EXTRA_PACKAGE = "target_package"
    }
}
