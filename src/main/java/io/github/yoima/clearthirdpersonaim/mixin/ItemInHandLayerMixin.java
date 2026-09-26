package io.github.yoima.clearthirdpersonaim.mixin;

import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 注入箇所: {@code ItemInHandLayer#submitArmWithItem} の先頭 (引数の登録先)。
 * 目的: 「手持ちの武器は透明化しない」設定 (既定) のとき、プレイヤー本体を合成用に振り分けている最中でも
 * 手持ちアイテムだけは通常のストレージへ登録し、弓が見えたままになるようにする。
 * 深度は通常どおり書かれるので、腕より奥にある弓は合成時の深度比較で正しく隠れる。
 */
@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector clearThirdPersonAim$keepHeldItemVisible(SubmitNodeCollector collector) {
        return RenderHooks.heldItemCollector(collector);
    }
}
