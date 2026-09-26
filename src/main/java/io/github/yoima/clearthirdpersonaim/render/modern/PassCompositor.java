package io.github.yoima.clearthirdpersonaim.render.modern;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.MaskMath;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Matrix4f;
import org.joml.Vector4f;
//? if >=26.3 {
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
//?} else {
/*import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
*///?}

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 26.2 / 26.3 向けのオフスクリーン合成 (描画登録を {@link SubmitNodeStorage} で行い、FeatureRenderDispatcher が
 * 準備済みフレームとして描く世代)。26.3 はレンダーパスを明示的に渡す Renderpearl、26.2 は出力先の上書き
 * (RenderSystem.outputColorTextureOverride) で描画先を切り替える、という違いだけを分岐している。
 *
 * <p>ローカルプレイヤーを専用の {@link SubmitNodeStorage} に登録させ、メインの描画前に専用のカラー + 深度テクスチャへ
 * 不透明で描く。合成は不透明物を描いた直後に、全画面三角形 1 枚をマスク付きで描いて行う。
 * テクスチャはウィンドウサイズが変わったときだけ作り直す (毎フレーム生成しない)。
 *
 * <p>合成は 2 回の描画に分ける:
 * <ul>
 *   <li>不透明パス: 不透明度 1 のピクセル (マスク外) だけを深度書き込みありで描く。後から描かれる半透明ブロックと正しく前後判定されるように。</li>
 *   <li>半透明パス: 透けているピクセルだけを深度書き込みなしで描く。後ろにある水などが透けた体に隠されないように。</li>
 * </ul>
 * どちらの世代も reversed-Z (手前ほど深度が大きく、クリア値は 0)。
 */
public final class PassCompositor {
    // std140: vec4 x4 + mat4 = 128 bytes
    private static final int PARAMS_SIZE = 16 * 4 + 64;

    //? if >=26.3 {
    private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withUniform("PlayerColor", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("PlayerDepth", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    //?} else {
    /*private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withSampler("PlayerColor")
            .withSampler("PlayerDepth")
            .build();
    *///?}
    private static final BindGroupLayout PARAMS = BindGroupLayout.builder()
            .withUniform("ClearAimParams", UniformType.UNIFORM_BUFFER)
            .build();

    static final RenderPipeline COMPOSITE_OPAQUE = pipeline("composite_opaque", true);
    static final RenderPipeline COMPOSITE_TRANSLUCENT = pipeline("composite_translucent", false);

    private static RenderPipeline pipeline(String name, boolean opaque) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ClearThirdPersonAim.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                //? if >=26.3 {
                .withFragmentShader(ClearThirdPersonAim.id("core/composite"))
                //?} else {
                /*.withFragmentShader(ClearThirdPersonAim.id("core/composite_gl330"))
                *///?}
                .withBindGroupLayout(SAMPLERS)
                .withBindGroupLayout(PARAMS)
                // オフスクリーンの色は、半透明のスキン外層が透明な背景へブレンドされた「乗算済みアルファ」になっている。
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                // reversed-Z なので、バニラのエンティティと同じく「以上」で比較する。
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
    private GpuSampler nearestSampler;
    //? if >=26.3 {
    private CompiledRenderPipeline opaquePipeline;
    private CompiledRenderPipeline translucentPipeline;
    //?} else {
    /*private boolean pipelinesChecked;
    *///?}

    public SubmitNodeStorage storage() {
        return storage;
    }

    /**
     * 登録済みのプレイヤーを別テクスチャへ描く。メインの {@code prepareFrame} より前に呼ぶこと
     * ({@link FeatureRenderDispatcher} は同時に 1 フレームしか準備できないため)。
     * 霧・ライティングはメインパスと同じ設定にする (バニラはメインパス開始時に設定するので、ここで先に設定する)。
     */
    public void renderCapture(FeatureRenderDispatcher dispatcher, GpuBufferSlice terrainFog, FrameParams params) {
        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.gameRenderer.mainRenderTarget();
        ensureTextures(mainTarget.width, mainTarget.height);

        GpuDevice device = RenderSystem.getDevice();
        device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, new Vector4f(0f), depthTexture, 0.0);
        RenderSystem.setShaderFog(terrainFog);
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        //? if >=26.3 {
        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
             RenderPass pass = device.createCommandEncoder().createRenderPass(
                     () -> "Clear Third Person Aim capture", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
        //?} else {
        /*RenderSystem.outputColorTextureOverride = colorView;
        RenderSystem.outputDepthTextureOverride = depthView;
        try {
            dispatcher.renderAllFeatures(storage);
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        }
        *///?}

        writeParams(params, device, mainTarget.width, mainTarget.height);
        // パイプラインの取得 (初回はコンパイル) もコマンド発行を伴うので、描画パスを開く前に済ませる。
        //? if >=26.3 {
        opaquePipeline = RenderSystem.getCompiledPipelineNullable(COMPOSITE_OPAQUE);
        translucentPipeline = RenderSystem.getCompiledPipelineNullable(COMPOSITE_TRANSLUCENT);
        if (opaquePipeline == null || translucentPipeline == null) {
            throw new IllegalStateException("composite pipeline failed to compile");
        }
        //?} else {
        /*if (!pipelinesChecked) {
            if (!device.precompilePipeline(COMPOSITE_OPAQUE).isValid() || !device.precompilePipeline(COMPOSITE_TRANSLUCENT).isValid()) {
                throw new IllegalStateException("composite pipeline failed to compile");
            }
            pipelinesChecked = true;
        }
        *///?}
        nearestSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private void writeParams(FrameParams params, GpuDevice device, int width, int height) {
        if (paramsBuffer == null) {
            paramsBuffer = new MappableRingBuffer(() -> "Clear Third Person Aim params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, PARAMS_SIZE);
        } else {
            // 前フレームで使ったバッファにフェンスを張って次のバッファへ進む。GPU がまだ読んでいるバッファを上書きしないため。
            // フェンス作成はコマンド発行なので、描画パスの外 (ここ) で行う必要がある。
            paramsBuffer.rotate();
        }
        // メインの描画ではモデルビュー = カメラの回転 (位置はカメラ相対)。深度からカメラ相対のワールド位置を復元するための逆行列。
        invViewProj.set(RenderHooks.levelProjection()).mul(RenderSystem.getModelViewStack()).invert();
        var feet = RenderHooks.feetRelativeToCamera();
        boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
        try (GpuBufferSlice.MappedView view = paramsBuffer.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(params.mode().ordinal(), params.progress(), params.remainingOpacity(), params.feather())
                    .putVec4(params.radiusX(), params.radiusY(), params.waistHeight(), MaskMath.WAIST_FEATHER)
                    // z: 深度が 0..1 (clip control) か -1..1 か、w: プレイヤーが描かれていない画素の深度 (reversed-Z のクリア値)
                    .putVec4(width, height, zeroToOne ? 1f : 0f, 0f)
                    .putVec4(feet.x, feet.y, feet.z, 0f)
                    .putMat4f(invViewProj);
        }
    }

    //? if >=26.3 {
    /** 不透明物の描画パスの中で呼ぶ。 */
    public void composite(RenderPass pass) {
        pass.pushDebugGroup(() -> "Clear Third Person Aim composite");
        for (CompiledRenderPipeline pipeline : new CompiledRenderPipeline[]{opaquePipeline, translucentPipeline}) {
            pass.setPipeline(pipeline);
            pass.setUniform("PlayerColor", colorView, nearestSampler);
            pass.setUniform("PlayerDepth", depthView, nearestSampler);
            pass.setUniform("ClearAimParams", paramsBuffer.currentBuffer());
            pass.draw(3, 1, 0, 0);
        }
        pass.popDebugGroup();
    }
    //?} else {
    /*/^* 不透明物を描き終えた直後に呼ぶ。メイン画面に対して専用のレンダーパスを開く。 ^/
    public void composite() {
        var mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Clear Third Person Aim composite", mainTarget.getColorTextureView(), Optional.empty(),
                mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            for (RenderPipeline pipeline : new RenderPipeline[]{COMPOSITE_OPAQUE, COMPOSITE_TRANSLUCENT}) {
                pass.setPipeline(pipeline);
                pass.bindTexture("PlayerColor", colorView, nearestSampler);
                pass.bindTexture("PlayerDepth", depthView, nearestSampler);
                pass.setUniform("ClearAimParams", paramsBuffer.currentBuffer());
                pass.draw(3, 1, 0, 0);
            }
        }
    }
    *///?}

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
