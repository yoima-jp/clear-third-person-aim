# Clear Third Person Aim

[日本語](#日本語) | [English](#english)

## 日本語

### 概要

弓などを構える三人称視点で、エイムの邪魔になる部分だけ自分のプレイヤーモデルを透明化するクライアント専用 MOD です。サーバーへの導入は不要で、他のプレイヤーからの見え方は変わりません。

### 対応環境

- Minecraft: 1.21.1、1.21.11、26.1.x（26.1.2 でビルド）、26.2、26.3
- ローダー: Fabric（Fabric API 必須、Mod Menu は任意）または NeoForge
- 配布 jar のファイル名: `clear_third_person_aim-<loader>-<modversion>+<mc>.jar`（`<loader>` は `fabric` または `neoforge`）

### 発動条件

設定画面で条件ごとに有効・無効を切り替えられます。

- 弓を引き絞る（既定 ON）
- クロスボウを装填中（既定 ON）
- 装填済みクロスボウを持つ（既定 ON）
- トライデントを構える（既定 ON）
- 投擲物を持つ（既定 OFF。雪玉、卵、エンダーパールなど）
- 追加アイテム ID（他 MOD のアイテム）。「使用中のみ」と「所持中」を別々に登録できます

一人称視点では動作しません。前面三人称視点では既定で無効です。

### モードと切り替え

- **全身**: プレイヤーモデル全体を透明化します。
- **上半身**: 頭・胴・腕を透明化し、脚を残します。
- **クロスヘア周辺**（既定）: 画面中央の楕円内を透明化し、境界をぼかします。
- **即時**: 構えたときと解除したときにすぐ切り替えます。
- **フェード**（既定）: なめらかに切り替えます。フェードインとフェードアウトの時間は別々に設定できます。

### 設定を開く

- チャットで `/clearaim` を実行（Fabric・NeoForge 共通。Mod Menu は不要）
- Fabric: Mod Menu の設定ボタン
- NeoForge: MOD 一覧の「設定」

ワールド内で設定画面を開くと、変更をその場でプレビューできます。キー割り当て画面の **「エイム時の透明化を切り替え」** は全体の有効・無効を切り替えます（既定では未割り当て）。

### 設定ファイル

設定は `config/clear_third_person_aim.json` に保存されます。数値は読み込み時に下表の範囲へ収められます。壊れたファイルは `.broken` に退避して、既定値で作り直します。

| 設定画面の表示名 | JSON キー | 既定値 | 範囲・値 |
| --- | --- | --- | --- |
| 有効 | `enabled` | `true` | — |
| 弓を引き絞る | `triggerBowDrawing` | `true` | — |
| クロスボウを装填中 | `triggerCrossbowCharging` | `true` | — |
| 装填済みクロスボウを持つ | `triggerCrossbowLoaded` | `true` | — |
| トライデントを構える | `triggerTridentAiming` | `true` | — |
| 投擲物を持つ | `triggerThrowableHeld` | `false` | — |
| 追加アイテム (使用中のみ) | `extraItemsWhileUsing` | `[]` | アイテム ID のリスト |
| 追加アイテム (所持中) | `extraItemsWhileHolding` | `[]` | アイテム ID のリスト |
| 前面視点でも有効 | `activeInFrontView` | `false` | — |
| モード | `mode` | `AIM_ELLIPSE` | `FULL_BODY` / `UPPER_BODY` / `AIM_ELLIPSE` |
| 残す不透明度 | `remainingOpacity` | `0.2` | `0.0–1.0` |
| 楕円の横半径 | `ellipseRadiusX` | `0.16` | `0.02–1.0` |
| 楕円の縦半径 | `ellipseRadiusY` | `0.26` | `0.02–1.0` |
| 境界のぼかし | `feather` | `0.06` | `0.0–0.5` |
| カメラ距離で半径を補正 | `distanceCompensation` | `true` | — |
| 手持ちアイテムも透明化 | `hideHeldItems` | `false` | — |
| 切り替え方 | `transition` | `FADE` | `INSTANT` / `FADE` |
| フェードイン時間 | `fadeInMs` | `150` ms | `0–2000` ms |
| フェードアウト時間 | `fadeOutMs` | `250` ms | `0–2000` ms |
| 簡易半透明を強制 | `forceFallbackRenderer` | `false` | — |

「楕円の横半径」と「楕円の縦半径」は画面の高さに対する比率です。追加アイテムにはアイテム ID（例: `somemod:longbow`）を指定します。

### シェーダーパックと制限

Iris などでシェーダーパックを使用すると、自動で簡易半透明描画に切り替わります。この方式では選択したモードにかかわらずプレイヤー本体全体が一律の不透明度になり、防具・マント・手持ちアイテムは不透明のままです。パーツの重なりが透けて見える場合があります。詳細は[既知の制限](KNOWN_ISSUES.md)を参照してください。

### 開発者向け

- ビルド: `./gradlew build`
- 全バージョン・全ローダーの配布 jar を収集: `./gradlew buildAndCollectAll`（出力先: `build/libs/<modversion>/`）
- 単体テスト: `./gradlew :common:test`（PowerShell: `.\gradlew.bat :common:test --quiet`）
- Fabric 1.21.4 以降のクライアントテスト: `./gradlew :<version>-fabric:runClientGameTest`
- NeoForge、および Fabric 1.21.1 のクライアントテスト: `./gradlew :<version>-<loader>:runSelfTest`
- 互換性構成: テストタスクに `-Pcta.compat=sodium`、`-Pcta.compat=iris`、または `-Pcta.compat=iris-shader` を指定
- クライアントテストをまとめて実行: `bash testing/run-all-client-tests.sh [version]`
- テストのスクリーンショット: `build/test-screenshots/`
- Fabric 配布 jar の本番起動確認: `./gradlew :<version>-fabric:runProductionSmoke`

ライセンス: MIT
作者: yoima-jp

## English

### Overview

Clear Third Person Aim is a client-side mod that makes only the parts of your player model blocking your aim transparent when you ready a bow or similar item in third person. It does not need to be installed on the server and does not change how other players see you.

### Supported environment

- Minecraft: 1.21.1, 1.21.11, 26.1.x (built for 26.1.2), 26.2, and 26.3
- Loaders: Fabric (Fabric API required; Mod Menu optional) or NeoForge
- Jar filename: `clear_third_person_aim-<loader>-<modversion>+<mc>.jar` (`<loader>` is `fabric` or `neoforge`)

### Triggers

Each condition can be enabled or disabled in the settings screen.

- Drawing a bow (ON by default)
- Loading a crossbow (ON by default)
- Holding a loaded crossbow (ON by default)
- Aiming a trident (ON by default)
- Holding throwables (OFF by default; snowballs, eggs, ender pearls, and similar items)
- Extra item IDs from other mods, configured separately for “while using” and “while held”

The mod does not work in first person. The front-facing third-person view is disabled by default.

### Modes and transitions

- **Full body**: Makes the whole player model transparent.
- **Upper body**: Makes the head, torso, and arms transparent while leaving the legs visible.
- **Around crosshair** (default): Makes the area inside an ellipse centered on screen transparent, with a soft edge.
- **Instant**: Switches immediately when you start or stop aiming.
- **Fade** (default): Transitions smoothly. Fade-in and fade-out durations can be set separately.

### Open the settings

- Run `/clearaim` in chat (works with Fabric and NeoForge; Mod Menu is not required)
- Fabric: Use the settings button in Mod Menu
- NeoForge: Use the settings button in the mod list

Open the settings screen in a world to preview changes live. The **“Toggle Aim Transparency”** key binding enables or disables the feature (unbound by default).

### Configuration file

Settings are saved to `config/clear_third_person_aim.json`. Numeric values are clamped to the ranges below when loaded. If the file is corrupted, it is backed up as `.broken` and recreated with defaults.

| Settings label | JSON key | Default | Range or values |
| --- | --- | --- | --- |
| Enabled | `enabled` | `true` | — |
| Drawing a bow | `triggerBowDrawing` | `true` | — |
| Loading a crossbow | `triggerCrossbowCharging` | `true` | — |
| Holding a loaded crossbow | `triggerCrossbowLoaded` | `true` | — |
| Aiming a trident | `triggerTridentAiming` | `true` | — |
| Holding throwables | `triggerThrowableHeld` | `false` | — |
| Extra items (while using) | `extraItemsWhileUsing` | `[]` | List of item IDs |
| Extra items (while held) | `extraItemsWhileHolding` | `[]` | List of item IDs |
| Front view too | `activeInFrontView` | `false` | — |
| Mode | `mode` | `AIM_ELLIPSE` | `FULL_BODY` / `UPPER_BODY` / `AIM_ELLIPSE` |
| Remaining opacity | `remainingOpacity` | `0.2` | `0.0–1.0` |
| Ellipse width | `ellipseRadiusX` | `0.16` | `0.02–1.0` |
| Ellipse height | `ellipseRadiusY` | `0.26` | `0.02–1.0` |
| Edge softness | `feather` | `0.06` | `0.0–0.5` |
| Adjust for camera distance | `distanceCompensation` | `true` | — |
| Include held items | `hideHeldItems` | `false` | — |
| Switching | `transition` | `FADE` | `INSTANT` / `FADE` |
| Fade-in time | `fadeInMs` | `150` ms | `0–2000` ms |
| Fade-out time | `fadeOutMs` | `250` ms | `0–2000` ms |
| Force simple translucency | `forceFallbackRenderer` | `false` | — |

Ellipse width and height are ratios of screen height. Enter item IDs for extra items, for example `somemod:longbow`.

### Shader packs and limitations

When a shader pack is active in Iris or a similar mod, rendering automatically switches to simple translucency. The whole player model then uses one opacity regardless of the selected mode; armor, capes, and held items remain opaque. Overlapping model parts may show through each other. See [Known issues](KNOWN_ISSUES.md) for details.

### For developers

- Build: `./gradlew build`
- Build and collect jars for all versions and loaders: `./gradlew buildAndCollectAll` (output: `build/libs/<modversion>/`)
- Unit tests: `./gradlew :common:test` (PowerShell: `.\gradlew.bat :common:test --quiet`)
- Client tests on Fabric 1.21.4 and later: `./gradlew :<version>-fabric:runClientGameTest`
- Client tests on NeoForge and Fabric 1.21.1: `./gradlew :<version>-<loader>:runSelfTest`
- Compatibility variants: pass `-Pcta.compat=sodium`, `-Pcta.compat=iris`, or `-Pcta.compat=iris-shader` to a test task
- Run the client test matrix: `bash testing/run-all-client-tests.sh [version]`
- Test screenshots: `build/test-screenshots/`
- Smoke-test a Fabric release jar: `./gradlew :<version>-fabric:runProductionSmoke`

License: MIT
Author: yoima-jp




