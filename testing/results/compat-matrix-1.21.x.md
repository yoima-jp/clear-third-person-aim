# Minecraft 1.21.11 / 1.21.1 互換テストマトリクス

実行日: 2026-09-27。Git Bash で `bash testing/run-all-client-tests.sh 1.21.11` を実行し、初回の summary と結果を記録してから `bash testing/run-all-client-tests.sh 1.21.1` を順に実行した。各構成の成否はスクリプトが記録した Gradle タスクの終了状態を示す（`PASS` は終了コード 0、`FAIL` は非 0）。失敗した 9 構成は同じ Gradle タスクをそれぞれ一度だけ再実行し、すべて同じ原因で再現した。

描画方式と overlap / occlusion の数値は `build/test-screenshots/<構成>/results.txt` から転記した。成功した構成はすべて `OFFSCREEN_COMPOSITE`。失敗した構成は `results.txt` が生成されず、`RESULT:` 行もなかった。`RESULT: PASS` なのに終了コードが非 0 となった構成はない。

集計: 7 PASS / 9 FAIL。失敗 9 構成はすべて再実行でも再現した。

## 構成別結果

| 構成 | 終了コード | RESULT | 描画方式 | 備考（overlap / occlusion または失敗理由） |
|---|---|---|---|---|
| 1.21.11 Fabric | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: pixels=9035, mean=4.44, changed=0.060; occlusion: pixels=7275, mean=0.13, changed=0.002 (`results.txt`: 19–20行) |
| 1.21.11 Fabric + Sodium | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 9032, 4.46, 0.062; occlusion: 7270, 0.10, 0.003 (同 19–20行) |
| 1.21.11 Fabric + Iris | 失敗 (非 0) | 行なし | 未記録 | Sodium 0.8.15-beta.1 が Iris 1.10.7 を拒否 (A) |
| 1.21.11 Fabric + Iris + shader | 失敗 (非 0) | 行なし | 未記録 | Fabric + Iris と同じ依存関係エラー (A) |
| 1.21.11 NeoForge | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 9092, 4.77, 0.065; occlusion: 7258, 0.10, 0.002 (`results.txt`: 19–20行) |
| 1.21.11 NeoForge + Sodium | 失敗 (非 0) | 行なし | 未記録 | Sodium が NeoForge の GPU texture を OpenGL texture にキャストしてクラッシュ (B) |
| 1.21.11 NeoForge + Iris | 失敗 (非 0) | 行なし | 未記録 | Iris が NeoForge の GPU device を OpenGL device にキャストしてクラッシュ (C) |
| 1.21.11 NeoForge + Iris + shader | 失敗 (非 0) | 行なし | 未記録 | NeoForge + Iris と同じ初期化エラー (C) |
| 1.21.1 Fabric | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 11022, 3.17, 0.044; occlusion: 7102, 0.26, 0.002 (`results.txt`: 19–20行) |
| 1.21.1 Fabric + Sodium | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 9169, 3.91, 0.057; occlusion: 7297, 0.43, 0.003 (同 19–20行) |
| 1.21.1 Fabric + Iris | 失敗 (非 0) | 行なし | 未記録 | Iris の `MixinLevelRenderer` 適用時に失敗 (D) |
| 1.21.1 Fabric + Iris + shader | 失敗 (非 0) | 行なし | 未記録 | Fabric + Iris と同じ Mixin エラー (D) |
| 1.21.1 NeoForge | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 9177, 3.68, 0.053; occlusion: 7285, 0.19, 0.002 (`results.txt`: 19–20行) |
| 1.21.1 NeoForge + Sodium | 成功 (0) | `PASS` | `OFFSCREEN_COMPOSITE` | overlap: 9189, 3.61, 0.052; occlusion: 7286, 0.24, 0.002 (同 19–20行) |
| 1.21.1 NeoForge + Iris | 失敗 (非 0) | 行なし | 未記録 | Iris が Sodium API の `VertexSerializer` を読み込めず停止 (E) |
| 1.21.1 NeoForge + Iris + shader | 失敗 (非 0) | 行なし | 未記録 | NeoForge + Iris と同じ Sodium API 欠落 (E) |

数値の並びは overlap / occlusion とも `pixels, mean, changed`。失敗時はテストの数値が記録されていないため、描画方式も判定できない。

## 失敗・異常の原因

**A — 1.21.11 Fabric の Iris 構成**

初回の `build/test-logs/1.21.11-fabric-iris.log:81-84,99` と `build/test-logs/1.21.11-fabric-iris-shader.log:55-58,73` に、Iris `1.10.7+mc1.21.11` が Sodium `0.8.15-beta.1+mc1.21.11` を依存条件で拒否した記録がある。Fabric Loader は Sodium を 0.8.x の互換版へ置き換えるよう示して起動を中断した。再実行でも同じ理由が出た。`versions/1.21.11-fabric/build/run/clientGameTest/logs/latest.log:3-6` にも再実行時の依存解決エラーがある。**見立て:** Iris / Sodium のバージョン組み合わせによる外部 MOD 側の問題。Clear Third Person Aim のテストや描画処理が始まる前に停止している。

**B — 1.21.11 NeoForge + Sodium**

`build/test-logs/1.21.11-neoforge-sodium.log:151-154` に `ValidationGpuTexture cannot be cast to GlTexture` があり、先頭フレームのスタックは Sodium の `ShaderChunkRenderer.begin` (91行目) を指す。ログ 345–348 行でクラッシュと `runSelfTest FAILED` が記録されている。再実行でも同じ型キャスト例外を確認した。実行ディレクトリの `versions/1.21.11-neoforge/build/run/selfTest/logs/latest.log:78-79` はクラッシュレポート作成を記録する。**見立て:** Sodium が NeoForge の GPU texture 実装を Mojang の OpenGL 実装として扱う互換性問題。例外の発生元は Sodium で、Clear Third Person Aim のテスト結果出力前に停止している。

**C — 1.21.11 NeoForge + Iris 系**

`build/test-logs/1.21.11-neoforge-iris.log:133-136` では Iris `1.10.7+mc1.21.11` の `Iris.setDebug` が `ValidationGpuDevice` を `GlDevice` にキャストして失敗する。Iris + shader 構成も `build/test-logs/1.21.11-neoforge-iris-shader.log:134-137` で同じ箇所に失敗し、それぞれ 366–368行、365–369行でクラッシュと Gradle タスク失敗が記録されている。再実行でも両方再現した。`versions/1.21.11-neoforge/build/run/selfTest/logs/latest.log:78-79` はクラッシュレポート作成を示す。**見立て:** Iris と NeoForge の GPU device 実装の互換性問題。スタックの失敗箇所は Iris の初期化コード。

**D — 1.21.1 Fabric + Iris 系**

`build/test-logs/1.21.1-fabric-iris.log:127-128,169` と `build/test-logs/1.21.1-fabric-iris-shader.log:129-130,171` では、Iris `1.8.14-beta.1+mc1.21.1` の `mixins.iris.json:MixinLevelRenderer` が `@ModifyArg` を解析中に失敗し、Fabric Loader の `MixinRemapper` で `desc is null` の NullPointerException が発生する。どちらもテスト結果を書き出す前にクラッシュした。再実行でも再現。再実行後の `run/fabric-1.21.1/logs/latest.log:79-80` にも Iris の Mixin エラーがある。**見立て:** Iris の Mixin とこの Fabric Loader / Mixin 環境の互換性問題。エラーは Iris の Mixin 適用時に発生しており、Clear Third Person Aim の処理を直接示すスタックではない。

**E — 1.21.1 NeoForge + Iris 系**

初回の `build/test-logs/1.21.1-neoforge-iris.log:153-161,170` と `build/test-logs/1.21.1-neoforge-iris-shader.log:154-162,171` では、`net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer` の `NoClassDefFoundError` / `ClassNotFoundException` で停止する。どちらも Iris `1.8.14-beta.1+mc1.21.1` と Sodium `0.8.13+mc1.21.1` の構成で、再実行でも同じ API クラス欠落が出た。`versions/1.21.1-neoforge/build/run/selfTest/logs/latest.log:32-70` にも Iris の Sodium 向け Mixin が複数の Sodium クラスを見つけられない警告がある。**見立て:** Iris と NeoForge 用 Sodium の API 組み合わせによる外部 MOD 側の問題。

5 つの原因群すべてで、初回と再実行の診断は一致した。現行ログに `RESULT: FAIL` 行はなく、失敗は Fabric Loader の起動拒否またはクライアントクラッシュとして記録されている。

## KNOWN_ISSUES.md 追記案

K-001 は 26.3 Fabric の Iris + 最小 shader pack の自動テストに基づく記述なので、1.21.x へ一般化しないよう確認範囲を明示する追記が有用。

> - 確認範囲: 「クラッシュしない」の確認は 26.3 Fabric の Iris + 最小 shader pack に限る。1.21.1 / 1.21.11 の Iris 構成は今回の互換テストで結果を確認できなかった。Fabric では Iris の依存関係または Mixin 適用、NeoForge では Iris / Sodium の API・GPU 型の互換性エラーが起き、描画結果を書き出す前に停止した。これらの構成での shader pack 使用時の挙動は未確認。

この文案は観測した互換範囲を狭めて記録するもので、Clear Third Person Aim 側に原因があると断定するものではない。
