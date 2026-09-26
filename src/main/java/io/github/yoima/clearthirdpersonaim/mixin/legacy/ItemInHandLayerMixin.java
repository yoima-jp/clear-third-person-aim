package io.github.yoima.clearthirdpersonaim.mixin.legacy;

import io.github.yoima.clearthirdpersonaim.render.legacy.LegacyHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.21.1 専用。注入箇所: {@code ItemInHandLayer#renderArmWithItem} の先頭 (引数の描画先)。
 * 目的は modern 版と同じ (手持ちの武器を透明化しない設定では、通常の描画先へ戻して弓を見えたままにする)。
 */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource clearThirdPersonAim$keepHeldItemVisible(MultiBufferSource buffer) {
        return LegacyHooks.heldItemBuffer(buffer);
    }
}
