package io.github.yoima.clearthirdpersonaim.mixin.modern;

import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
import com.llamalad7.mixinextras.sugar.Local;
import io.github.yoima.clearthirdpersonaim.render.modern.ModernHooks;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//?}
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
//?} elif >=26.2 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}

/**
 * 注入箇所 ({@code LevelRenderer}):
 * <ol>
 *   <li>ワールド描画の先頭 … このフレームで透明化するかを決める (全世代)。</li>
 *   <li>26.2 以降: メインの {@code prepareFrame} を呼ぶ直前 … 振り分けたプレイヤーをオフスクリーンへ描く。
 *       FeatureRenderDispatcher は同時に 1 フレームしか準備できないので、必ずメインより前に済ませる。</li>
 *   <li>26.3: {@code executeSolid} の末尾 … 不透明物を描き終えたメインのパスへ合成する。
 *       improved transparency (OIT) の有無にかかわらず呼ばれる共通地点で、半透明物より前なので水などが正しく重なる。
 *       (26.2 は PreparedFrameMixin、1.21.11〜26.1 は FeatureRenderDispatcherMixin が同じ役割を持つ。)</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    //? if >=26.2 {
    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;
    //?}

    //? if >=26.2 {
    @Inject(method = "render", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    *///?}
    private void clearThirdPersonAim$beginFrame(CallbackInfo ci) {
        RenderHooks.beginLevelRender(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    //? if >=26.2 {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private void clearThirdPersonAim$renderCapture(CallbackInfo ci, @Local(argsOnly = true) GpuBufferSlice terrainFog) {
        ModernHooks.renderCapture(featureRenderDispatcher, terrainFog);
    }
    //?}

    //? if >=26.3 {
    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void clearThirdPersonAim$composite(CallbackInfo ci, @Local(argsOnly = true) RenderPass renderPass) {
        ModernHooks.composite(renderPass);
    }
    //?}
}
