package io.github.yoima.clearthirdpersonaim.render.legacy;

import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * 1.21.1 (レンダーステートも submit も無い世代) の描画フック。Mixin からだけ呼ばれる。
 * 流れは modern 版と同じで、描画先が SubmitNodeCollector ではなく MultiBufferSource になっているだけ。
 */
public final class LegacyHooks {
    private static LegacyCompositor compositor;
    /** 合成用に振り分けている間、本来の描画先 (メインの BufferSource)。 */
    private static MultiBufferSource mainBuffer;
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f viewRotation = new Matrix4f();

    private LegacyHooks() {
    }

    private static LegacyCompositor compositor() {
        if (compositor == null) compositor = new LegacyCompositor();
        return compositor;
    }

    public static boolean isLocalPlayer(Entity entity) {
        return entity != null && entity == Minecraft.getInstance().player;
    }

    /** ワールド描画の先頭で、この描画に使われる行列を記録する (UPPER_BODY の高さ判定用)。 */
    public static void beginLevelRender(float partialTick, Matrix4f viewRotationMatrix, Matrix4f projectionMatrix) {
        RenderHooks.beginLevelRender(partialTick);
        viewRotation.set(viewRotationMatrix);
        projection.set(projectionMatrix);
        RenderHooks.setLevelProjection(projectionMatrix);
    }

    public static MultiBufferSource beginCapture(MultiBufferSource main, double feetX, double feetY, double feetZ) {
        mainBuffer = main;
        RenderHooks.beginCapture(feetX, feetY, feetZ);
        return compositor().buffer();
    }

    /** 振り分けを終え、その場で専用ターゲットへ描き切る。 */
    public static void endCapture() {
        RenderHooks.endCapture();
        mainBuffer = null;
        try {
            compositor.flushCapture();
            RenderHooks.setCaptureRendered(true);
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("offscreen capture", e);
        }
    }

    public static MultiBufferSource heldItemBuffer(MultiBufferSource buffer) {
        if (RenderHooks.isCapturing() && mainBuffer != null && !RenderHooks.frame().hideHeldItems() && compositor != null
                && buffer == compositor.buffer()) {
            return mainBuffer;
        }
        return buffer;
    }

    public static void composite() {
        if (!RenderHooks.takeCaptureForComposite()) return;
        try {
            compositor.composite(RenderHooks.frame(), projection, viewRotation);
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("composite", e);
        }
    }
}
