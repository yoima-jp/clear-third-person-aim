package io.github.yoima.clearthirdpersonaim.neoforge;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.client.SelfTestLauncher;
import io.github.yoima.clearthirdpersonaim.client.gui.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge のエントリポイント。クライアント専用 (dist = CLIENT) なので、専用サーバーでは読み込まれない。
 * 描画は Fabric と同じ Mixin で行い、ここでは設定の読み込み・キー登録・tick・設定画面の登録だけを行う。
 */
@Mod(value = ClearThirdPersonAim.MOD_ID, dist = Dist.CLIENT)
public final class ClearThirdPersonAimNeoForge {
    public ClearThirdPersonAimNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        ClearThirdPersonAim.LOGGER.info("Clear Third Person Aim {} (NeoForge, MC {})", ClearThirdPersonAim.VERSION, ClearThirdPersonAim.MINECRAFT);
        ClearAimClient client = ClearAimClient.init(FMLPaths.CONFIGDIR.get());
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=1.21.9 {
            event.registerCategory(ClearAimClient.KEY_CATEGORY);
            //?}
            event.register(client.toggleKey());
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> client.tick(Minecraft.getInstance()));
        // Mods 画面の「設定」ボタンから開く。
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> new ConfigScreen(parent));
        SelfTestLauncher.startIfRequested("NeoForge", FMLPaths.CONFIGDIR.get(), parent ->
                modContainer.getCustomExtension(IConfigScreenFactory.class).orElseThrow().createScreen(modContainer, parent));
    }
}
