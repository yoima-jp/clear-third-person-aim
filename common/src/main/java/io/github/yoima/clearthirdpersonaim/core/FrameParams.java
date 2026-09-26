package io.github.yoima.clearthirdpersonaim.core;

/**
 * 1 フレーム分の透明化パラメータ。描画層はこれだけを見て描けばよい。
 *
 * @param mode             透明化モード
 * @param progress         フェード進行度 (イージング済み, 0..1)
 * @param remainingOpacity 残す不透明度
 * @param radiusX          楕円の横半径 (画面高さ比, 距離補正済み)
 * @param radiusY          楕円の縦半径 (画面高さ比, 距離補正済み)
 * @param feather          楕円境界のぼかし幅
 * @param waistHeight      UPPER_BODY の腰の高さ (足元から, ブロック)
 * @param hideHeldItems    手持ちアイテムも透明化するか
 */
public record FrameParams(TransparencyMode mode, float progress, float remainingOpacity, float radiusX, float radiusY,
                          float feather, float waistHeight, boolean hideHeldItems) {
    /** 透明化の最小不透明度 (フェード込み)。フォールバック描画では全体にこの値を掛ける。 */
    public float minOpacity() {
        return MaskMath.opacity(remainingOpacity, progress, 1f);
    }
}
