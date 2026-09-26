package io.github.yoima.clearthirdpersonaim.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 注入箇所: {@code LivingEntityRenderer#submit} の本体描画部分。
 * 目的: フォールバック描画 (シェーダーパック使用中など) で、ローカルプレイヤーの本体を半透明にする。
 * バニラには「透明化したチームメイトを半透明で見せる」経路 (entityTranslucentCull + 固定色 0x26FFFFFF) があるので、
 * その経路に乗せて色のアルファだけ設定値に差し替える。シェーダーパックもこの経路は正しく扱える。
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @ModifyExpressionValue(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private boolean clearThirdPersonAim$hideBodyForFallback(boolean visible) {
        return RenderHooks.isFallbackActive() ? false : visible;
    }

    @ModifyExpressionValue(method = "submit", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;isInvisibleToPlayer:Z"))
    private boolean clearThirdPersonAim$showTranslucentForFallback(boolean invisibleToPlayer) {
        return RenderHooks.isFallbackActive() ? false : invisibleToPlayer;
    }

    @ModifyExpressionValue(method = "submit", at = @At(value = "CONSTANT", args = "intValue=654311423"))
    private int clearThirdPersonAim$fallbackColor(int translucentColor) {
        return RenderHooks.isFallbackActive() ? RenderHooks.fallbackColor() : translucentColor;
    }
}
