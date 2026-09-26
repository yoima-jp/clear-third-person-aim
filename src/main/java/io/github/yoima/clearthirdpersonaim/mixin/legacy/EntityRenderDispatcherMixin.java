package io.github.yoima.clearthirdpersonaim.mixin.legacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import io.github.yoima.clearthirdpersonaim.render.legacy.LegacyHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1 専用。注入箇所: {@code EntityRenderDispatcher#render} 内の {@code EntityRenderer#render} 呼び出し (本体とレイヤーの描画)。
 * 目的は modern 版 (mixin.modern.EntityRenderDispatcherMixin) と同じ。影と炎はこの呼び出しの外で描かれるので、通常どおり残る。
 * この世代は描画内容をその場でバッファへ積むので、呼び出しが終わった時点で専用ターゲットへ描き切る。
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void clearThirdPersonAim$routeLocalPlayer(EntityRenderer<?> renderer, Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                                      MultiBufferSource buffers, int light, Operation<Void> original,
                                                      @Local(argsOnly = true, ordinal = 0) double x,
                                                      @Local(argsOnly = true, ordinal = 1) double y,
                                                      @Local(argsOnly = true, ordinal = 2) double z) {
        if (!LegacyHooks.isLocalPlayer(entity)) {
            original.call(renderer, entity, yaw, partialTick, poseStack, buffers, light);
            return;
        }
        if (RenderHooks.shouldCapture()) {
            MultiBufferSource capture = LegacyHooks.beginCapture(buffers, x, y, z);
            try {
                original.call(renderer, entity, yaw, partialTick, poseStack, capture, light);
            } finally {
                LegacyHooks.endCapture();
            }
        } else if (RenderHooks.shouldUseFallback()) {
            RenderHooks.beginFallback();
            try {
                original.call(renderer, entity, yaw, partialTick, poseStack, buffers, light);
            } finally {
                RenderHooks.endFallback();
            }
        } else {
            original.call(renderer, entity, yaw, partialTick, poseStack, buffers, light);
        }
    }
}
