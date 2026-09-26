package io.github.yoima.clearthirdpersonaim.mixin.modern;

import io.github.yoima.clearthirdpersonaim.render.modern.ModernHooks;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 専用 (ビルドスクリプトが他のバージョンではコンパイル対象から外す)。
 * 注入箇所: {@code FeatureRenderDispatcher.PreparedFrame#executeSolid} の末尾。
 * 目的: メインのパスで不透明なエンティティ等を描き終えた直後 (半透明物より前) に、オフスクリーンのプレイヤーを合成する。
 * このメソッドは自前のキャプチャ描画でも呼ばれるが、合成はキャプチャ完了後にだけ許可されるので二重には走らない。
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
abstract class PreparedFrameMixin {
    @Inject(method = "executeSolid", at = @At("TAIL"))
    private void clearThirdPersonAim$composite(CallbackInfo ci) {
        ModernHooks.composite();
    }
}
