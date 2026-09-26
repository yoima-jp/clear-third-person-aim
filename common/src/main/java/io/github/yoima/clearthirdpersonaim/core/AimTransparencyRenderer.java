package io.github.yoima.clearthirdpersonaim.core;

/**
 * 描画層のインターフェース。MC バージョン・ローダーごとの実装が、ローカルプレイヤーをどう透明化して描くかを担う。
 * コアロジック側は {@link FrameParams} を渡すだけで、フレームバッファやシェーダーの扱いには関与しない。
 */
public interface AimTransparencyRenderer {
    /** 実際に使われている描画方式。設定画面とデバッグログに表示する。 */
    enum Method {
        /** 別フレームバッファに不透明で描き、マスク付きで合成する (4.1 の方式)。 */
        OFFSCREEN_COMPOSITE,
        /** パーツごとに半透明で直接描く (シェーダーパック使用中などのフォールバック)。 */
        FALLBACK_TRANSLUCENT
    }

    /** 次に描くフレームで使う方式。環境の変化 (シェーダーパックの ON/OFF) に追従するため毎フレーム問い合わせる。 */
    Method currentMethod();

    /** 方式を選んだ理由 (ログと設定画面向けの短い英語。例 "shader pack active")。 */
    String methodReason();
}
