package io.github.yoima.clearthirdpersonaim.mixin.legacy;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1 専用。注入箇所: {@code LivingEntityRenderer#render} の本体描画部分。
 * 目的は modern 版と同じ (フォールバック描画で、バニラの「透明化したチームメイトを半透明で見せる」経路に乗せ、アルファだけ差し替える)。
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean clearThirdPersonAim$hideBodyForFallback(boolean visible) {
        return RenderHooks.isFallbackActive() ? false : visible;
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean clearThirdPersonAim$showTranslucentForFallback(boolean invisibleToPlayer) {
        return RenderHooks.isFallbackActive() ? false : invisibleToPlayer;
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "CONSTANT", args = "intValue=654311423"))
    private int clearThirdPersonAim$fallbackColor(int translucentColor) {
        return RenderHooks.isFallbackActive() ? RenderHooks.fallbackColor() : translucentColor;
    }
}
