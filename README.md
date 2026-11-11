# Asrish v0.6.1

アプリごとに画面設定を変更する LSPosed モジュール。
App Settings Reborn の後継ではなく、その「っぽい」機能を、
**自分で選んだ範囲で、動く機能から1つずつ新規実装**する方針の第1弾 (土台)。

## 技術構成 (実績のある型)
- Modern Xposed API (libxposed) 102 / Kotlin
- 設定の受け渡しは Remote Preferences (LSPosed フレームワーク経由)。
  設定アプリが XposedService で書き、フックは getRemotePreferences で読む
- 旧フォーク版のソースは参考資料として別途保持 (どの内部APIを叩くかの設計図)

## ライセンス・出典
- 機能セットの原典は rovo89 の App Settings と、その
  Apache License 2.0 フォークである BlueCat300 の
  App Settings Reborn。本プロジェクトはその機能を Android 14
  以降向けに新規実装したもので、原典のコードは含まない
- 依存ライブラリ: libxposed API (Apache License 2.0) を compileOnly、
  libxposed service (Apache License 2.0) を implementation で使用

## 機能 (全部アプリ別)
| 機能 | 選択肢 |
|---|---|
| 画面の向き | デフォルト / 縦固定 / 横固定 / 自動回転 (センサー) |
| 全画面 | デフォルト / 全画面にする / イマーシブ (バーを隠す) / 全画面にしない |
| フォント倍率 | デフォルト / 85%〜130% |
| DPI | デフォルト / 240〜640 |
| スクリーンショット | デフォルト / 許可する (FLAG_SECURE を外す) / 禁止する |
| 画面ON維持 | ON / OFF |
| ミュート | ON / OFF (MediaPlayer / AudioTrack / SoundPool / ToneGenerator。ネイティブ再生は対象外) |

いずれも Android の公開 API の範囲で実装しています。OS の内部実装を
フックしていないため、OS のバージョンアップで壊れにくい設計です。

## 動作環境
- Android 8.1 以降 (minSdk 27)
- LSPosed (Modern Xposed API 102 対応のもの)
- 作者環境では HyperOS 3 (Android 16) で全機能の動作を確認済み

## 使い方
1. Release から APK をインストール
2. LSPosed でモジュールを有効化し、設定したいアプリをスコープに
   入れる (端末を再起動)
3. Asrish を開き、アプリ一覧から対象アプリを選んで設定する
4. 設定は対象アプリの次回起動 (画面の再表示) から反映される

## v0.6.1 の変更点
- GitHub 公開準備: LICENSE (Apache-2.0) を追加、README に機能一覧・
  動作環境・使い方と英語セクションを追加、モジュール説明文を
  現在の機能に合わせて更新。機能の変更はなし

## v0.6.0 の変更点
- **ミュートを追加** (アプリ別、スイッチ): 対象アプリの
  MediaPlayer / AudioTrack / SoundPool の音量指定を 0 に
  差し替え、再生開始時にも音量 0 を先に設定する。ToneGenerator は
  コンストラクタの音量引数を 0 に差し替える。いずれも公開 API の
  範囲で実装。ネイティブ再生 (AAudio / OpenSL 等、ゲームや
  ブラウザ内蔵エンジンなど) は Java 層を通らないため対象外

## v0.5.0 の変更点
- **DPI を追加** (アプリ別): デフォルト / 240〜640 (40刻み)。
  対象アプリの Resources の Configuration.densityDpi と
  DisplayMetrics の density を書き換える標準 API 方式。
  フォント倍率と 1 回の更新でまとめて適用し、scaledDensity も
  整合させる (両方設定したときに片方が上書きされるのを防ぐ)

## v0.4.1 の変更点
- アプリ別設定画面のレイアウトを変更: 設定名を左詰め、操作項目
  (スピナー / スイッチ) を右詰めにして同じ行に表示。行の間に
  区切り線を追加。スクリーンショットの選択肢は行に収まる短い
  表記 (デフォルト / 許可する / 禁止する) に変更
- システム言語に追従 (日本語 / 英語): UI 文字列をリソース化し、
  values (日本語) と values-en (英語) を用意。LSPosed の
  モジュール説明文も英語版を追加

## v0.4.0 の変更点
- **フォント倍率を追加** (アプリ別): デフォルト / 85% / 90% / 95% /
  100% / 105% / 110% / 115% / 130%。対象アプリの Resources の
  Configuration.fontScale を書き換える標準 API 方式で、
  Application.attach 直後と Activity.onCreate 直前 (レイアウト
  展開前) に適用し、onResume でも現在値と違う場合だけ再適用する

## v0.3.1 の変更点
- **スクリーンショット制御を復活** (アプリ別): デフォルト /
  許可する (FLAG_SECURE を外す) / 禁止する (FLAG_SECURE を付ける)。
  v0.2.0 で動かなかった原因はフックではなく設定の受け渡し
  (ContentProvider) が対象アプリから読めなかったことで、
  v0.3.0 の Remote Preferences 移行で解消したため再実装した。
  「許可する」は Window.setFlags / addFlags / setAttributes の
  横取りで対象アプリ自身が立てる FLAG_SECURE を無効化する

## v0.3.0 の変更点
- **設定の受け渡しを Remote Preferences に移行**: v0.2.0 までの
  自前 ContentProvider 方式は、HyperOS 3 では対象アプリ (通常アプリ)
  のプロセスからモジュールのパッケージが見えず (AppsFilter)、
  モジュールプロセス未起動時に設定が読めないことが実機ログで判明。
  フレームワーク経由にしたことで可視性・起動状態に依存しなくなった。
  旧設定はローカル保存から初回同期でそのまま引き継がれる
  - 移行に伴い compileSdk を 37.0 に引き上げ (libxposed service
    102.0.0 の AAR が minCompileSdk 37 を要求するため)。
    API 37 のプラットフォームは android-37.0 としてのみ提供される
    ため、AGP 9.1.1 + Gradle 9.3.1 に更新し、compileSdk は
    release DSL (minorApiLevel = 0) で指定する。
    ビルドには SDK Platform 37.0 (Android 17) が必要。
    実行要件は従来通り Android 8.1 以降 (minSdk 27)
- **スクリーンショット制御を削除**: FLAG_SECURE を外す/付ける機能は
  専用モジュールが多く存在するため、本モジュールの範囲から外した
- **設定画面がダークテーマに追従**: 端末のライト/ダークに合わせて
  背景・文字色が切り替わる (values / values-night のテーマ切替)。
  ダーク側の背景は AMOLED 向けの純黒 (#000000)、文字色は純白
  (#FFFFFF) ベースに調整
- **アプリ一覧に検索窓を追加**: アプリ名・パッケージ名で絞り込み

## v0.2.0 の変更点
- **スクリーンショット制御を追加** (アプリ別): デフォルト /
  許可する (アプリが立てた FLAG_SECURE を無効化して撮影可能にする) /
  禁止する (FLAG_SECURE を立てて撮影・録画をブロック)
  - 「許可する」は Window.setFlags / addFlags / setAttributes の横取り +
    onResume でのフラグ解除で実現。SurfaceView の setSecure や
    DRM 保護された動画には効かない

## v0.1.3 の変更点
- **保存できない問題を修正**: v0.1.2 でも Settings.System への書き込みが
  "You cannot keep your settings in the secure settings." で拒否された。
  原因はキーの形ではなく、targetSdk 31 以降の SettingsProvider の制限
  (通常アプリの uid からはカスタムキーを Settings.System に書けない。
  WRITE_SETTINGS を許可しても同じ) だったため、保存方式ごと変更した
- **画面上部が隠れる問題を修正**: HyperOS 3 では標準のバーの下に
  コンテンツが潜り込んでいたため、テーマを明示 (NoActionBar) し、
  ステータスバー分の余白を取るよう変更
- 「システム設定への書き込み」許可の手順が不要になった

## v0.1.2 の変更点
- 設定アプリが起動時に落ちる問題を修正: キー生成関数を MainHook (XposedModule 継承) から単独の object に分離。v0.1.1 では設定アプリが MainHook クラスを読み込み、APK に同梱されていない libxposed API (compileOnly) が見つからず落ちていた

## v0.1 の機能 (全部アプリ別)
| 機能 | 選択肢 |
|---|---|
| 画面の向き | デフォルト / 縦固定 / 横固定 / 自動回転 (センサー) |
| 全画面 | デフォルト / 全画面にする / イマーシブ (バーを隠す) / 全画面にしない |
| 画面ON維持 | ON / OFF |

設定方法:
1. ビルドしてインストール
2. LSPosed でモジュールを有効化し、対象アプリをスコープに入れる (再起動)
3. モジュールアプリを開き、アプリ一覧から対象アプリを選んで設定する

## 今後の予定
現在の機能範囲で公開し、不具合報告を見ながら対応を検討します。
機能追加の予定は未定です (候補: 通知制御 / アプリ別言語 /
常駐 / 権限まわり)。

## ビルド
Android Studio で開き Build > Build APK(s)。ビルドには SDK Platform 37.0
(Android 17) のインストールが必要 (compileSdk 37.0 のため)。
対象: Android 8.1 以降 (minSdk 27)。

---

# Asrish (English)

An LSPosed module to change display settings per app. It is **not** a
successor to App Settings Reborn: it re-implements a self-chosen subset
of that kind of features from scratch, using only public Android APIs
(no hooks into OS internals, so it is resilient to OS updates).

## Features (all per-app)
| Feature | Options |
|---|---|
| Orientation | Default / Portrait / Landscape / Auto-rotate (sensor) |
| Fullscreen | Default / Force fullscreen / Immersive (hide bars) / Prevent fullscreen |
| Font scale | Default / 85%–130% |
| DPI | Default / 240–640 |
| Screenshot | Default / Allow (removes FLAG_SECURE) / Block |
| Keep screen on | On / Off |
| Mute | On / Off (MediaPlayer / AudioTrack / SoundPool / ToneGenerator; native playback engines are out of scope) |

## Requirements
- Android 8.1 or later (minSdk 27)
- LSPosed with Modern Xposed API 102 support
- All features verified by the author on HyperOS 3 (Android 16)

## Usage
1. Install the APK from Releases
2. Enable the module in LSPosed and add the apps you want to
   configure to the scope (then reboot)
3. Open Asrish, pick an app from the list, and configure it
4. Settings take effect the next time the target app is launched
   (or its screen is shown again)

The UI follows the system language (Japanese / English).

## Tech
- Modern Xposed API (libxposed) 102 / Kotlin
- Settings are passed via Remote Preferences (through the LSPosed
  framework)

## License & attribution
- Licensed under the Apache License 2.0 (see LICENSE)
- The feature concept originates from App Settings by rovo89 and its
  Apache-2.0 fork App Settings Reborn by BlueCat300. This project is
  an original implementation for Android 14 and later and contains
  none of their code.
- Dependencies: libxposed API and libxposed service (Apache License 2.0)
