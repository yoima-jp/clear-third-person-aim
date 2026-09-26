package io.github.yoima.clearthirdpersonaim.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void clearThirdPersonAim$routeLocalPlayer(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack,
                                                      SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        if (!isLocalPlayer(state)) {
            original.call(renderer, state, poseStack, collector, camera);
            return;
        }
        if (RenderHooks.shouldCapture()) {
            RenderHooks.beginCapture(collector, state.x - camera.pos.x, state.y - camera.pos.y, state.z - camera.pos.z);
            try {
                original.call(renderer, state, poseStack, RenderHooks.compositor().storage(), camera);
            } finally {
                RenderHooks.endCapture();
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

    private static boolean isLocalPlayer(EntityRenderState state) {
        var player = Minecraft.getInstance().player;
        return player != null && state instanceof AvatarRenderState avatar && avatar.id == player.getId();
    }
}
