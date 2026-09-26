package io.github.yoima.clearthirdpersonaim.client;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import net.minecraft.client.Minecraft;
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
        if (Boolean.getBoolean("cta.smoke")) startSmokeTest(loader);
        if (!Boolean.getBoolean("cta.selftest")) return;
        try {
            Class.forName(SELF_TEST_CLASS)
                    .getMethod("start", String.class, Path.class, Function.class)
                    .invoke(null, loader, configDir, configScreenFactory);
        } catch (ReflectiveOperationException | LinkageError e) {
            ClearThirdPersonAim.LOGGER.error("cta.selftest was requested but the self test could not be started", e);
        }
    }

    /**
     * 配布用 jar をそのまま起動する確認 (-Dcta.smoke=true、Fabric の runProductionSmoke から使う)。
     * Mixin は起動時に対象クラスへ適用され、失敗すれば起動が止まるので、メニュー画面まで到達すれば
     * 配布用 jar のマッピング (1.21.x は intermediary) で全 Mixin が適用できたことになる。確認したらゲームを終了する。
     */
    private static void startSmokeTest(String loader) {
        Thread thread = new Thread(() -> {
            try {
                Minecraft mc;
                while ((mc = Minecraft.getInstance()) == null) Thread.sleep(100);
                Minecraft client = mc;
                long deadline = System.currentTimeMillis() + 10 * 60_000L;
                while (System.currentTimeMillis() < deadline) {
                    boolean ready = client.submit(() -> currentScreen(client) != null && noOverlay(client)).get();
                    if (ready) {
                        ClearThirdPersonAim.LOGGER.info("[smoke] {} MC {}: reached {} with all mixins applied; renderer = {}",
                                loader, ClearThirdPersonAim.MINECRAFT, client.submit(() -> currentScreen(client).getClass().getSimpleName()).get(),
                                client.submit(() -> ClearAimClient.get().renderer().currentMethod()).get());
                        client.execute(client::stop);
                        return;
                    }
                    Thread.sleep(200);
                }
                ClearThirdPersonAim.LOGGER.error("[smoke] timed out waiting for the main menu");
                Runtime.getRuntime().halt(1);
            } catch (Exception e) {
                ClearThirdPersonAim.LOGGER.error("[smoke] failed", e);
                Runtime.getRuntime().halt(1);
            }
        }, "Clear Third Person Aim smoke test");
        thread.setDaemon(true);
        thread.start();
    }

    private static Screen currentScreen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    private static boolean noOverlay(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.overlay() == null;
        //?} else {
        /*return mc.getOverlay() == null;
        *///?}
    }
}
