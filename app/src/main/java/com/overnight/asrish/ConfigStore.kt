package com.overnight.asrish

import android.content.Context
import android.content.SharedPreferences

/**
 * アプリ別設定の保存 (設定アプリ側) と、フックとの受け渡し定義。
 *
 * 方式の変遷:
 * - v0.1〜v0.1.2: Settings.System → targetSdk 31 以降の制限で書き込み不可
 * - v0.1.3〜v0.2.0: モジュール内蔵 SharedPreferences + 自前 ContentProvider
 *   → HyperOS 3 では、対象アプリ (通常アプリ) のプロセスからモジュールの
 *     パッケージが見えず (AppsFilter)、モジュールプロセス未起動時に
 *     プロバイダを解決できず設定が読めないことが実機ログで判明
 *     (システムアプリが対象のモジュールでは起きない)
 * - v0.3.0〜: Remote Preferences (LSPosed フレームワーク経由)
 *   設定アプリはローカル (この object) に書いた内容を RemoteConfig 経由で
 *   フレームワークへ同期し、フックは XposedModule.getRemotePreferences
 *   で読む。可視性にもプロセス起動状態にも依存しない。
 *   ローカル保存はそのまま残すので、旧バージョンの設定は初回同期で
 *   そのまま引き継がれる。
 *
 * 注意: この object は libxposed のクラスを継承しないこと。
 * 設定アプリ (通常プロセス) からもフックからも使う共有コードのため、
 * MainHook (XposedModule 継承) の中に置いてはいけない
 * (v0.1.1 の NoClassDefFoundError の教訓)。
 * RemoteConfig (service のクラスを含む) への参照は putInt の中だけにし、
 * フック側プロセスでは決して呼ばないこと。
 */
object ConfigStore {
    const val FEATURE_ORIENTATION = "orientation"
    const val FEATURE_FULLSCREEN = "fullscreen"
    const val FEATURE_SCREEN_ON = "screenon"
    const val FEATURE_SCREENSHOT = "screenshot"
    const val FEATURE_FONTSIZE = "fontsize"
    const val FEATURE_DPI = "dpi"
    const val FEATURE_MUTE = "mute"

    /** Remote Preferences のグループ名 (フック側の読み取りも同じ名前) */
    const val GROUP = "asrish_config"

    private const val PREFS_NAME = "asr_config"

    fun keyFor(feature: String, packageName: String) = "$feature|$packageName"

    fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInt(context: Context, feature: String, packageName: String): Int =
        localPrefs(context).getInt(keyFor(feature, packageName), 0)

    fun putInt(context: Context, feature: String, packageName: String, value: Int) {
        localPrefs(context).edit()
            .putInt(keyFor(feature, packageName), value).apply()
        // フレームワークへ即時同期 (未接続なら次回接続時にまとめて同期される)
        RemoteConfig.syncFromLocal(context)
    }

    /** いずれかの設定がデフォルト以外になっているか */
    fun isConfigured(context: Context, packageName: String): Boolean =
        getInt(context, FEATURE_ORIENTATION, packageName) != 0 ||
            getInt(context, FEATURE_FULLSCREEN, packageName) != 0 ||
            getInt(context, FEATURE_SCREEN_ON, packageName) != 0 ||
            getInt(context, FEATURE_SCREENSHOT, packageName) != 0 ||
            getInt(context, FEATURE_FONTSIZE, packageName) != 0 ||
            getInt(context, FEATURE_DPI, packageName) != 0 ||
            getInt(context, FEATURE_MUTE, packageName) != 0
}
