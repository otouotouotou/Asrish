package com.overnight.asrish

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

/**
 * アプリ別設定画面 (v0.10.1 でグループカード型 + 適用ボタン方式、
 * v0.10.2 で操作部品をプレビューに合わせて刷新し、数値の中央表示
 * など表示の細部を詰めて v0.10.3 で確定)。
 *
 * - 項目はグループ (表示 / 画面と動作 / その他) ごとのカードに並ぶ
 * - 変更はその場では保存せず、下書きとして保持する。画面下部の
 *   「適用」を押したときだけ ConfigStore に保存される。「破棄」で
 *   適用済みの状態に戻る。適用済みと異なる項目には ● が付く
 * - 操作部品は項目ごとに: 向き = セグメント、フォント倍率 / DPI /
 *   最小幅 = スライダー、全画面 / 言語 / スクリーンショット =
 *   ドロップダウン (スピナー)、ON/OFF 項目 = スイッチ
 * - 「デフォルト」ボタンはスライダー項目にだけ付ける。ドロップダウンと
 *   セグメントは選択肢の中に「デフォルト」自体があるのでボタンは付けない
 *   (二重になる)。スライダーには「デフォルト」の位置がないため、
 *   ボタンで下書きを初期値 (0) に戻す。スライダーのツマミは初期値の
 *   とき中立位置 (倍率 100% / DPI 480 / 最小幅 400) に置き、値ラベル
 *   には常にツマミ位置の数値を出す (未設定かどうかはデフォルト
 *   ボタンの有効 / 無効で判別する)。ドラッグを始めた時点でその
 *   位置の値が下書きに入る
 * - 保存後の反映は、今まで通り対象アプリの次回起動 (画面の再表示) から
 */
class PerAppActivity : Activity() {

    private lateinit var targetPackage: String

    /** 適用済み (ConfigStore に保存されている) 値 */
    private val applied = mutableMapOf<String, Int>()

    /** 編集中の下書き値。適用を押すまで保存されない */
    private val draft = mutableMapOf<String, Int>()

    private val handles = mutableListOf<RowHandle>()
    private lateinit var pendingText: TextView
    private lateinit var applyButton: Button
    private lateinit var discardButton: Button

    /** 1 項目分の操作部品と、プログラムからの値セット用クロージャ */
    private class RowHandle(
        val feature: String,
        val dot: TextView,
        val defaultButton: Button?,
        val showValue: (Int) -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        // 適用済みスナップショットを読み、下書きはそのコピーから始める
        for (feature in ALL_FEATURES) {
            val value = ConfigStore.getInt(this, feature, targetPackage)
            applied[feature] = value
            draft[feature] = value
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

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(TextView(this).apply {
            text = label
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = targetPackage
            textSize = 12f
            setPadding(0, dp(4), 0, dp(16))
        })

        // --- グループ: 表示 ---
        val displayRows = mutableListOf<View>()

        // 画面の向き (セグメント。保存値はセグメントの位置そのもの)
        displayRows += segmentedRow(
            ConfigStore.FEATURE_ORIENTATION, getString(R.string.label_orientation),
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_portrait),
                getString(R.string.opt_landscape),
                getString(R.string.opt_sensor)
            )
        )

        // 全画面 (API 30 / Android 11 以降でのみ有効。未満では
        // 行をグレーアウトし、スピナーを触れないようにする。
        // 「押せるのに動かない」が一番まずいので、理由も行に表示する)
        val fullscreenSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val fullscreenLabel = if (fullscreenSupported) {
            getString(R.string.label_fullscreen)
        } else {
            "${getString(R.string.label_fullscreen)} (${getString(R.string.note_requires_android_11)})"
        }
        val fullscreenRow = spinnerRow(
            ConfigStore.FEATURE_FULLSCREEN, fullscreenLabel,
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_fullscreen_on),
                getString(R.string.opt_fullscreen_immersive),
                getString(R.string.opt_fullscreen_off)
            ),
            listOf(0, 1, 2, 3),
            controlEnabled = fullscreenSupported
        )
        if (!fullscreenSupported) fullscreenRow.alpha = 0.45f
        displayRows += fullscreenRow

        // フォント倍率 (スライダー: 85〜130% を 5% 刻み)
        displayRows += sliderRow(
            ConfigStore.FEATURE_FONTSIZE, getString(R.string.label_fontsize),
            min = 85, max = 130, step = 5, neutral = 100,
            format = { "$it%" }
        )

        // DPI (スライダー: 240〜640 を 10 刻み。中立位置は端末標準の 480)
        displayRows += sliderRow(
            ConfigStore.FEATURE_DPI, getString(R.string.label_dpi),
            min = 240, max = 640, step = 10, neutral = 480,
            format = { "$it" }
        )

        // 最小幅 (スライダー: 320〜800 を 10 刻み。中立位置は端末標準の約 400)
        displayRows += sliderRow(
            ConfigStore.FEATURE_SMALLEST_WIDTH, getString(R.string.label_smallest_width),
            min = 320, max = 800, step = 10, neutral = 400,
            format = { "$it" }
        )

        // 言語 (ロケール、ドロップダウン)。保存値は ConfigStore.LOCALE_TAGS
        // へのインデックスそのもの。表示名は各言語自身の表記にする
        // (日本語 / English / 中文 (简体) など、リソース不要で正確)
        val localeLabels = ConfigStore.LOCALE_TAGS.mapIndexed { index, tag ->
            if (index == 0) {
                getString(R.string.opt_default)
            } else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale)
            }
        }
        displayRows += spinnerRow(
            ConfigStore.FEATURE_LOCALE, getString(R.string.label_locale),
            localeLabels, ConfigStore.LOCALE_TAGS.indices.toList()
        )

        content.addView(groupCard(getString(R.string.group_display), displayRows))

        // --- グループ: 画面と動作 ---
        val screenRows = mutableListOf<View>()
        screenRows += switchRow(
            ConfigStore.FEATURE_SCREEN_ON, getString(R.string.label_screen_on)
        )
        screenRows += switchRow(
            ConfigStore.FEATURE_SHOW_WHEN_LOCKED, getString(R.string.label_show_when_locked)
        )
        // スクリーンショット (ドロップダウン。選択肢は短い表記)
        screenRows += spinnerRow(
            ConfigStore.FEATURE_SCREENSHOT, getString(R.string.label_screenshot),
            listOf(
                getString(R.string.opt_default),
                getString(R.string.opt_allow),
                getString(R.string.opt_deny)
            ),
            listOf(0, 1, 2)
        )
        content.addView(groupCard(getString(R.string.group_screen), screenRows))

        // --- グループ: その他 ---
        val otherRows = mutableListOf<View>()
        otherRows += switchRow(
            ConfigStore.FEATURE_MUTE, getString(R.string.label_mute)
        )
        otherRows += switchRow(
            ConfigStore.FEATURE_EXCLUDE_RECENTS, getString(R.string.label_exclude_recents)
        )
        content.addView(groupCard(getString(R.string.group_other), otherRows))

        content.addView(TextView(this).apply {
            text = getString(R.string.settings_note)
            textSize = 13f
            setPadding(0, dp(16), 0, dp(8))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(content)
        }
        root.addView(scroll)

        // --- 適用バー (画面下部に固定) ---
        root.addView(divider())
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        pendingText = TextView(this).apply {
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        bar.addView(pendingText)
        discardButton = Button(this).apply {
            text = getString(R.string.button_discard)
            setOnClickListener { discardChanges() }
        }
        bar.addView(discardButton)
        applyButton = Button(this).apply {
            text = getString(R.string.button_apply)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
            setOnClickListener { applyChanges() }
        }
        bar.addView(applyButton)
        root.addView(bar)

        setContentView(root)
        root.requestApplyInsets()

        for (handle in handles) refreshRow(handle)
        refreshBar()
    }

    /**
     * グループ 1 つ分: 見出し + カード (角丸背景) の中に項目行を並べる。
     */
    private fun groupCard(title: String, rows: List<View>): LinearLayout {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }
        group.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.sub_text))
            setPadding(dp(4), 0, 0, dp(6))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(getColor(R.color.card_bg))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) card.addView(divider())
            card.addView(row)
        }
        group.addView(card)
        return group
    }

    /**
     * 項目の見出し行: 未適用マーク (●) / 設定名 / 値ラベル (任意) /
     * デフォルトボタン (任意)。縦パディングは呼び出し側が付ける。
     * RowHandle の登録もここで行う。
     *
     * 値ラベルがある行 (スライダー) は FrameLayout で組み、数値を
     * 行の中央に置く。行は画面に対して左右対称の余白 (ルート 16dp +
     * カード 14dp) の中にあるので、行の中央 = 画面の中央になる。
     */
    private fun makeHeader(
        feature: String, label: String,
        withDefaultButton: Boolean, valueLabel: TextView?,
        showValue: (Int) -> Unit
    ): ViewGroup {
        val dot = TextView(this).apply {
            text = "●"
            textSize = 10f
            setTextColor(getColor(R.color.pending_dot))
            layoutParams = LinearLayout.LayoutParams(
                dp(18), LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val nameView = TextView(this).apply {
            text = label
            textSize = 16f
            maxLines = 2
        }
        var defaultButton: Button? = null
        if (withDefaultButton) {
            defaultButton = Button(this).apply {
                text = getString(R.string.button_default)
                textSize = 11f
                // テーマ標準の最小サイズを外して一回り小さくする
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { setDraft(feature, 0, fromControl = false) }
            }
        }
        handles += RowHandle(feature, dot, defaultButton, showValue)

        if (valueLabel != null) {
            // スライダー行: 左に設定名、中央に数値、右にデフォルトボタン
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            left.addView(dot)
            left.addView(nameView)
            frame.addView(left)
            valueLabel.gravity = Gravity.CENTER
            valueLabel.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            frame.addView(valueLabel)
            if (defaultButton != null) {
                defaultButton.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
                frame.addView(defaultButton)
            }
            return frame
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(dot)
        nameView.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )
        header.addView(nameView)
        if (defaultButton != null) header.addView(defaultButton)
        return header
    }

    /** 見出し行の下に操作部品を置く、2 段組の行を組み立てる */
    private fun stackedRow(header: View, control: View): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(header)
        control.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        row.addView(control)
        return row
    }

    /**
     * スピナー (ドロップダウン) 項目の行を作る。options と values は
     * 同じ長さで、位置 i の選択が値 values[i] に対応する。
     * 「デフォルト」が選択肢に含まれるので、デフォルトボタンは付けない。
     */
    private fun spinnerRow(
        feature: String, label: String,
        options: List<String>, values: List<Int>,
        controlEnabled: Boolean = true
    ): View {
        val spinner = Spinner(this)
        spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.isEnabled = controlEnabled
        // プログラムからの setSelection で来るコールバックを 1 回読み飛ばす
        var suppress = true
        val initialPos = values.indexOf(draft[feature] ?: 0).coerceAtLeast(0)
        spinner.setSelection(initialPos)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (suppress) {
                    suppress = false
                    return
                }
                setDraft(feature, values[position], fromControl = true)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val showValue: (Int) -> Unit = { value ->
            val pos = values.indexOf(value).coerceAtLeast(0)
            if (spinner.selectedItemPosition != pos) {
                suppress = true
                spinner.setSelection(pos)
            }
        }
        val header = makeHeader(feature, label, withDefaultButton = false, null, showValue)
        header.setPadding(0, dp(10), 0, dp(10))
        header.addView(spinner)
        return header
    }

    /** スイッチ項目の行を作る。値は 0 / 1。 */
    private fun switchRow(feature: String, label: String): View {
        val toggle = Switch(this)
        var suppress = false
        toggle.isChecked = (draft[feature] ?: 0) == 1
        toggle.setOnCheckedChangeListener { _, checked ->
            if (suppress) {
                suppress = false
                return@setOnCheckedChangeListener
            }
            setDraft(feature, if (checked) 1 else 0, fromControl = true)
        }
        val showValue: (Int) -> Unit = { value ->
            val target = value == 1
            if (toggle.isChecked != target) {
                suppress = true
                toggle.isChecked = target
            }
        }
        val header = makeHeader(feature, label, withDefaultButton = false, null, showValue)
        header.setPadding(0, dp(10), 0, dp(10))
        header.addView(toggle)
        return header
    }

    /**
     * セグメント項目の行を作る (画面の向き)。保存値はセグメントの
     * 位置そのもの。「デフォルト」がセグメントに含まれるので、
     * デフォルトボタンは付けない。
     */
    private fun segmentedRow(feature: String, label: String, options: List<String>): View {
        val mainTextColor = TextView(this).currentTextColor
        val subTextColor = getColor(R.color.sub_text)
        val segments = mutableListOf<TextView>()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0x00000000)
                setStroke(dp(1), getColor(R.color.divider))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        options.forEachIndexed { index, text ->
            val segment = TextView(this).apply {
                this.text = text
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(2), dp(8), dp(2), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                isClickable = true
                // 「自動回転 (センサー)」のような長い文言は自動で縮小する
                setAutoSizeTextTypeUniformWithConfiguration(
                    8, 12, 1, TypedValue.COMPLEX_UNIT_SP
                )
                setOnClickListener { setDraft(feature, index, fromControl = false) }
            }
            segments += segment
            container.addView(segment)
        }
        val showValue: (Int) -> Unit = { value ->
            segments.forEachIndexed { i, segment ->
                val selected = i == value
                segment.background = if (selected) {
                    GradientDrawable().apply {
                        setColor(getColor(R.color.divider))
                        cornerRadius = dp(8).toFloat()
                    }
                } else {
                    null
                }
                segment.setTextColor(if (selected) mainTextColor else subTextColor)
                segment.setTypeface(
                    Typeface.DEFAULT,
                    if (selected) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }
        showValue(draft[feature] ?: 0)
        val header = makeHeader(feature, label, withDefaultButton = false, null, showValue)
        return stackedRow(header, container)
    }

    /**
     * スライダー項目の行を作る (フォント倍率 / DPI / 最小幅)。
     * スライダーの範囲は min..max (step 刻み) で、「デフォルト」の
     * 位置は持たない。下書きが初期値 (0) のときはツマミを中立位置
     * (neutral) に置く。値ラベルは常に数値表示で、初期値のときは
     * 中立位置の数値になる。デフォルトに戻すのは項目別のデフォルト
     * ボタン (見出し行の右端)。
     * 保存値が刻みに無い場合 (旧版の最小幅 411 など) は、ツマミは
     * 最寄りの位置に置き、ラベルには実際の値を出す (ドラッグした
     * 時点で刻みに丸まる)。
     */
    private fun sliderRow(
        feature: String, label: String,
        min: Int, max: Int, step: Int, neutral: Int,
        format: (Int) -> String
    ): View {
        val positions = (max - min) / step
        fun valueAt(position: Int): Int = min + position * step
        fun positionOf(value: Int): Int =
            ((value - min).toFloat() / step).roundToInt().coerceIn(0, positions)

        val valueLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(getColor(R.color.sub_text))
            setPadding(dp(8), 0, dp(8), 0)
        }
        val seekBar = SeekBar(this).apply { this.max = positions }
        val showValue: (Int) -> Unit = { value ->
            // 値ラベルは常に数値を出す。未設定 (0) のときは中立位置の
            // 数値になり、未設定かどうかはデフォルトボタンの
            // 有効 / 無効で判別する
            valueLabel.text = if (value == 0) format(neutral) else format(value)
            val position = if (value == 0) positionOf(neutral) else positionOf(value)
            if (seekBar.progress != position) seekBar.progress = position
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?, progress: Int, fromUser: Boolean
            ) {
                if (!fromUser) return
                // ドラッグ中は値ラベルもその場で追随させる
                valueLabel.text = format(valueAt(progress))
                setDraft(feature, valueAt(progress), fromControl = true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        showValue(draft[feature] ?: 0)
        val header = makeHeader(
            feature, label, withDefaultButton = true, valueLabel, showValue
        )
        return stackedRow(header, seekBar)
    }

    /**
     * 下書きを 1 件更新する。fromControl = true のときは操作部品側が
     * 既に新しい値を表示しているので、部品への書き戻しはしない。
     */
    private fun setDraft(feature: String, value: Int, fromControl: Boolean) {
        if (draft[feature] == value) return
        draft[feature] = value
        val handle = handles.firstOrNull { it.feature == feature } ?: return
        if (!fromControl) handle.showValue(value)
        refreshRow(handle)
        refreshBar()
    }

    /** 未適用マークとデフォルトボタンの有効状態を下書きに合わせる */
    private fun refreshRow(handle: RowHandle) {
        val value = draft[handle.feature] ?: 0
        handle.dot.visibility =
            if (value != (applied[handle.feature] ?: 0)) View.VISIBLE else View.INVISIBLE
        handle.defaultButton?.isEnabled = value != 0
    }

    /** 適用バーの文言とボタンの有効状態を未適用件数に合わせる */
    private fun refreshBar() {
        val count = ALL_FEATURES.count { (draft[it] ?: 0) != (applied[it] ?: 0) }
        pendingText.text = if (count == 0) {
            getString(R.string.label_no_changes)
        } else {
            getString(R.string.label_pending_changes, count)
        }
        applyButton.isEnabled = count > 0
        discardButton.isEnabled = count > 0
    }

    /** 下書きを ConfigStore に保存し、適用済みスナップショットを更新する */
    private fun applyChanges() {
        var allSaved = true
        for (feature in ALL_FEATURES) {
            val value = draft[feature] ?: 0
            if (value == (applied[feature] ?: 0)) continue
            try {
                ConfigStore.putInt(this, feature, targetPackage, value)
                applied[feature] = value
            } catch (e: Exception) {
                allSaved = false
                Toast.makeText(
                    this,
                    getString(
                        R.string.toast_save_failed, e.javaClass.simpleName, e.message
                    ),
                    Toast.LENGTH_LONG
                ).show()
                break
            }
        }
        if (allSaved) {
            Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
        }
        for (handle in handles) refreshRow(handle)
        refreshBar()
    }

    /** 下書きを捨てて、操作部品の表示を適用済みの値に戻す */
    private fun discardChanges() {
        for (feature in ALL_FEATURES) {
            draft[feature] = applied[feature] ?: 0
        }
        for (handle in handles) {
            handle.showValue(draft[handle.feature] ?: 0)
            refreshRow(handle)
        }
        refreshBar()
    }

    /** 区切り線。色はテーマ (ライト/ダーク) で切り替わる */
    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
        setBackgroundColor(getColor(R.color.divider))
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

        private val ALL_FEATURES = listOf(
            ConfigStore.FEATURE_ORIENTATION,
            ConfigStore.FEATURE_FULLSCREEN,
            ConfigStore.FEATURE_FONTSIZE,
            ConfigStore.FEATURE_DPI,
            ConfigStore.FEATURE_SMALLEST_WIDTH,
            ConfigStore.FEATURE_LOCALE,
            ConfigStore.FEATURE_SCREENSHOT,
            ConfigStore.FEATURE_SCREEN_ON,
            ConfigStore.FEATURE_SHOW_WHEN_LOCKED,
            ConfigStore.FEATURE_MUTE,
            ConfigStore.FEATURE_EXCLUDE_RECENTS
        )
    }
}
