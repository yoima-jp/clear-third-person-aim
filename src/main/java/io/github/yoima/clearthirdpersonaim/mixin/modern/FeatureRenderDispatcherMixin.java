package io.github.yoima.clearthirdpersonaim.mixin.modern;

import io.github.yoima.clearthirdpersonaim.render.modern.ModernHooks;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9〜26.1.x 専用 (ビルドスクリプトが他のバージョンではコンパイル対象から外す)。
 * 注入箇所: メインの FeatureRenderDispatcher が不透明なエンティティ等を描くメソッド
 * (26.1: {@code renderSolidFeatures}、1.21.11: {@code renderAllFeatures}) の先頭と末尾。
 * <ul>
 *   <li>先頭 … このフレームで振り分けたプレイヤーを専用テクスチャへ描く (登録はこの直前の submitEntities で済んでいる)。</li>
 *   <li>末尾 … メインのバッファを描き切ってから、不透明物の上にプレイヤーを合成する。バニラもこの直後に同じバッファを
 *       描き切るので、先に描いても見た目の順序は変わらない。</li>
 * </ul>
 * キャプチャ専用のディスパッチャや GUI の人物表示でも同じメソッドが呼ばれるが、キャプチャは 1 フレームに 1 回、
 * 合成はキャプチャ後の最初の 1 回だけに制限しているので、ワールド描画の呼び出しでだけ働く。
 */
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
    //? if >=26.1 {
    @Inject(method = "renderSolidFeatures", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "renderAllFeatures", at = @At("HEAD"))
    *///?}
    private void clearThirdPersonAim$renderCapture(CallbackInfo ci) {
        if (ModernHooks.compositor().isOwnDispatcher(this)) return;
        ModernHooks.renderCapture();
    }

    //? if >=26.1 {
    @Inject(method = "renderSolidFeatures", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "renderAllFeatures", at = @At("TAIL"))
    *///?}
    private void clearThirdPersonAim$composite(CallbackInfo ci) {
        if (ModernHooks.compositor().isOwnDispatcher(this)) return;
        ModernHooks.flushMainBuffersAndComposite();
    }
}
