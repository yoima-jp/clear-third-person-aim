package io.github.yoima.clearthirdpersonaim.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}

//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
abstract class GuiCrosshairMixin {
    // バニラのクロスヘア描画だけで視点条件を広げる。HUD 非表示、観戦者、デバッグ表示などの
    // バニラの判定と描画方法はそのまま使い、三人称でも同じクロスヘアを表示する。
    //? if >=26.1 {
    @ModifyExpressionValue(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    //?} else {
    /*@ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    *///?}
    private boolean clearThirdPersonAim$showCrosshair(boolean vanillaShowsCrosshair) {
        if (vanillaShowsCrosshair) return true;
        Minecraft minecraft = Minecraft.getInstance();
        ClearAimClient client = ClearAimClient.get();
        return client != null && client.config().showCrosshair && client.controller().isActive()
                && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
    }
}
