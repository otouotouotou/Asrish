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
        val svc = service ?: return
        try {
            val all = ConfigStore.localPrefs(context).all
            if (all.isEmpty()) return
            val editor = svc.getRemotePreferences(ConfigStore.GROUP).edit()
            for ((key, value) in all) {
                if (value is Int) editor.putInt(key, value)
            }
            editor.apply()
        } catch (t: Throwable) {
            // フレームワーク未有効化など。ローカルには保存済みなので
            // 次回接続時の同期で回復する
        }
    }
}
