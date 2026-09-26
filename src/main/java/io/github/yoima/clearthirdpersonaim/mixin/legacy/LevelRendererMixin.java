package io.github.yoima.clearthirdpersonaim.mixin.legacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.yoima.clearthirdpersonaim.render.legacy.LegacyHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 専用。注入箇所 ({@code LevelRenderer#renderLevel}):
 * <ol>
 *   <li>先頭 … このフレームで透明化するかを決め、ビュー回転と射影行列 (画面揺れ込み) を記録する。
 *       この世代は renderLevel の引数に実際の行列が渡されるので、GameRenderer への注入は要らない。</li>
 *   <li>半透明の地形 ({@code RenderType.translucent()}) を描く直前 … 不透明物の上にプレイヤーを合成する。
 *       Fabulous グラフィックの有無で呼び出し位置が 2 か所に分かれているので、描画レイヤーの種類で判定する。</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void clearThirdPersonAim$beginFrame(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                                LightTexture lightTexture, Matrix4f viewRotation, Matrix4f projection, CallbackInfo ci) {
        LegacyHooks.beginLevelRender(deltaTracker.getGameTimeDeltaPartialTick(false), viewRotation, projection);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void clearThirdPersonAim$compositeBeforeTranslucent(LevelRenderer self, RenderType renderType, double x, double y, double z,
                                                                Matrix4f frustumMatrix, Matrix4f projection, Operation<Void> original) {
        if (renderType == RenderType.translucent()) LegacyHooks.composite();
        original.call(self, renderType, x, y, z, frustumMatrix, projection);
    }
}
