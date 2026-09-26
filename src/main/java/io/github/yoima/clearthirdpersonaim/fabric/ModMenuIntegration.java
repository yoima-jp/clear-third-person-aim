package io.github.yoima.clearthirdpersonaim.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.yoima.clearthirdpersonaim.client.gui.ConfigScreen;

/**
 * Mod Menu 連携 (任意依存)。fabric.mod.json の "modmenu" エントリポイントは Mod Menu が入っているときだけ
 * 読み込まれるので、Mod Menu が無い環境ではこのクラスはロードされずクラッシュしない。
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
