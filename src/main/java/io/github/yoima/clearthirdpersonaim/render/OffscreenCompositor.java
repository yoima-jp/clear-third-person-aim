package io.github.yoima.clearthirdpersonaim.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.MaskMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 26.3 (Renderpearl) 向けのオフスクリーン合成。
 *
 * <p>ローカルプレイヤーを専用の {@link SubmitNodeStorage} に登録させ、メインの描画前に
 * 専用のカラー + 深度テクスチャへ不透明で描く。合成は不透明物の描画パスの最後に、
 * 全画面三角形 1 枚をマスク付きで描いて行う。
 *
 * <p>テクスチャはウィンドウサイズが変わったときだけ作り直す (毎フレーム生成しない)。
 * 合成は 2 回の描画に分ける:
 * <ul>
 *   <li>不透明パス: 不透明度 1 のピクセル (マスク外) だけを深度書き込みありで描く。後から描かれる半透明ブロックと正しく前後判定されるように。</li>
 *   <li>半透明パス: 透けているピクセルだけを深度書き込みなしで描く。後ろにある水などが透けた体に隠されないように。</li>
 * </ul>
 */
public final class OffscreenCompositor {
    // std140: vec4 x4 + mat4 = 128 bytes
    private static final int PARAMS_SIZE = 16 * 4 + 64;

    private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withUniform("PlayerColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("PlayerDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final BindGroupLayout PARAMS = BindGroupLayout.builder()
            .withUniform("ClearAimParams", UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline COMPOSITE_OPAQUE = pipeline("composite_opaque", true);
    static final RenderPipeline COMPOSITE_TRANSLUCENT = pipeline("composite_translucent", false);

    private static RenderPipeline pipeline(String name, boolean opaque) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ClearThirdPersonAim.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(ClearThirdPersonAim.id("core/composite"))
                .withBindGroupLayout(SAMPLERS)
                .withBindGroupLayout(PARAMS)
                // オフスクリーンの色は、半透明のスキン外層が透明な背景へブレンドされた「乗算済みアルファ」になっている。
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                // 26.3 は reversed-Z (手前ほど深度が大きい)。バニラのエンティティと同じ比較にする。
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, opaque))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (opaque) builder.withShaderDefine("CTPA_OPAQUE_PASS");
        return builder.build();
    }

    private final SubmitNodeStorage storage = new SubmitNodeStorage();
    private GpuTexture colorTexture;
    private GpuTextureView colorView;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private MappableRingBuffer paramsBuffer;
    private final Matrix4f invViewProj = new Matrix4f();
    private CompiledRenderPipeline opaquePipeline;
    private CompiledRenderPipeline translucentPipeline;
    private GpuSampler nearestSampler;

    public SubmitNodeStorage storage() {
        return storage;
    }

    /**
     * 登録済みのプレイヤーを別テクスチャへ描く。メインの {@code prepareFrame} より前に呼ぶこと
     * ({@link FeatureRenderDispatcher} は同時に 1 フレームしか準備できないため)。
     */
    public void renderCapture(FeatureRenderDispatcher dispatcher, CameraRenderState camera, GpuBufferSlice terrainFog, FrameParams params) {
        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.gameRenderer.mainRenderTarget();
        ensureTextures(mainTarget.width, mainTarget.height);

        GpuDevice device = RenderSystem.getDevice();
        device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, new Vector4f(0f), depthTexture, 0.0);

        // メインパスと同じ霧・ライティングで描く (バニラはメインパス開始時に設定するので、ここで先に設定する)。
        RenderSystem.setShaderFog(terrainFog);
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
             RenderPass pass = device.createCommandEncoder().createRenderPass(
                     () -> "Clear Third Person Aim capture", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }

        writeParams(params, camera, mainTarget.width, mainTarget.height);
        // パイプラインの取得 (初回はコンパイル) もコマンド発行を伴うので、描画パスを開く前に済ませる。
        opaquePipeline = RenderSystem.getCompiledPipelineNullable(COMPOSITE_OPAQUE);
        translucentPipeline = RenderSystem.getCompiledPipelineNullable(COMPOSITE_TRANSLUCENT);
        if (opaquePipeline == null || translucentPipeline == null) {
            throw new IllegalStateException("composite pipeline failed to compile");
        }
        nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private void writeParams(FrameParams params, CameraRenderState camera, int width, int height) {
        if (paramsBuffer == null) {
            paramsBuffer = new MappableRingBuffer(() -> "Clear Third Person Aim params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, PARAMS_SIZE);
        } else {
            // 前フレームで使ったバッファにフェンスを張って次のバッファへ進む。GPU がまだ読んでいるバッファを上書きしないため。
            // フェンス作成はコマンド発行なので、描画パスの外 (ここ) で行う必要がある。
            paramsBuffer.rotate();
        }
        invViewProj.set(RenderHooks.levelProjection()).mul(camera.viewRotationMatrix).invert();
        var feet = RenderHooks.feetRelativeToCamera();
        try (GpuBufferSlice.MappedView view = paramsBuffer.currentBuffer().slice().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(params.mode().ordinal(), params.progress(), params.remainingOpacity(), params.feather())
                    .putVec4(params.radiusX(), params.radiusY(), params.waistHeight(), MaskMath.WAIST_FEATHER)
                    .putVec4(width, height, 0f, 0f)
                    .putVec4(feet.x, feet.y, feet.z, 0f)
                    .putMat4f(invViewProj);
        }
    }

    /** 不透明物の描画パスの中で呼ぶ。 */
    public void composite(RenderPass pass) {
        GpuBuffer params = paramsBuffer.currentBuffer();
        pass.pushDebugGroup(() -> "Clear Third Person Aim composite");
        for (CompiledRenderPipeline pipeline : new CompiledRenderPipeline[]{opaquePipeline, translucentPipeline}) {
            pass.setPipeline(pipeline);
            pass.setUniform("PlayerColor", colorView, nearestSampler);
            pass.setUniform("PlayerDepth", depthView, nearestSampler);
            pass.setUniform("ClearAimParams", params);
            pass.draw(3, 1, 0, 0);
        }
        pass.popDebugGroup();
    }

    private void ensureTextures(int width, int height) {
        if (colorTexture != null && colorTexture.getWidth(0) == width && colorTexture.getHeight(0) == height) return;
        closeTextures();
        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        colorTexture = device.createTexture(() -> "Clear Third Person Aim color", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        colorView = device.createTextureView(colorTexture);
        depthTexture = device.createTexture(() -> "Clear Third Person Aim depth", usage, GpuFormat.D32_FLOAT, width, height, 1, 1);
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
