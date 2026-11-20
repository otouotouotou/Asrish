package com.overnight.asrish

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale

/**
 * Asrish — アプリ別設定モジュール。
 * App Settings 系の機能を「自分で選んだ範囲」で新規実装する。
 * Modern Xposed API (libxposed) 102 / Kotlin。
 *
 * 機能 (全部アプリ別):
 *  - 向き固定: Activity.setRequestedOrientation を横取り + onResume で適用
 *    (ForceAutoRotate と同じ原理)
 *  - 全画面: onResume / フォーカス取得時に WindowInsetsController で
 *    システムバーを隠す/出す
 *  - 画面ON維持: onResume で FLAG_KEEP_SCREEN_ON を立てる
 *  - スクリーンショット制御 (v0.3.1 で復活): FLAG_SECURE の付け外し。
 *    「許可する」は対象アプリ自身が立てる FLAG_SECURE を
 *    Window.setFlags / addFlags / setAttributes の横取りで無効化し、
 *    onResume でも剥がす。「禁止する」は onResume で立てる
 *  - ミュート (v0.6.0〜): MediaPlayer / AudioTrack / SoundPool の
 *    音量指定を 0 に差し替え、再生開始時にも音量 0 を先に設定する。
 *    ToneGenerator はコンストラクタの音量引数を 0 に差し替える。
 *    いずれも公開 API の範囲。ネイティブ再生 (AAudio / OpenSL 等) は
 *    Java 層を通らないため対象外
 *  - フォント倍率 (v0.4.0〜) / DPI (v0.5.0〜): アプリの Resources の
 *    Configuration (fontScale / densityDpi) と DisplayMetrics を
 *    書き換える。Application.attach 直後と Activity.onCreate の
 *    直前 (レイアウト展開前) に適用し、onResume でも現在値と
 *    違う場合だけ再適用する。両設定は 1 回の更新でまとめて適用する
 *  - ロケール (v0.7.0〜): 同じ Configuration 書き換えで locales を
 *    差し替え、プロセスのデフォルトロケールも合わせる。対象アプリに
 *    その言語のリソースが含まれていない場合は表示は変わらない
 *    (Play の分割 APK 配信では言語リソースが別分割になることがある)
 *  - 最小幅 (v0.8.0〜): 同じ Configuration 書き換えで
 *    smallestScreenWidthDp を差し替える。sw<N>dp 修飾子の
 *    リソース選択 (タブレット向けレイアウト等) に効くもので、
 *    実際の画面サイズや DPI が変わるわけではない
 *  - 最近使ったアプリから除外 (v0.9.0〜): onResume で公開 API の
 *    ActivityManager.AppTask.setExcludeFromRecents(true) を呼ぶ。
 *    対象アプリ自身の全タスクに設定するので、そのアプリのタスクは
 *    どれも履歴に出なくなる
 *  - ロック画面の上に表示 (v0.10.0〜): onResume で公開 API の
 *    Activity.setShowWhenLocked(true) を呼ぶ (API 27 からある
 *    公開 API なので直接呼べる)。ロック中にその Activity が
 *    前面へ出たとき、ロック画面の上に表示される。
 *    OFF (デフォルト) では何もしない
 *
 * 設定の受け渡しは Remote Preferences (LSPosed フレームワーク経由):
 * 設定アプリが XposedService 経由で書き、フックは
 * getRemotePreferences で読む (v0.3.0〜)。
 *  "orientation": 0=デフォルト, 1=縦, 2=横, 3=自動(センサー)
 *  "fullscreen":  0=デフォルト, 1=全画面, 2=イマーシブ, 3=全画面にしない
 *  "screenon":    0=OFF, 1=ON
 *  "screenshot":  0=デフォルト, 1=許可する (FLAG_SECURE を無効化), 2=禁止する
 *  "fontsize":    0=デフォルト, それ以外はフォント倍率のパーセント (85〜130)
 *  "dpi":         0=デフォルト, それ以外は密度 (dpi の絶対値)
 *  "mute":        0=デフォルト, 1=ミュート
 *  "locale":      ConfigStore.LOCALE_TAGS へのインデックス (0=デフォルト)
 *  "forceltr":    0=デフォルト, 1=レイアウト方向を LTR に固定 (v0.10.5〜)
 *  "smallestwidth": 0=デフォルト, それ以外は最小幅 (dp の絶対値)
 *  "excluderecents": 0=デフォルト, 1=最近使ったアプリから除外する
 *  "showwhenlocked": 0=デフォルト, 1=ロック画面の上に表示する
 *
 * ※ v0.1.3〜v0.2.0 はモジュール内蔵 SharedPreferences + 自前
 *   ContentProvider で受け渡していたが、HyperOS 3 では対象アプリ
 *   (通常アプリ) のプロセスからモジュールのパッケージが見えず
 *   (AppsFilter)、モジュールプロセス未起動時にプロバイダを解決できず
 *   設定が読めなかったため方式ごと変更した。
 * ※ スクリーンショット制御は v0.2.0 で追加 → v0.3.0 で一旦削除
 *   (専用モジュールが多く、HyperCeiler がシステム側で担うため)。
 *   v0.2.0 で動かなかった原因はフックではなく上記の受け渡し問題で、
 *   Remote Preferences 化で解消したため v0.3.1 で復活させた。
 */
class MainHook : XposedModule() {

    private var hooksInstalled = false
    private var targetPackage: String = ""

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == ownPackageName()) return
        if (!param.isFirstPackage || hooksInstalled) return
        hooksInstalled = true
        targetPackage = param.packageName
        installHooks(param.packageName)
    }

    /**
     * 自モジュールのパッケージ名。applicationId を変更したビルドでも
     * 追従するよう、フレームワークから実行時に取得する
     * (文字列の直書きだと改名ビルドで自己判定が外れるため)。
     * 取得に失敗した場合は null (自己フック防止は LSPosed の
     * スコープ側でも効いているので、その場合は通常通り進む)。
     */
    private fun ownPackageName(): String? = try {
        moduleApplicationInfo.packageName
    } catch (t: Throwable) {
        null
    }

    private fun installHooks(packageName: String) {
        try {
            // --- フォント倍率: プロセス起動直後にアプリの Resources へ適用 ---
            val appAttach = Application::class.java.getDeclaredMethod(
                "attach", Context::class.java
            )
            hook(appAttach)
                .setId("asrish.appAttach")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    applyDisplayConfig(chain.thisObject as Application)
                    result
                }

            // --- Force LTR の保険: Activity 生成時にレイアウト方向を固定 ---
            // 方向の本命は applyDisplayConfig の Configuration 書き換えだが、
            // アプリが独自に Context を作り直す等で方向が RTL に戻る場合に
            // 備え、Activity の生成直前 (attachBaseContext) でも固定する。
            // ON のときだけ作用し、OFF では素通しする
            val attachBase = ContextWrapper::class.java.getDeclaredMethod(
                "attachBaseContext", Context::class.java
            )
            hook(attachBase)
                .setId("asrish.attachBaseContext")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (chain.thisObject is Activity &&
                        getConfig(ConfigStore.FEATURE_FORCE_LTR) == 1
                    ) {
                        val base = chain.getArg(0) as? Context
                        if (base != null) {
                            return@intercept chain.proceed(arrayOf<Any>(ltrContext(base)))
                        }
                    }
                    chain.proceed()
                }

            // --- フォント倍率: レイアウト展開前に Activity の Resources へ適用 ---
            val onCreate = Activity::class.java.getDeclaredMethod(
                "onCreate", Bundle::class.java
            )
            hook(onCreate)
                .setId("asrish.onCreate")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    applyDisplayConfig(chain.thisObject as Activity)
                    chain.proceed()
                }

            // --- 向き固定: setRequestedOrientation の引数を差し替える ---
            val setOrientation = Activity::class.java.getDeclaredMethod(
                "setRequestedOrientation", Int::class.javaPrimitiveType!!
            )
            hook(setOrientation)
                .setId("asrish.orientation")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val mapped = mappedOrientation()
                    if (mapped != null) {
                        chain.proceed(arrayOf<Any>(mapped))
                    } else {
                        chain.proceed()
                    }
                }

            // --- onResume: 向き・全画面・画面ON維持を適用する ---
            val onResume = Activity::class.java.getDeclaredMethod("onResume")
            hook(onResume)
                .setId("asrish.onResume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    applyWindowSettings(chain.thisObject as Activity)
                    result
                }

            // --- フォーカスが戻ったとき全画面とスクショ制御を再適用する ---
            val onFocus = Activity::class.java.getDeclaredMethod(
                "onWindowFocusChanged", Boolean::class.javaPrimitiveType!!
            )
            hook(onFocus)
                .setId("asrish.onFocus")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    if (chain.getArg(0) == true) {
                        val activity = chain.thisObject as Activity
                        applyFullscreen(activity)
                        applyScreenshot(activity)
                    }
                    result
                }

            installWindowFlagHooks()
            installAudioHooks()

            log(Log.INFO, TAG, "hooks installed in $packageName")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "failed to install hooks", t)
        }
    }

    private fun applyWindowSettings(activity: Activity) {
        try {
            mappedOrientation()?.let { activity.setRequestedOrientation(it) }
        } catch (t: Throwable) {
            // setRequestedOrientation フック側で差し替え済みの値が来るだけなので安全
        }
        applyFullscreen(activity)
        applyScreenshot(activity)
        applyDisplayConfig(activity)
        try {
            val screenOn = getConfig(ConfigStore.FEATURE_SCREEN_ON) == 1
            val window = activity.window ?: return
            if (screenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "screen-on apply failed", t)
        }
        applyExcludeFromRecents(activity)
        applyShowWhenLocked(activity)
    }

    /**
     * 「最近使ったアプリから除外」: 公開 API の
     * ActivityManager.AppTask.setExcludeFromRecents(true) を使う。
     * (Activity.setExcludeFromRecents はプラットフォーム内部には
     * あるが公開 SDK には含まれず、直接呼ぶとコンパイルできない
     * ことを v0.9.0 初版のビルドエラーで確認済み)
     * 対象プロセス自身のタスク一覧を取り、全タスクに除外を設定する。
     * 設定したタスクは履歴 (Recents) に表示されなくなる。
     */
    private fun applyExcludeFromRecents(activity: Activity) {
        try {
            if (getConfig(ConfigStore.FEATURE_EXCLUDE_RECENTS) != 1) return
            if (activity.isFinishing) return
            val manager = activity.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return
            manager.appTasks.forEach { it.setExcludeFromRecents(true) }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "exclude-from-recents apply failed", t)
        }
    }

    /**
     * 「ロック画面の上に表示」: 公開 API の
     * Activity.setShowWhenLocked(true) を使う (API 27〜)。
     * ロック中にこの Activity が前面へ出たとき (通知や履歴から
     * 起動されたときなど)、ロック画面の上に表示される。
     * すでに前面にある状態でロックしただけでは表示は変わらない
     * ことがある (プラットフォーム側の仕様)。
     */
    private fun applyShowWhenLocked(activity: Activity) {
        try {
            if (getConfig(ConfigStore.FEATURE_SHOW_WHEN_LOCKED) != 1) return
            activity.setShowWhenLocked(true)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "show-when-locked apply failed", t)
        }
    }

    private fun applyFullscreen(activity: Activity) {
        // WindowInsetsController は API 30 (Android 11) から。
        // 未満では全画面は対象外なので、設定が残っていても何もしない
        // (設定画面側も同じ判定でグレーアウト済み)。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val mode = getConfig(ConfigStore.FEATURE_FULLSCREEN)
            if (mode == 0) return
            val controller = activity.window?.decorView?.windowInsetsController ?: return
            when (mode) {
                1 -> { // 全画面 (ステータスバーを隠す)
                    controller.hide(WindowInsets.Type.statusBars())
                }
                2 -> { // イマーシブ (両方のバーを隠し、スワイプで一時表示)
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsets.Type.systemBars())
                }
                3 -> { // 全画面にしない (バーを出す)
                    controller.show(WindowInsets.Type.systemBars())
                }
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "fullscreen apply failed", t)
        }
    }

    /**
     * スクリーンショット「許可する」用: 対象アプリ自身が FLAG_SECURE を
     * 立てる経路 (Window.setFlags / addFlags / setAttributes) を横取りし、
     * そのビットだけを落とす。設定が「許可する」のとき以外は素通し。
     */
    private fun installWindowFlagHooks() {
        try {
            val secure = WindowManager.LayoutParams.FLAG_SECURE

            val setFlags = Window::class.java.getDeclaredMethod(
                "setFlags", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            hook(setFlags)
                .setId("asrish.windowSetFlags")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val flags = chain.getArg(0) as Int
                    val mask = chain.getArg(1) as Int
                    if (isScreenshotAllowed() && mask and secure != 0) {
                        log(Log.INFO, TAG, "neutralized FLAG_SECURE in setFlags")
                        chain.proceed(arrayOf<Any>(flags and secure.inv(), mask and secure.inv()))
                    } else {
                        chain.proceed()
                    }
                }

            val addFlags = Window::class.java.getDeclaredMethod(
                "addFlags", Int::class.javaPrimitiveType!!
            )
            hook(addFlags)
                .setId("asrish.windowAddFlags")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val flags = chain.getArg(0) as Int
                    if (isScreenshotAllowed() && flags and secure != 0) {
                        log(Log.INFO, TAG, "neutralized FLAG_SECURE in addFlags")
                        chain.proceed(arrayOf<Any>(flags and secure.inv()))
                    } else {
                        chain.proceed()
                    }
                }

            val setAttributes = Window::class.java.getDeclaredMethod(
                "setAttributes", WindowManager.LayoutParams::class.java
            )
            hook(setAttributes)
                .setId("asrish.windowSetAttributes")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val params = chain.getArg(0) as? WindowManager.LayoutParams
                    if (isScreenshotAllowed() && params != null && params.flags and secure != 0) {
                        params.flags = params.flags and secure.inv()
                        log(Log.INFO, TAG, "neutralized FLAG_SECURE in setAttributes")
                    }
                    chain.proceed()
                }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "window flag hooks failed", t)
        }
    }

    private fun isScreenshotAllowed(): Boolean =
        getConfig(ConfigStore.FEATURE_SCREENSHOT) == 1

    /** スクリーンショット制御を現在のウィンドウへ適用する */
    private fun applyScreenshot(activity: Activity) {
        try {
            val window = activity.window ?: return
            when (getConfig(ConfigStore.FEATURE_SCREENSHOT)) {
                1 -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                2 -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "screenshot apply failed", t)
        }
    }

    /**
     * フォント倍率・DPI・ロケール・最小幅を対象コンテキストの Resources へ適用する。
     * Configuration.fontScale / densityDpi と DisplayMetrics の
     * density / scaledDensity を書き換えると、以後に展開される
     * レイアウトの sp / dp 指定がその倍率・密度で描画される。
     * ロケールは Configuration.locales を差し替え、あわせてプロセスの
     * デフォルトロケールも設定して、日付・数値などの書式も追随させる。
     * 現在値と同じなら何もしない (無駄な再レイアウトを避ける)。
     * 各設定は競合しないよう、必ず 1 回の更新でまとめて適用する。
     */
    private fun applyDisplayConfig(context: Context) {
        try {
            val percent = getConfig(ConfigStore.FEATURE_FONTSIZE)
            val dpi = getConfig(ConfigStore.FEATURE_DPI)
            val localeIdx = getConfig(ConfigStore.FEATURE_LOCALE)
            val smallestWidth = getConfig(ConfigStore.FEATURE_SMALLEST_WIDTH)
            val forceLtr = getConfig(ConfigStore.FEATURE_FORCE_LTR) == 1
            if (percent == 0 && dpi == 0 && localeIdx == 0 && smallestWidth == 0 &&
                !forceLtr
            ) {
                return
            }
            val resources = context.resources ?: return
            val current = resources.configuration

            val fontTarget = if (percent != 0) percent / 100f else current.fontScale
            val dpiTarget = if (dpi != 0) dpi else current.densityDpi
            val fontDone = kotlin.math.abs(current.fontScale - fontTarget) < 0.001f
            val dpiDone = current.densityDpi == dpiTarget
            val swTarget =
                if (smallestWidth != 0) smallestWidth else current.smallestScreenWidthDp
            val swDone = current.smallestScreenWidthDp == swTarget

            // ロケールの目標値 (未設定なら null = 触らない)。
            // 範囲外のインデックスや空タグは未設定として扱う
            val localeTag = ConfigStore.LOCALE_TAGS.getOrNull(localeIdx).orEmpty()
            val localeTarget = if (localeTag.isNotEmpty()) {
                LocaleList(Locale.forLanguageTag(localeTag))
            } else {
                null
            }
            val localeDone = localeTarget == null || current.locales == localeTarget
            // Force LTR: 現在の方向が既に LTR なら追加の仕事はない。
            // 言語を触らない場合でも、システムロケールが RTL なら効かせる
            val dirDone = !forceLtr ||
                current.layoutDirection == View.LAYOUT_DIRECTION_LTR
            if (fontDone && dpiDone && localeDone && swDone && dirDone) return

            val config = Configuration(current)
            config.fontScale = fontTarget
            config.densityDpi = dpiTarget
            config.smallestScreenWidthDp = swTarget
            if (localeTarget != null) {
                config.setLocales(localeTarget)
                // 書式 (日付・数値・通貨など) も対象言語に追随させる
                Locale.setDefault(localeTarget[0])
            }
            if (forceLtr) {
                // setLocales の後で上書きする (setLocales はロケールから
                // 方向を再計算するため、先に書くと RTL に戻される)。
                // layoutDirection フィールドは Kotlin からは val 扱いで
                // 直接代入できない (API 37 スタブ) ため、公開メソッド
                // setLayoutDirection に LTR なロケールを渡して設定する
                config.setLayoutDirection(Locale.ENGLISH)
            }

            val metrics = DisplayMetrics()
            metrics.setTo(resources.displayMetrics)
            if (dpi != 0) {
                metrics.density = dpi / 160f
                metrics.densityDpi = dpi
            }
            // scaledDensity は density × fontScale で整合させる
            metrics.scaledDensity = metrics.density * fontTarget

            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, metrics)
            log(Log.INFO, TAG, "display config set: fontScale=$fontTarget dpi=$dpiTarget locale=$localeTag smallestWidth=$swTarget forceLtr=$forceLtr in $targetPackage")
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "display config apply failed", t)
        }
    }

    /**
     * Force LTR 用: 渡された Context と同じ設定で、レイアウト方向だけを
     * LTR に固定した Context を作る。失敗したら元の Context をそのまま返す。
     */
    private fun ltrContext(base: Context): Context {
        return try {
            val config = Configuration(base.resources.configuration)
            config.setLayoutDirection(Locale.ENGLISH)
            base.createConfigurationContext(config)
        } catch (t: Throwable) {
            base
        }
    }

    private fun isMuted(): Boolean = getConfig(ConfigStore.FEATURE_MUTE) == 1

    /**
     * ミュート用フック群。公開 API の音量指定を横取りして 0 にする。
     * 個別の失敗が他へ波及しないよう、まとめて try/catch する
     * (installHooks 本体の try/catch の内側から呼ばれる)。
     */
    private fun installAudioHooks() {
        try {
            // --- MediaPlayer: 音量指定を 0 にし、再生開始時も先に 0 を設定 ---
            val mpSetVolume = MediaPlayer::class.java.getDeclaredMethod(
                "setVolume",
                Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!
            )
            hook(mpSetVolume)
                .setId("asrish.mpSetVolume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) chain.proceed(arrayOf<Any>(0f, 0f)) else chain.proceed()
                }

            val mpStart = MediaPlayer::class.java.getDeclaredMethod("start")
            hook(mpStart)
                .setId("asrish.mpStart")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) {
                        try {
                            (chain.thisObject as MediaPlayer).setVolume(0f, 0f)
                        } catch (t: Throwable) {
                            // 状態によっては setVolume が失敗する。再生自体は止めない
                        }
                    }
                    chain.proceed()
                }

            // --- AudioTrack: 音量指定を 0 にし、再生開始時も先に 0 を設定 ---
            val atSetVolume = AudioTrack::class.java.getDeclaredMethod(
                "setVolume", Float::class.javaPrimitiveType!!
            )
            hook(atSetVolume)
                .setId("asrish.atSetVolume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) chain.proceed(arrayOf<Any>(0f)) else chain.proceed()
                }

            val atSetStereoVolume = AudioTrack::class.java.getDeclaredMethod(
                "setStereoVolume",
                Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!
            )
            hook(atSetStereoVolume)
                .setId("asrish.atSetStereoVolume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) chain.proceed(arrayOf<Any>(0f, 0f)) else chain.proceed()
                }

            val atPlay = AudioTrack::class.java.getDeclaredMethod("play")
            hook(atPlay)
                .setId("asrish.atPlay")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) {
                        try {
                            (chain.thisObject as AudioTrack).setVolume(0f)
                        } catch (t: Throwable) {
                            // 同上
                        }
                    }
                    chain.proceed()
                }

            // --- SoundPool: 再生・音量変更の引数を 0 に差し替える ---
            val spPlay = SoundPool::class.java.getDeclaredMethod(
                "play",
                Int::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!
            )
            hook(spPlay)
                .setId("asrish.spPlay")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) {
                        chain.proceed(
                            arrayOf<Any>(
                                chain.getArg(0)!!, 0f, 0f,
                                chain.getArg(3)!!, chain.getArg(4)!!, chain.getArg(5)!!
                            )
                        )
                    } else {
                        chain.proceed()
                    }
                }

            val spSetVolume = SoundPool::class.java.getDeclaredMethod(
                "setVolume",
                Int::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!
            )
            hook(spSetVolume)
                .setId("asrish.spSetVolume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) {
                        chain.proceed(arrayOf<Any>(chain.getArg(0)!!, 0f, 0f))
                    } else {
                        chain.proceed()
                    }
                }

            // --- ToneGenerator: コンストラクタの音量引数を 0 に差し替える ---
            val tgConstructor = ToneGenerator::class.java.getDeclaredConstructor(
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )
            hook(tgConstructor)
                .setId("asrish.tgConstructor")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (isMuted()) {
                        chain.proceed(arrayOf<Any>(chain.getArg(0)!!, 0))
                    } else {
                        chain.proceed()
                    }
                }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "audio hooks failed", t)
        }
    }

    /** 設定値 → ActivityInfo の向き定数。デフォルト (触らない) なら null */
    private fun mappedOrientation(): Int? = when (getConfig(ConfigStore.FEATURE_ORIENTATION)) {
        1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        3 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        else -> null
    }

    /**
     * Remote Preferences (フレームワーク経由) から対象パッケージの設定を読む。
     * フック側の SharedPreferences はフレームワークが配る読み取り専用
     * スナップショットで、設定変更は次回読み取りに反映される。
     * フレームワークが未対応・未接続なら 0 (デフォルト) を返す。
     */
    private fun getConfig(feature: String): Int {
        val prefs = try {
            getRemotePreferences(ConfigStore.GROUP)
        } catch (t: Throwable) {
            null
        } ?: return 0
        return try {
            prefs.getInt(ConfigStore.keyFor(feature, targetPackage), 0)
        } catch (t: Throwable) {
            0
        }
    }

    companion object {
        private const val TAG = "Asrish"
    }
}
