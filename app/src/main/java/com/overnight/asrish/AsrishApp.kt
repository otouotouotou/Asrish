package com.overnight.asrish

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 設定アプリの Application。
 * LSPosed フレームワークのサービス接続を受け取り、RemoteConfig に渡す。
 * サービスは登録前にも届くことがあるが、XposedServiceHelper が
 * 再生してくれるため、onCreate で 1 回登録すればよい。
 */
class AsrishApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        RemoteConfig.bind(service)
        // 接続できたら、ローカルに溜まっている設定をまとめて同期する
        RemoteConfig.syncFromLocal(this)
    }

    override fun onServiceDied(service: XposedService) {
        RemoteConfig.unbind(service)
    }
}
