# PROGRESS

再開時は最初にこのファイルと `DECISIONS.md` / `KNOWN_ISSUES.md` を読むこと。
チェックは「完了条件を満たした」ことを意味する。満たせずに記録して先へ進んだ項目は `KNOWN_ISSUES.md` の参照を併記する。

## Phase 0: 環境構築
- [ ] Stonecutter マルチバージョン・マルチローダー構成 (common / Stonecutter ツリー)
- [ ] `./gradlew :26.3-fabric:build` が通る
- [ ] PROGRESS / DECISIONS / KNOWN_ISSUES 作成
- [ ] 開発クライアントで起動し MOD 一覧に表示される

## Phase 1: コアロジック + 単体テスト
- [ ] AimState + テスト
- [ ] FadeController + テスト
- [ ] Config + テスト
- [ ] MaskMath + テスト
- [ ] AimTransparencyRenderer インターフェース

## Phase 2: 全身モード (即時)
- [ ] 発動判定をゲームに接続
- [ ] FULL_BODY + INSTANT が動く

## Phase 3: フェード
- [ ] フェードイン/アウト、途中反転

## Phase 4: オフスクリーン合成
- [ ] 別フレームバッファへの描画と合成
- [ ] 重なり問題の解消・手前ブロックでの遮蔽

## Phase 5: 上半身モード
- [ ] 上半身だけ透明化、脚は通常表示

## Phase 6: 楕円マスクモード
- [ ] クロスヘア周辺だけ透明化、なめらかな境界、距離補正

## Phase 7: 設定画面・キーバインド・翻訳・Mod Menu
- [ ] 設定画面 (即時プレビュー)
- [ ] キーバインド
- [ ] ja_jp / en_us
- [ ] Mod Menu 連携
- [ ] 再起動後も保持

## Phase 8: 互換性
- [ ] Sodium
- [ ] Iris (シェーダーパック使用時フォールバック)
- [ ] TEST_REPORT.md に記録

## Phase 9: NeoForge 26.3
- [ ] ビルド
- [ ] 起動・機能

## Phase 10: 旧バージョン対応
- [ ] 26.2 (Fabric / NeoForge)
- [ ] 26.1.2 (Fabric / NeoForge)
- [ ] 1.21.11 (Fabric / NeoForge)
- [ ] 1.21.1 (Fabric / NeoForge)
- [ ] 全バージョンで自動テスト

## Phase 11: 仕上げ
- [ ] README (日英)
- [ ] CHANGELOG
- [ ] TEST_REPORT
- [ ] 最終チェックリスト
