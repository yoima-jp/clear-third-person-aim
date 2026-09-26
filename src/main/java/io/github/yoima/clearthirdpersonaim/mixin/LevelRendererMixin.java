package io.github.yoima.clearthirdpersonaim.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入箇所 (すべて {@code LevelRenderer}):
 * <ol>
 *   <li>{@code render} の先頭 … このフレームで透明化するかを決める。</li>
 *   <li>{@code render} でメインの {@code prepareFrame} を呼ぶ直前 … 振り分けたプレイヤーをオフスクリーンへ描く。
 *       FeatureRenderDispatcher は同時に 1 フレームしか準備できないので、必ずメインより前に済ませる。</li>
 *   <li>{@code executeSolid} の末尾 … 不透明物を描き終えたメインのパスへ合成する。
 *       improved transparency (OIT) の有無にかかわらず呼ばれる共通地点で、半透明物より前なので水などが正しく重なる。</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(method = "render", at = @At("HEAD"))
    private void clearThirdPersonAim$beginFrame(CallbackInfo ci) {
        RenderHooks.beginLevelRender(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private void clearThirdPersonAim$renderCapture(CallbackInfo ci, @Local(argsOnly = true) CameraRenderState camera,
                                                   @Local(argsOnly = true) GpuBufferSlice terrainFog) {
        RenderHooks.renderCapture(featureRenderDispatcher, camera, terrainFog);
    }

    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void clearThirdPersonAim$composite(CallbackInfo ci, @Local(argsOnly = true) RenderPass renderPass) {
        RenderHooks.composite(renderPass);
    }
}
