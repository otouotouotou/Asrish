package com.overnight.asrish

import android.content.Context
import io.github.libxposed.service.XposedService

/**
 * 設定アプリ専用: LSPosed フレームワークとの接続を保持し、
 * ローカル保存 (ConfigStore) の内容を Remote Preferences へ同期する。
 *
 * フック側プロセスからは決して参照しないこと
 * (io.github.libxposed.service のクラスは設定アプリ側でのみ使う)。
 * 接続は AsrishApp が XposedServiceHelper 経由で受け取る。
 */
object RemoteConfig {

    @Volatile
    private var service: XposedService? = null

    // 読み込み (全置き換え) がフレームワーク未接続の間に起きた場合に、
    // 次回接続時の同期を「クリアしてからコピー」に切り替えるための印。
    // 設定本体とは別の小さな保存領域に置く (書き出し対象にも混ざらない)
    private const val TRANSFER_PREFS = "asr_transfer"
    private const val KEY_REPLACE_PENDING = "replace_pending"

    fun markReplacePending(context: Context) {
        context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REPLACE_PENDING, true).apply()
    }

    fun isReplacePending(context: Context): Boolean =
        context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REPLACE_PENDING, false)

    private fun clearReplacePending(context: Context) {
        context.getSharedPreferences(TRANSFER_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REPLACE_PENDING, false).apply()
    }

    fun bind(svc: XposedService) {
        service = svc
    }

    fun unbind(svc: XposedService) {
        if (service == svc) service = null
    }

    fun isConnected(): Boolean = service != null

    /**
     * ローカルの全設定を Remote Preferences へコピーする。
     * 未接続なら何もしない (次回接続時に AsrishApp が再実行する)。
     */
    fun syncFromLocal(context: Context) {
        copyToRemote(context, clearFirst = false)
    }

    /**
     * Remote Preferences を一度クリアしてからローカルの全設定を
     * コピーする。設定の読み込み (全置き換え) 専用: ローカルから
     * 消えたアプリの設定が Remote 側に残留するのを防ぐ。
     */
    fun replaceFromLocal(context: Context) {
        copyToRemote(context, clearFirst = true)
    }

    private fun copyToRemote(context: Context, clearFirst: Boolean) {
        val svc = service ?: return
        try {
            val all = ConfigStore.localPrefs(context).all
            val editor = svc.getRemotePreferences(ConfigStore.GROUP).edit()
            if (clearFirst) editor.clear()
            if (all.isEmpty()) {
                editor.apply()
                if (clearFirst) clearReplacePending(context)
                return
            }
            for ((key, value) in all) {
                if (value is Int) editor.putInt(key, value)
            }
            editor.apply()
            if (clearFirst) clearReplacePending(context)
        } catch (t: Throwable) {
            // フレームワーク未有効化など。ローカルには保存済みなので
            // 次回接続時の同期で回復する
        }
    }
}
