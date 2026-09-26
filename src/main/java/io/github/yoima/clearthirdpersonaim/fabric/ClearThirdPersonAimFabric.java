package io.github.yoima.clearthirdpersonaim.fabric;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.client.SelfTestLauncher;
import io.github.yoima.clearthirdpersonaim.client.gui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}

public final class ClearThirdPersonAimFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClearThirdPersonAim.LOGGER.info("Clear Third Person Aim {} (Fabric, MC {})", ClearThirdPersonAim.VERSION, ClearThirdPersonAim.MINECRAFT);
        ClearAimClient client = ClearAimClient.init(FabricLoader.getInstance().getConfigDir());
        //? if >=26.1 {
        KeyMappingHelper.registerKeyMapping(client.toggleKey());
        //?} else {
        /*KeyBindingHelper.registerKeyBinding(client.toggleKey());
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(client::tick);
        // 開発用の自作クライアントテスト (ゲームテスト API の無い 1.21.1 用)。-Dcta.selftest=true のときだけ動く。
        SelfTestLauncher.startIfRequested("Fabric", FabricLoader.getInstance().getConfigDir(), ConfigScreen::new);
    }
}
