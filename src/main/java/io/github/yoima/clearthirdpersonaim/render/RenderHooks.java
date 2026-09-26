package io.github.yoima.clearthirdpersonaim.render;

import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.core.AimTransparencyRenderer.Method;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Mixin から呼ばれる描画フックの集約点。状態はすべて描画スレッドだけで読み書きする。
 *
 * <p>1 フレームの流れ (オフスクリーン合成時):
 * <ol>
 *   <li>{@link #beginLevelRender} … このフレームの {@link FrameParams} と方式を決める。null ならバニラのまま。</li>
 *   <li>ローカルプレイヤーの描画登録 … 本体と付随物 (防具・マント・オウム等) を合成用の別ストレージへ振り分ける。
 *       手持ちアイテムは設定により通常ストレージへ戻す。</li>
 *   <li>メインの描画前 … 振り分けたプレイヤーを別フレームバッファへ不透明で描く。</li>
 *   <li>不透明物の描画直後 … マスク付きでメイン画面へ合成する。半透明ブロック (水など) はこの後に描かれるので、
 *       水中の脚などは水越しに正しく見える。</li>
 * </ol>
 * 透明化が不要なフレームでは 1 の判定以外に何もしない (性能要件 4.3)。
 */
public final class RenderHooks {
    private static FrameParams frame;
    private static Method method;
    /** ローカルプレイヤーを合成用ストレージへ登録している最中か。 */
    private static boolean capturing;
    /** このフレームでプレイヤーを合成用ストレージへ登録したか。 */
    private static boolean captured;
    /** オフスクリーンへの描画に成功し、合成すべき内容があるか。 */
    private static boolean captureRendered;
    /** フォールバック描画中の本体の不透明度。負なら非適用。 */
    private static float fallbackAlpha = -1f;
    /** プレイヤーの足元 (カメラ相対, ワールド軸)。UPPER_BODY の高さ判定に使う。 */
    private static final Vector3f feetRelativeToCamera = new Vector3f();
    /** 実際に描画に使われたワールドの射影行列 (画面揺れ込み)。 */
    private static final Matrix4f levelProjection = new Matrix4f();

    private static OffscreenCompositor compositor;
    /** 合成用に振り分けている間、本来の登録先 (メインのストレージ)。 */
    private static SubmitNodeCollector mainCollector;

    private RenderHooks() {
    }

    public static OffscreenCompositor compositor() {
        if (compositor == null) compositor = new OffscreenCompositor();
        return compositor;
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

    /** ローカルプレイヤー以外、または透明化不要なら false。 */
    public static boolean shouldCapture() {
        return frame != null && method == Method.OFFSCREEN_COMPOSITE;
    }

    public static boolean shouldUseFallback() {
        return frame != null && method == Method.FALLBACK_TRANSLUCENT;
    }

    public static void beginCapture(SubmitNodeCollector mainCollector, double feetX, double feetY, double feetZ) {
        RenderHooks.mainCollector = mainCollector;
        capturing = true;
        captured = true;
        feetRelativeToCamera.set((float) feetX, (float) feetY, (float) feetZ);
    }

    public static void endCapture() {
        capturing = false;
        mainCollector = null;
    }

    /**
     * 手持ちアイテムの登録先。本体を合成用に振り分けている最中で、かつ武器を透明化しない設定なら
     * 通常のストレージへ戻す。
     */
    public static SubmitNodeCollector heldItemCollector(SubmitNodeCollector collector) {
        if (capturing && mainCollector != null && !frame.hideHeldItems() && collector == compositor.storage()) {
            return mainCollector;
        }
        return collector;
    }

    public static void renderCapture(FeatureRenderDispatcher dispatcher, CameraRenderState camera, GpuBufferSlice terrainFog) {
        if (!captured) return;
        try {
            compositor().renderCapture(dispatcher, camera, terrainFog, frame);
            captureRendered = true;
        } catch (RuntimeException | LinkageError e) {
            onCompositorFailure("offscreen capture", e);
        }
    }

    public static void composite(RenderPass pass) {
        if (!captureRendered) return;
        captureRendered = false;
        try {
            compositor.composite(pass);
        } catch (RuntimeException | LinkageError e) {
            onCompositorFailure("composite", e);
        }
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

    public static boolean hasCapture() {
        return captured;
    }

    public static void onCompositorFailure(String what, Throwable error) {
        captureRendered = false;
        ClearAimClient client = ClearAimClient.get();
        if (client != null) client.rendererSelector().markCompositorFailed(what + ": " + error, error);
    }
}
