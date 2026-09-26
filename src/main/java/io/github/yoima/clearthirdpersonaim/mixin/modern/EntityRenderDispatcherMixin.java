package io.github.yoima.clearthirdpersonaim.mixin.modern;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import io.github.yoima.clearthirdpersonaim.render.modern.ModernHooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 注入箇所: {@code EntityRenderDispatcher#submit} 内の {@code EntityRenderer#submit} 呼び出し (本体とレイヤーの登録)。
 * 目的: ローカルプレイヤーの本体・防具・マント・オウム等を、オフスクリーン合成用のストレージへ振り分ける。
 * 影と炎はこの呼び出しの外で登録されるので、通常どおりメイン画面に描かれる (影まで透けると足元の接地感が失われる)。
 * フォールバック時は、同じ場所で本体を半透明描画に切り替えるフラグを立てる。
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    //? if >=26.1 {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    //?} else {
    /*@WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
    *///?}
    private void clearThirdPersonAim$routeLocalPlayer(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack,
                                                      SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        if (!ModernHooks.isLocalPlayer(state)) {
            original.call(renderer, state, poseStack, collector, camera);
            return;
        }
        if (RenderHooks.shouldCapture()) {
            SubmitNodeCollector capture = ModernHooks.beginCapture(collector, state.x - camera.pos.x, state.y - camera.pos.y, state.z - camera.pos.z);
            try {
                original.call(renderer, state, poseStack, capture, camera);
            } finally {
                ModernHooks.endCapture();
            }
        } else if (RenderHooks.shouldUseFallback()) {
            RenderHooks.beginFallback();
            try {
                original.call(renderer, state, poseStack, collector, camera);
            } finally {
                RenderHooks.endFallback();
            }
        } else {
            original.call(renderer, state, poseStack, collector, camera);
        }
    }

}
