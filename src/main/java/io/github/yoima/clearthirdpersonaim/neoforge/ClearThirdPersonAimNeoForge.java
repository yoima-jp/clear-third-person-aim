package io.github.yoima.clearthirdpersonaim.neoforge;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = ClearThirdPersonAim.MOD_ID, dist = Dist.CLIENT)
public final class ClearThirdPersonAimNeoForge {
    public ClearThirdPersonAimNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        ClearThirdPersonAim.LOGGER.info("Clear Third Person Aim {} (NeoForge, MC {})", ClearThirdPersonAim.VERSION, ClearThirdPersonAim.MINECRAFT);
    }
}
