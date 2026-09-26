package io.github.yoima.clearthirdpersonaim.render;

import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.core.AimTransparencyRenderer.Method;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * 描画フックの、描画 API 世代に依存しない状態。状態はすべて描画スレッドだけで読み書きする。
 * 世代ごとの処理は {@code render.modern} (1.21.9 以降の submit 方式) と {@code render.legacy} (1.21.1) にある。
 *
 * <p>1 フレームの流れ (オフスクリーン合成時):
 * <ol>
 *   <li>{@link #beginLevelRender} … このフレームの {@link FrameParams} と方式を決める。null ならバニラのまま。</li>
 *   <li>ローカルプレイヤーの描画 … 本体と付随物 (防具・マント・オウム等) を合成用の別の描画先へ振り分ける。
 *       手持ちアイテムは設定により通常の描画先へ戻す。</li>
 *   <li>振り分けたプレイヤーを別フレームバッファへ不透明で描く。</li>
 *   <li>不透明物の描画直後 … マスク付きでメイン画面へ合成する。半透明ブロック (水など) はこの後に描かれるので、
 *       水中の脚などは水越しに正しく見える。</li>
 * </ol>
 * 透明化が不要なフレームでは 1 の判定以外に何もしない (性能要件 4.3)。
 */
public final class RenderHooks {
    private static FrameParams frame;
    private static Method method;
    /** ローカルプレイヤーを合成用の描画先へ振り分けている最中か。 */
    private static boolean capturing;
    /** このフレームでプレイヤーを合成用の描画先へ振り分けたか。 */
    private static boolean captured;
    /** オフスクリーンへの描画に成功し、合成すべき内容があるか。 */
    private static boolean captureRendered;
    /** フォールバック描画中の本体の不透明度。負なら非適用。 */
    private static float fallbackAlpha = -1f;
    /** プレイヤーの足元 (カメラ相対, ワールド軸)。UPPER_BODY の高さ判定に使う。 */
    private static final Vector3f feetRelativeToCamera = new Vector3f();
    /** 実際に描画に使われたワールドの射影行列 (画面揺れ込み)。 */
    private static final Matrix4f levelProjection = new Matrix4f();

    private RenderHooks() {
    }

    public static FrameParams frame() {
        return frame;
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static Vector3f feetRelativeToCamera() {
        return feetRelativeToCamera;
    }

    public static Matrix4fc levelProjection() {
        return levelProjection;
    }

    public static void setLevelProjection(Matrix4fc projection) {
        levelProjection.set(projection);
    }

    public static void beginLevelRender(float partialTick) {
        captured = false;
        captureRendered = false;
        capturing = false;
        fallbackAlpha = -1f;
        ClearAimClient client = ClearAimClient.get();
        frame = client == null ? null : client.frameParams(Minecraft.getInstance(), partialTick);
        method = frame == null ? null : client.rendererSelector().currentMethod();
    }

    public static boolean shouldCapture() {
        return frame != null && method == Method.OFFSCREEN_COMPOSITE;
    }

    public static boolean shouldUseFallback() {
        return frame != null && method == Method.FALLBACK_TRANSLUCENT;
    }

    public static void beginCapture(double feetX, double feetY, double feetZ) {
        capturing = true;
        captured = true;
        feetRelativeToCamera.set((float) feetX, (float) feetY, (float) feetZ);
    }

    public static void endCapture() {
        capturing = false;
    }

    public static boolean hasCapture() {
        return captured;
    }

    public static void setCaptureRendered(boolean rendered) {
        captureRendered = rendered;
    }

    /** 合成すべき内容があれば true を返し、同じフレームで二重に合成しないよう印を消す。 */
    public static boolean takeCaptureForComposite() {
        boolean rendered = captureRendered;
        captureRendered = false;
        return rendered;
    }

    public static void beginFallback() {
        fallbackAlpha = frame.minOpacity();
    }

    public static void endFallback() {
        fallbackAlpha = -1f;
    }

    public static boolean isFallbackActive() {
        return fallbackAlpha >= 0f;
    }

    /** バニラの「半透明の透明化エフェクト」色 (0x26FFFFFF) の代わりに使う色。 */
    public static int fallbackColor() {
        int alpha = Math.round(Math.max(0f, Math.min(1f, fallbackAlpha)) * 255f);
        return (alpha << 24) | 0x00FFFFFF;
    }

    /** 合成で例外が起きたら以後フォールバックに切り替える (毎フレーム例外を出し続けないため)。 */
    public static void onCompositorFailure(String what, Throwable error) {
        captureRendered = false;
        ClearAimClient client = ClearAimClient.get();
        if (client != null) client.rendererSelector().markCompositorFailed(what + ": " + error, error);
    }
}
