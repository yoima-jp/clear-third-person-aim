# PROGRESS

再開時は最初にこのファイルと `DECISIONS.md` / `KNOWN_ISSUES.md` を読むこと。
チェックは「完了条件を満たした」ことを意味する。満たせずに記録して先へ進んだ項目は `KNOWN_ISSUES.md` の参照を併記する。

## Phase 0: 環境構築
- [x] Stonecutter マルチバージョン・マルチローダー構成 (common / Stonecutter ツリー)
- [x] `./gradlew :26.3-fabric:build` が通る
- [x] PROGRESS / DECISIONS / KNOWN_ISSUES 作成
- [x] 開発クライアントで起動し MOD 一覧に表示される (ゲームテストのログと Mod Menu で確認)

## Phase 1: コアロジック + 単体テスト
- [x] AimState + テスト
- [x] FadeController + テスト
- [x] Config + テスト
- [x] MaskMath + テスト
- [x] AimTransparencyRenderer インターフェース

## Phase 2〜8 は 26.3 Fabric で実装・自動テスト済み (build/test-screenshots/26.3-fabric*)

## Phase 2: 全身モード (即時)
- [x] 発動判定をゲームに接続
- [x] FULL_BODY + INSTANT が動く

## Phase 3: フェード
- [x] フェードイン/アウト、途中反転

## Phase 4: オフスクリーン合成
- [x] 別フレームバッファへの描画と合成
- [x] 重なり問題の解消・手前ブロックでの遮蔽

## Phase 5: 上半身モード
- [x] 上半身だけ透明化、脚は通常表示

## Phase 6: 楕円マスクモード
- [x] クロスヘア周辺だけ透明化、なめらかな境界、距離補正

## Phase 7: 設定画面・キーバインド・翻訳・Mod Menu
- [x] 設定画面 (即時プレビュー)
- [x] キーバインド
- [x] ja_jp / en_us
- [x] Mod Menu 連携
- [x] 再起動後も保持

## Phase 8: 互換性
- [x] Sodium
- [x] Iris (シェーダーパック使用時フォールバック)
- [x] TEST_REPORT.md に記録

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
