# Asrish v0.6.1

アプリごとに各種設定を変更する LSPosed モジュール。
「某モジュールっぽい」機能を、
**自分で選んだ範囲で、動く機能から1つずつ新規実装**する方針の第1弾 (土台)。

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

## 今後の予定
現在の機能範囲で公開し、不具合報告を見ながら対応を検討します。
機能追加の予定は未定です (候補: 通知制御 / アプリ別言語 /
常駐 / 権限まわり)。
