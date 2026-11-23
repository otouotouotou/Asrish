package com.overnight.asrish

import android.content.Context
import org.json.JSONObject

/**
 * 設定のファイル書き出し / 読み込み (設定アプリ専用)。
 *
 * 形式は JSON:
 * {
 *   "app": "Asrish",
 *   "format": 1,
 *   "version": "<versionName>",
 *   "settings": {
 *     "com.example.app": { "orientation": 1, "fontsize": 110, ... },
 *     ...
 *   }
 * }
 *
 * - 書き出し: ローカル保存 (ConfigStore) のうちデフォルト (0) でない
 *   項目だけを対象にする。
 * - 形式の目印はアプリ名 "Asrish" であってパッケージ名ではない。
 *   applicationId を変えたビルド同士でもファイルをやり取りできる
 *   ようにするため (キーも対象アプリのパッケージ名だけに依存する)。
 * - 読み込み: 現在の設定をファイルの内容で「全置き換え」する。
 *   機能名が未知の項目・範囲外の値は黙って捨てる (手編集や将来版の
 *   ファイルで壊れないように)。置き換え後は Remote Preferences も
 *   クリアしてから同期し直す (消えたアプリの設定が残留しないため)。
 *
 * ファイルの受け渡しは Storage Access Framework (MainActivity) で、
 * ストレージ権限は不要。この object はフック側からは参照しないこと。
 */
object SettingsTransfer {

    private const val FORMAT_VERSION = 1

    private val KNOWN_FEATURES = setOf(
        ConfigStore.FEATURE_ORIENTATION,
        ConfigStore.FEATURE_FULLSCREEN,
        ConfigStore.FEATURE_SCREEN_ON,
        ConfigStore.FEATURE_SCREENSHOT,
        ConfigStore.FEATURE_FONTSIZE,
        ConfigStore.FEATURE_DPI,
        ConfigStore.FEATURE_MUTE,
        ConfigStore.FEATURE_LOCALE,
        ConfigStore.FEATURE_FORCE_LTR,
        ConfigStore.FEATURE_NOTIF_ONGOING,
        ConfigStore.FEATURE_NOTIF_INSISTENT,
        ConfigStore.FEATURE_NOTIF_MUTE,
        ConfigStore.FEATURE_CLIPBOARD_BLOCK,
        ConfigStore.FEATURE_SMALLEST_WIDTH,
        ConfigStore.FEATURE_EXCLUDE_RECENTS,
        ConfigStore.FEATURE_SHOW_WHEN_LOCKED
    )

    /** 値がその機能の取りうる範囲内か (スライダー系は 0 = 未設定も可) */
    private fun isValidValue(feature: String, value: Int): Boolean = when (feature) {
        ConfigStore.FEATURE_FONTSIZE -> value == 0 || value in 85..130
        ConfigStore.FEATURE_DPI -> value == 0 || value in 240..640
        ConfigStore.FEATURE_SMALLEST_WIDTH -> value == 0 || value in 320..800
        ConfigStore.FEATURE_LOCALE -> value in ConfigStore.LOCALE_TAGS.indices
        // インデックス系 (向き・全画面・スクリーンショット・トグル類)
        else -> value in 0..3
    }

    /** 現在の全設定 (デフォルト以外) を JSON 文字列にする */
    fun buildExportJson(context: Context): String {
        val all = ConfigStore.localPrefs(context).all
        val settings = JSONObject()
        for ((key, value) in all) {
            if (value !is Int || value == 0) continue
            val sep = key.indexOf('|')
            if (sep <= 0) continue
            val feature = key.substring(0, sep)
            val pkg = key.substring(sep + 1)
            if (feature !in KNOWN_FEATURES || pkg.isEmpty()) continue
            val perApp = settings.optJSONObject(pkg) ?: JSONObject().also {
                settings.put(pkg, it)
            }
            perApp.put(feature, value)
        }
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (t: Throwable) {
            null
        }
        val root = JSONObject()
        root.put("app", "Asrish")
        root.put("format", FORMAT_VERSION)
        if (versionName != null) root.put("version", versionName)
        root.put("settings", settings)
        return root.toString(2)
    }

    /**
     * 読み込み用 JSON を検証して (パッケージ名 → 機能名 → 値) に変換する。
     * Asrish の設定ファイルとして読めない場合は null。
     * settings が空のファイルは「全消去」として有効 (空マップを返す)。
     */
    fun parseImportJson(text: String): Map<String, Map<String, Int>>? {
        val root = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return null
        }
        if (root.has("app") && root.optString("app") != "Asrish") return null
        if (root.has("format") && root.optInt("format", FORMAT_VERSION) > FORMAT_VERSION) {
            return null
        }
        val settings = root.optJSONObject("settings") ?: return null
        val result = mutableMapOf<String, Map<String, Int>>()
        val packages = settings.keys()
        while (packages.hasNext()) {
            val pkg = packages.next()
            val perApp = settings.optJSONObject(pkg) ?: continue
            val entries = mutableMapOf<String, Int>()
            val features = perApp.keys()
            while (features.hasNext()) {
                val feature = features.next()
                if (feature !in KNOWN_FEATURES) continue
                if (!perApp.has(feature) || perApp.isNull(feature)) continue
                val value = perApp.optInt(feature, Int.MIN_VALUE)
                if (value == Int.MIN_VALUE) continue
                if (!isValidValue(feature, value) || value == 0) continue
                entries[feature] = value
            }
            if (entries.isNotEmpty()) result[pkg] = entries
        }
        return result
    }

    /** 読み込んだ内容で現在の設定を全置き換えし、フレームワークへ同期する */
    fun applyImport(context: Context, data: Map<String, Map<String, Int>>) {
        val editor = ConfigStore.localPrefs(context).edit()
        editor.clear()
        for ((pkg, entries) in data) {
            for ((feature, value) in entries) {
                editor.putInt(ConfigStore.keyFor(feature, pkg), value)
            }
        }
        editor.apply()
        // 消えたアプリの旧設定が Remote 側に残らないよう、クリアしてから同期。
        // 未接続で同期できなかった場合に備え、次回接続時も置き換え同期に
        // なるよう印を付ける (同期成功時に RemoteConfig が印を消す)
        RemoteConfig.markReplacePending(context)
        RemoteConfig.replaceFromLocal(context)
    }
}
