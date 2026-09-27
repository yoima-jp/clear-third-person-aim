package io.github.yoima.clearthirdpersonaim.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 現在の画面・オーバーレイの取得 (26.2 で Gui へ移動した API の差を吸収する)。クライアントスレッドから呼ぶ。 */
public final class Screens {
    private Screens() {
    }

    public static Screen current(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    public static boolean noOverlay(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.overlay() == null;
        //?} else {
        /*return mc.getOverlay() == null;
        *///?}
    }
}
