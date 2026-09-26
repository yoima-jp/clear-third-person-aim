package io.github.yoima.clearthirdpersonaim.render.modern;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.MaskMath;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Matrix4f;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
//?} else {
/*import com.mojang.blaze3d.platform.DepthTestFunction;
*///?}

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 1.21.9〜26.1.x 向けのオフスクリーン合成。この世代の FeatureRenderDispatcher は 1 つのストレージに固定され、
 * 登録内容を BufferSource へ即座に流し込む。そのためキャプチャ専用の FeatureRenderDispatcher・ストレージ・
 * BufferSource を持ち、描画先の上書き (RenderSystem.outputColorTextureOverride) で専用テクスチャへ描く。
 * 輪郭線 (発光) と破壊中ブロックの表示は、メインのバッファへ流してバニラの処理に任せる。
 *
 * <p>深度はこの世代では通常の向き (手前ほど小さく、クリア値は 1)。合成の 2 パスの考え方は {@link PassCompositor} と同じ。
 */
public final class BufferSourceCompositor {
    private static final int PARAMS_SIZE = 16 * 4 + 64;

    static final RenderPipeline COMPOSITE_OPAQUE = pipeline("composite_opaque", true);
    static final RenderPipeline COMPOSITE_TRANSLUCENT = pipeline("composite_translucent", false);

    private static RenderPipeline pipeline(String name, boolean opaque) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ClearThirdPersonAim.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(ClearThirdPersonAim.id("core/composite_gl330"))
                .withSampler("PlayerColor")
                .withSampler("PlayerDepth")
                .withUniform("ClearAimParams", UniformType.UNIFORM_BUFFER)
                //? if >=26.1 {
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, opaque))
                //?} else {
                /*.withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .withDepthWrite(opaque)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                *///?}
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
        if (opaque) builder.withShaderDefine("CTPA_OPAQUE_PASS");
        return builder.build();
    }

    private final SubmitNodeStorage storage = new SubmitNodeStorage();
    private final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    private FeatureRenderDispatcher dispatcher;
    private GpuTexture colorTexture;
    private GpuTextureView colorView;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private MappableRingBuffer paramsBuffer;
    private final Matrix4f invViewProj = new Matrix4f();
    private GpuSampler nearestSampler;
    private boolean pipelinesChecked;

    public SubmitNodeStorage storage() {
        return storage;
    }

    /** キャプチャ専用のディスパッチャか (メインのディスパッチャへのフックで自分自身を除外するため)。 */
    public boolean isOwnDispatcher(Object candidate) {
        return candidate == dispatcher;
    }

    private FeatureRenderDispatcher dispatcher() {
        if (dispatcher == null) {
            Minecraft mc = Minecraft.getInstance();
            var renderBuffers = mc.renderBuffers();
            //? if >=26.1 {
            dispatcher = new FeatureRenderDispatcher(storage, mc.getModelManager(), bufferSource, mc.getAtlasManager(),
                    renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource(), mc.font, mc.gameRenderer.getGameRenderState());
            //?} else {
            /*dispatcher = new FeatureRenderDispatcher(storage, mc.getBlockRenderer(), bufferSource, mc.getAtlasManager(),
                    renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource(), mc.font);
            *///?}
        }
        return dispatcher;
    }

    /**
     * 登録済みのプレイヤーを専用テクスチャへ描く。メインの不透明物描画の直前に呼ぶ
     * (霧・ライティングはメインパスで設定済み)。描画先の上書きは呼び出し前の値に戻す。
     */
    public void renderCapture(FrameParams params) {
        Minecraft mc = Minecraft.getInstance();
        var mainTarget = mc.getMainRenderTarget();
        ensureTextures(mainTarget.width, mainTarget.height);
        GpuDevice device = RenderSystem.getDevice();
        device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0);

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = colorView;
        RenderSystem.outputDepthTextureOverride = depthView;
        try {
            dispatcher().renderAllFeatures();
            bufferSource.endBatch();
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }

        writeParams(params, device, mainTarget.width, mainTarget.height);
        if (!pipelinesChecked) {
            if (!device.precompilePipeline(COMPOSITE_OPAQUE).isValid() || !device.precompilePipeline(COMPOSITE_TRANSLUCENT).isValid()) {
                throw new IllegalStateException("composite pipeline failed to compile");
            }
            pipelinesChecked = true;
        }
        nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private void writeParams(FrameParams params, GpuDevice device, int width, int height) {
        if (paramsBuffer == null) {
            paramsBuffer = new MappableRingBuffer(() -> "Clear Third Person Aim params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, PARAMS_SIZE);
        } else {
            // 前フレームで使ったバッファにフェンスを張って次へ進む。GPU がまだ読んでいるバッファを上書きしないため。
            paramsBuffer.rotate();
        }
        invViewProj.set(RenderHooks.levelProjection()).mul(RenderSystem.getModelViewStack()).invert();
        var feet = RenderHooks.feetRelativeToCamera();
        //? if >=26.1 {
        boolean zeroToOne = device.isZZeroToOne();
        //?} else {
        /*boolean zeroToOne = false;
        *///?}
        try (GpuBuffer.MappedView view = device.createCommandEncoder().mapBuffer(paramsBuffer.currentBuffer(), false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(params.mode().ordinal(), params.progress(), params.remainingOpacity(), params.feather())
                    .putVec4(params.radiusX(), params.radiusY(), params.waistHeight(), MaskMath.WAIST_FEATHER)
                    // z: 深度が 0..1 か、w: プレイヤーが描かれていない画素の深度 (通常の向きのクリア値 1)
                    .putVec4(width, height, zeroToOne ? 1f : 0f, 1f)
                    .putVec4(feet.x, feet.y, feet.z, 0f)
                    .putMat4f(invViewProj);
        }
    }

    /** 不透明物を描き終えた直後に呼ぶ。メイン画面に対して専用のレンダーパスを開く。 */
    public void composite() {
        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Clear Third Person Aim composite", mainTarget.getColorTextureView(), OptionalInt.empty(),
                mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            for (RenderPipeline pipeline : new RenderPipeline[]{COMPOSITE_OPAQUE, COMPOSITE_TRANSLUCENT}) {
                pass.setPipeline(pipeline);
                pass.bindTexture("PlayerColor", colorView, nearestSampler);
                pass.bindTexture("PlayerDepth", depthView, nearestSampler);
                pass.setUniform("ClearAimParams", paramsBuffer.currentBuffer());
                pass.draw(0, 3);
            }
        }
    }

    private void ensureTextures(int width, int height) {
        if (colorTexture != null && colorTexture.getWidth(0) == width && colorTexture.getHeight(0) == height) return;
        closeTextures();
        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        colorTexture = device.createTexture(() -> "Clear Third Person Aim color", usage, TextureFormat.RGBA8, width, height, 1, 1);
        colorView = device.createTextureView(colorTexture);
        depthTexture = device.createTexture(() -> "Clear Third Person Aim depth", usage, TextureFormat.DEPTH32, width, height, 1, 1);
        depthView = device.createTextureView(depthTexture);
        ClearThirdPersonAim.LOGGER.debug("Allocated offscreen target {}x{}", width, height);
    }

    private void closeTextures() {
        if (colorView != null) colorView.close();
        if (colorTexture != null) colorTexture.close();
        if (depthView != null) depthView.close();
        if (depthTexture != null) depthTexture.close();
        colorView = null;
        colorTexture = null;
        depthView = null;
        depthTexture = null;
    }
}
