# TEST REPORT

実施日: 2026-09-26〜27 / 環境: Windows 11, NVIDIA GeForce RTX 3060, Java 21 (1.21.x) / Java 25 (26.x)

## 1. 自動で検証できた項目

### 1.1 単体テスト (`./gradlew :common:test`)
すべて成功。対象: FadeController (即時/フェード、途中反転の連続性、partialTick 補間、フェード時間 0)、AimState (各トリガー、追加アイテム ID、一人称・前面三人称)、Config (既定値、クランプ、壊れた JSON からの復帰、保存→読込の往復)、MaskMath (中心・境界・外側、アスペクト比補正、距離補正、上半身の境界)、プレビュー時の発動条件。

### 1.2 自動クライアントテスト (全バージョン × ローダー)
実際のクライアントを起動し、同じシナリオ (`src/gametest/.../AimScenario.java`) を実行する。Fabric 1.21.4 以降は Fabric のクライアントゲームテスト API (`runClientGameTest`)、NeoForge と Fabric 1.21.1 は自作ドライバ (`runSelfTest`、DECISIONS D-009)。しきい値の根拠は DECISIONS D-008。

| バージョン | Fabric | NeoForge |
|---|---|---|
| 26.3 | PASS | PASS |
| 26.2 | PASS | PASS |
| 26.1.2 | PASS | PASS |
| 1.21.11 | PASS | PASS |
| 1.21.1 | PASS | PASS |

各構成で自動判定している内容:
- 3 モード × 即時/フェードで透明化が発動し、描画方式がオフスクリーン合成であること
- AIM_ELLIPSE: 楕円内のプレイヤーが変化し (変化率 ≥ 0.6)、楕円外 (足元) はほぼ変化しない (平均差 ≤ 6)
- UPPER_BODY: 上半身が変化し、脚はほぼ変化しない
- 不透明度 0.5 の全身モードが「不透明なプレイヤー画像と背景の 50% ブレンド」とほぼ一致する (重なった内側が透けていない。平均差 ≤ 8)
- カメラとプレイヤーの間のブロックに、合成したプレイヤーが正しく隠れる
- フォールバック描画 (強制) でも本体が半透明になる
- 使用解除後 (フェードアウト経過後) に待機姿勢の基準画像とほぼ一致する
- 一人称では MOD の有無で画面が変わらず、透明化も発動しない
- 設定画面: ワールド内で開くとプレビューが発動する、ボタンのクリックで値が変わり即座に描画へ反映される、「完了」で保存・「キャンセル」で復元、閉じるとプレビューが止まる
- `/clearaim` で設定画面が開く、切り替えキーで全体スイッチが反転し保存される
- Mod Menu / NeoForge の MOD 一覧から設定画面が開く (Fabric は `-Pcta.withModMenu` で確認)

代表値 (26.3 Fabric): 全身 変化率 0.82 / 楕円内 0.997・楕円外 平均差 0.00 / 上半身 0.998・脚 平均差 0.00 / 0.5 ブレンドとの平均差 3.6 / 遮蔽ブロック部分の平均差 0.14 / 解除後 0.09 / 一人称 0.01。
他バージョンもおおむね同じ値 (0.5 ブレンドとの平均差は 3.2〜4.8)。

### 1.3 配布用 jar の本番起動確認
Fabric の全 5 バージョンで、配布用 jar (1.21.x は intermediary へ remap 済み) を Loom の本番起動 (`runProductionSmoke`) で起動し、メニュー画面まで到達すること (起動時に全 Mixin が適用できたこと) を確認した。NeoForge は開発環境も本番と同じ Mojang マッピングで動くため、1.2 の結果で代用している。

## 2. Sodium / Iris 互換性 (`testing/run-all-client-tests.sh`)

「iris」は Iris 導入・シェーダーパック無し、「iris-shader」は Iris + 自作の最小シェーダーパック (`testing/iris/`) を有効にした構成。iris-shader ではフォールバック描画へ自動で切り替わることを確認し、モードごとの厳密な見た目は判定しない。

| バージョン | ローダー | 単体 | Sodium | Iris | Iris + シェーダーパック |
|---|---|---|---|---|---|
| 26.3 | Fabric | PASS | PASS | PASS | PASS (フォールバック) |
| 26.3 | NeoForge | PASS | PASS | — (Iris の NeoForge 版なし) | — |
| 26.2 | Fabric | PASS | PASS | PASS | PASS (フォールバック) |
| 26.2 | NeoForge | PASS | PASS | PASS | PASS (フォールバック。OpenGL 描画検証を切った条件、K-003) |
| 26.1.2 | Fabric | PASS | PASS (撮影方法を変更、K-004) | PASS (同左) | PASS (フォールバック) |
| 26.1.2 | NeoForge | PASS | PASS | PASS | PASS (フォールバック) |
| 1.21.11 | Fabric | PASS | PASS | PASS | PASS (フォールバック) |
| 1.21.11 | NeoForge | PASS | PASS | PASS | PASS (フォールバック) |
| 1.21.1 | Fabric | PASS | PASS | PASS | PASS (フォールバック) |
| 1.21.1 | NeoForge | PASS | PASS | PASS | PASS (フォールバック) |

使用した版: Sodium 0.9.3-alpha.1 (26.3) / 0.9.2 (26.1.2, 26.2) / 0.8.15-beta.1 (1.21.11 単体) / 0.8.13 (1.21.1 単体)、Iris 1.11.6 (26.3) / 1.11.4 (26.1, 26.2) / 1.10.7 (1.21.11) / 1.8.8 (1.21.1 Fabric) / 1.8.12 (1.21.1 NeoForge)。Iris 構成の Sodium は Iris の必須依存の版に合わせた (stonecutter.properties.toml の deps.sodium_iris)。
テスト環境側の調整 (本番の動作には影響しない): NeoForge の互換構成では開発環境専用の Blaze3D 検証レイヤーを切る、Fabric 1.21.11 の Iris に同梱ライブラリを開発クラスパスへ追加する。詳細は KNOWN_ISSUES K-005。
NeoForge の互換構成では、本番の既定と同じく OpenGL 描画検証 (`-Dneoforge.disableGlValidation=true`) も切る。26.2 NeoForge + Iris 1.11.4 + シェーダーパックは、この検証を有効にしたままだと Iris の頂点形式配列を検査してクラッシュする (K-003)。
Sodium を入れた Fabric 構成では、Fabric のテスト API の撮影処理 (`GameRenderer.update()` を呼ばずに描画し直す) の代わりに、直前に通常描画されたフレームを撮影する。26.1.2 + Sodium 0.9.2 はこの撮影処理で例外になるため (K-004)。
全 38 構成 (上表で「—」以外) は 2026-09-28 の最終実行ですべて PASS (`testing/results/compat-matrix-2026-09-28-final.txt`)。
調査報告: `testing/investigations/`、途中経過: `testing/results/`。

## 3. 人間の目視確認が必要な項目

自動テストは「変化したか・一致するか」しか判定できないため、次は人が見て判断する必要がある。スクリーンショットは `build/test-screenshots/<version>-<loader>[-<compat>]/` (テスト実行で生成) にある。

| 確認すること | 見るファイル |
|---|---|
| 楕円の境界のぼかしが自然か、クロスヘア周辺の透け方がエイムの邪魔を解消しているか | `2x_aim_ellipse_*_active.png`、`72_config_screen_mode_changed_preview.png` |
| 上半身モードの腰の境界 (±0.06 ブロックのぼかし) が自然か | `2x_upper_body_*_active.png` |
| フェードの途中の見た目 | `1x_*_mid_fade.png` |
| 不透明度 0.5 で内側のパーツが透けていないか | `30_full_body_opacity_050.png` |
| 手前のブロックに正しく隠れているか | `32_occluder_full_body.png` |
| 弓 (手持ちアイテム) が見えたままで違和感がないか | `2x_full_body_*_active.png` |
| シェーダーパック使用時のフォールバックの見た目 | `*-iris-shader/2x_*_active.png`、`40_fallback_full_body.png` |
| 設定画面のレイアウト (最小 GUI サイズ 427x240 で重ならないか) | `70_config_screen_triggers.png`、`71_config_screen_appearance.png` |

加えて、実際のプレイで次を確認してほしい (自動テストの対象外):
- 歩行・走行・ジャンプ中、クロスボウ・トライデント・投擲物での見た目
- 水中・水辺 (半透明ブロックとの前後関係)、マント・エリトラ・肩のオウム・防具の透け方
- 実際のシェーダーパック (Complementary 等) でのフォールバックの見た目
- 高リフレッシュレートでのフェードのなめらかさ
