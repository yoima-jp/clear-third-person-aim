package io.github.yoima.clearthirdpersonaim.render.modern;

import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//? if >=26.2 {
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//?}
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
//?} elif >=26.2 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}

/**
 * 1.21.9 以降 (エンティティを SubmitNodeCollector へ登録してから描く世代) の描画フック。Mixin からだけ呼ばれる。
 * 世代内の違い (26.3 / 26.2 / 1.21.11〜26.1) は合成器の実装 ({@link PassCompositor} / {@link BufferSourceCompositor}) に閉じ込める。
 */
public final class ModernHooks {
    /** 合成用に振り分けている間、本来の登録先 (メインのストレージ)。 */
    private static SubmitNodeCollector mainCollector;

    //? if >=26.2 {
    private static PassCompositor compositor;

    public static PassCompositor compositor() {
        if (compositor == null) compositor = new PassCompositor();
        return compositor;
    }

    private static SubmitNodeCollector captureStorage() {
        return compositor().storage();
    }
    //?} else {
    /*private static BufferSourceCompositor compositor;

    public static BufferSourceCompositor compositor() {
        if (compositor == null) compositor = new BufferSourceCompositor();
        return compositor;
    }

    private static SubmitNodeCollector captureStorage() {
        return compositor().storage();
    }
    *///?}

    private ModernHooks() {
    }

    public static boolean isLocalPlayer(EntityRenderState state) {
        var player = Minecraft.getInstance().player;
        return player != null && state instanceof AvatarRenderState avatar && avatar.id == player.getId();
    }

    /** ローカルプレイヤーの振り分けを始め、代わりに登録すべきストレージを返す。 */
    public static SubmitNodeCollector beginCapture(SubmitNodeCollector main, double feetX, double feetY, double feetZ) {
        mainCollector = main;
        RenderHooks.beginCapture(feetX, feetY, feetZ);
        return captureStorage();
    }

    public static void endCapture() {
        RenderHooks.endCapture();
        mainCollector = null;
    }

    /**
     * 手持ちアイテムの登録先。本体を合成用に振り分けている最中で、かつ武器を透明化しない設定なら
     * 通常のストレージへ戻す。
     */
    public static SubmitNodeCollector heldItemCollector(SubmitNodeCollector collector) {
        if (RenderHooks.isCapturing() && mainCollector != null && !RenderHooks.frame().hideHeldItems() && collector == captureStorage()) {
            return mainCollector;
        }
        return collector;
    }

    //? if >=26.2 {
    public static void renderCapture(FeatureRenderDispatcher dispatcher, GpuBufferSlice terrainFog) {
        if (!RenderHooks.hasCapture()) return;
        try {
            compositor().renderCapture(dispatcher, terrainFog, RenderHooks.frame());
            RenderHooks.setCaptureRendered(true);
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("offscreen capture", e);
        }
    }
    //?} else {
    /*private static long lastCaptureFrame = -1;

    /^*
     * メインの不透明物描画の直前 (描画先の上書きが無い状態) に呼ぶ。同じメソッドは GUI やパーティクルの描画でも
     * 呼ばれるので、ワールド描画 1 回につき最初の 1 回だけ描く。
     ^/
    public static void renderCapture() {
        if (!RenderHooks.hasCapture() || compositor == null || lastCaptureFrame == RenderHooks.levelFrame()) return;
        lastCaptureFrame = RenderHooks.levelFrame();
        try {
            compositor.renderCapture(RenderHooks.frame());
            RenderHooks.setCaptureRendered(true);
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("offscreen capture", e);
        }
    }

    /^* メインのバッファを描き切ってから合成する (この世代では登録内容がバッファに溜まっているだけで、まだ描かれていないため)。 ^/
    public static void flushMainBuffersAndComposite() {
        if (!RenderHooks.takeCaptureForComposite()) return;
        try {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            compositor.composite();
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("composite", e);
        }
    }
    *///?}

    //? if >=26.3 {
    public static void composite(RenderPass pass) {
        if (!RenderHooks.takeCaptureForComposite()) return;
        try {
            compositor.composite(pass);
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("composite", e);
        }
    }
    //?} elif >=26.2 {
    /*public static void composite() {
        if (!RenderHooks.takeCaptureForComposite()) return;
        try {
            compositor.composite();
        } catch (RuntimeException | LinkageError e) {
            RenderHooks.onCompositorFailure("composite", e);
        }
    }
    *///?}
}
