package io.github.yoima.clearthirdpersonaim.client;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * 開発用の自作クライアントテスト (NeoForge / Fabric 1.21.1 用) を起動する。
 * {@code -Dcta.selftest=true} を付けた開発用の実行構成でだけ動く。テスト本体は gametest ソースセットにあり、
 * 配布 jar には含まれないので、リフレクションで探して無ければ何もしない。
 */
public final class SelfTestLauncher {
    private static final String SELF_TEST_CLASS = "io.github.yoima.clearthirdpersonaim.gametest.selftest.SelfTest";

    private SelfTestLauncher() {
    }

    public static void startIfRequested(String loader, Path configDir, Function<Screen, Screen> configScreenFactory) {
        if (!Boolean.getBoolean("cta.selftest")) return;
        try {
            Class.forName(SELF_TEST_CLASS)
                    .getMethod("start", String.class, Path.class, Function.class)
                    .invoke(null, loader, configDir, configScreenFactory);
        } catch (ReflectiveOperationException | LinkageError e) {
            ClearThirdPersonAim.LOGGER.error("cta.selftest was requested but the self test could not be started", e);
        }
    }
}
