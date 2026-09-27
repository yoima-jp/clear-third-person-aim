//? if <26.1 {
package io.github.yoima.clearthirdpersonaim.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 26.1 より前は現在の視野角が GameRenderer の private メソッドにある。描画と同じ補間値を参照する。 */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
    //? if >=1.21.2 {
    @Invoker("getFov")
    float clearThirdPersonAim$invokeGetFov(Camera camera, float partialTick, boolean useFovSetting);
    //?} else {
    /*@Invoker("getFov")
    double clearThirdPersonAim$invokeGetFov(Camera camera, float partialTick, boolean useFovSetting);
    *///?}
}
//?}
