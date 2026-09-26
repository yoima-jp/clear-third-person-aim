# DECISIONS

指示書の曖昧な点や、指示書と異なる判断をした点を「判断内容・理由・代替案」で記録する。

## D-001 ユーザー記入欄はすべてデフォルト値
- 判断: Maven group `io.github.yoima.clearthirdpersonaim`、作者 `yoima-jp`、ライセンス MIT、旧バージョン対象 `1.21.11`, `1.21.1`, `26.1.x`, `26.2`。
- 理由: 指示書 0 章の欄が空欄だったため。
- 26.1.x は最新パッチの 26.1.2 でビルドし、`fabric.mod.json` の互換範囲は `~26.1`、NeoForge は `[26.1, 26.2)` とする。

## D-002 ビルドツールのバージョン
- 判断: Stonecutter 0.9.8 + loom-back-compat 0.4.2 + Fabric Loom 1.18.2 + Gradle 9.8.0 + ModDevGradle 2.0.147。
- 理由: Fabric 公式ブログ (2026-09-15「Fabric for Minecraft 26.3」) の要件は「Loom 1.17 / Gradle 9.6.0 以上」。Stonecutter 公式のマルチローダーテンプレート (2026-09-24 更新) が同じ対象バージョン群 (1.21.1〜26.3) を Loom 1.18 + Gradle 9.8.0 で動かしており、Loom 1.18.2 は安定版リリースなので、要件を満たす最新安定版を採用した。
- 26.x は `net.fabricmc.fabric-loom` (non-remap)、1.21.x は `net.fabricmc.fabric-loom-remap` が必要で、loom-back-compat がバージョンに応じて切り替える。
- 1.21.x の Fabric でも Mojang 公式マッピングを使い、全バージョンのソースを同じクラス名で書く。
- 代替案: Loom 1.17.x + Gradle 9.6.0 固定。1.21.1 の remap と 26.x の non-remap を同一ビルドで扱う実績がテンプレートにあるため不採用。

## D-003 ディレクトリ構成 (指示書 5 章からの変更)
- 判断:
  - `common/` … MC 非依存の純 Java Gradle サブプロジェクト。JUnit 5 の単体テストはここで実行する。各ローダーのビルドはこのソースを取り込み、1 つの jar に同梱する。
  - `src/main/java` … Stonecutter が前処理する MC 依存コード。`client/` `render/` `mixin/` は両ローダー共通、`fabric/` と `neoforge/` パッケージはローダー固有で、ビルドスクリプトで相手側のパッケージをコンパイル対象から除外する。
- 理由: Stonecutter はツリーの `src/` を 1 つのソースとしてバージョンごとに前処理する。`fabric/` `neoforge/` をトップレベルの別ディレクトリにすると、そこに書いたバージョン分岐コメントが処理されず、1.21.1〜26.3 の差分をローダー固有コード内で扱えない。パッケージ単位で分けても「コアと描画の分離」「ローダー固有コードの隔離」という 5 章の目的は満たせる。
- 代替案: Architectury 風に `common/` `fabric/` `neoforge/` をそれぞれ Stonecutter ブランチにする。MC 依存の共通コードを 3 か所目のブランチとしてさらに共有する必要があり、構成が大きく複雑になるため不採用。
