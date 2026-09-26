package io.github.yoima.clearthirdpersonaim.mixin.modern;

import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 注入箇所: {@code GameRenderer#renderLevel} でワールド用の射影行列を GPU に渡す直前。
 * 目的: UPPER_BODY モードの合成シェーダーで深度からワールド位置を復元するため、実際に使われた射影行列
 * (視点の揺れ・被ダメージ時の揺れ込み) を記録する。カメラ状態の素の射影行列を使うと、歩行中の揺れの分だけ
 * 腰の境界が上下にぶれるので、ここで実物を受け取る。値は変更しない。
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    //? if >=26.3 {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    //?} elif >=26.1 {
    /*@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    *///?} else {
    /*@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    *///?}
    private Matrix4f clearThirdPersonAim$captureProjection(Matrix4f projection) {
        RenderHooks.setLevelProjection(projection);
        return projection;
    }
}
