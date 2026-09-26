package io.github.yoima.clearthirdpersonaim.core;

/**
 * 合成シェーダー ({@code assets/clear_third_person_aim/shaders/core/composite.fsh} など) と同じ式の Java 版。
 * 単体テストでシェーダーの式を検証するため、およびフォールバック描画で同じ見た目の係数を得るために使う。
 * シェーダー側の式を変えたら必ずこちらも合わせ、MaskMathTest を更新すること。
 */
public final class MaskMath {
    /** バニラの三人称カメラ距離 (ブロック)。この距離のとき距離補正の倍率が 1 になる。 */
    public static final float REFERENCE_CAMERA_DISTANCE = 4.0f;
    /** 距離補正の倍率の範囲。カメラが壁に押し付けられて極端に近づいても画面全体が透けないよう上限を設ける。 */
    public static final float MIN_DISTANCE_SCALE = 0.5f;
    public static final float MAX_DISTANCE_SCALE = 2.5f;

    /** UPPER_BODY の上半身/下半身境界 (足元からの高さ、スケール 1 のプレイヤー、ブロック単位)。脚モデルの長さ 12px = 0.75。 */
    public static final float WAIST_HEIGHT = 0.75f;
    /** しゃがみ時は胴体が前傾し腰の位置が下がる。 */
    public static final float WAIST_HEIGHT_CROUCHING = 0.62f;
    /** 腰の境界をぼかす幅 (ブロック単位、片側)。 */
    public static final float WAIST_FEATHER = 0.06f;

    private MaskMath() {
    }

    /** GLSL の smoothstep と同じ。edge0 == edge1 のときは段差関数として扱う (GLSL では未定義)。 */
    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x < edge0 ? 0f : 1f;
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    /**
     * 楕円中心 (画面中央) からの正規化距離 d。d = 1 が楕円の境界。
     * 横方向も画面の高さで正規化するので、ワイド画面でも楕円の形 (縦横比) が崩れない。
     *
     * @param x      ピクセル x (左端 0)
     * @param y      ピクセル y (上端でも下端でも可。中心対称なので結果は同じ)
     * @param width  画面幅 (px)
     * @param height 画面高さ (px)
     * @param rx     横半径 (画面高さ比)
     * @param ry     縦半径 (画面高さ比)
     */
    public static float ellipseDistance(float x, float y, float width, float height, float rx, float ry) {
        float dx = (x - width * 0.5f) / height;
        float dy = (y - height * 0.5f) / height;
        float nx = dx / rx;
        float ny = dy / ry;
        return (float) Math.sqrt(nx * nx + ny * ny);
    }

    /** 楕円マスクの「透明化の強さ」。楕円内 1、楕円外 0、境界付近で feather 幅でなめらかに変化する。 */
    public static float ellipseWeight(float d, float feather) {
        return 1f - smoothstep(1f - feather, 1f + feather, d);
    }

    /**
     * 最終的な不透明度。{@code weight} (マスクの透明化の強さ 0..1) と {@code progress} (フェード進行度 0..1) を掛け、
     * 1 (通常表示) から remainingOpacity の間を補間する。
     */
    public static float opacity(float remainingOpacity, float progress, float weight) {
        return 1f - (1f - remainingOpacity) * progress * weight;
    }

    /** AIM_ELLIPSE モードでのピクセルの不透明度。 */
    public static float ellipseOpacity(float x, float y, float width, float height, float rx, float ry, float feather,
                                       float remainingOpacity, float progress) {
        float d = ellipseDistance(x, y, width, height, rx, ry);
        return opacity(remainingOpacity, progress, ellipseWeight(d, feather));
    }

    /**
     * カメラ距離による半径の倍率。画面上のプレイヤーの大きさはカメラ距離にほぼ反比例するので、
     * 基準距離 / 実距離 を倍率にする。
     */
    public static float distanceScale(float cameraDistance) {
        if (!(cameraDistance > 0f)) return MAX_DISTANCE_SCALE;
        float s = REFERENCE_CAMERA_DISTANCE / cameraDistance;
        return Math.max(MIN_DISTANCE_SCALE, Math.min(MAX_DISTANCE_SCALE, s));
    }

    /**
     * UPPER_BODY の透明化の強さ。足元からの高さ {@code heightAboveFeet} が腰より上なら 1、下なら 0。
     *
     * @param heightAboveFeet ピクセルのワールド座標の、プレイヤー足元からの高さ (ブロック)
     * @param waistHeight     腰の高さ (スケール込み、ブロック)
     */
    public static float upperBodyWeight(float heightAboveFeet, float waistHeight, float featherBlocks) {
        return smoothstep(waistHeight - featherBlocks, waistHeight + featherBlocks, heightAboveFeet);
    }

    public static float waistHeight(float playerScale, boolean crouching) {
        return (crouching ? WAIST_HEIGHT_CROUCHING : WAIST_HEIGHT) * playerScale;
    }
}
