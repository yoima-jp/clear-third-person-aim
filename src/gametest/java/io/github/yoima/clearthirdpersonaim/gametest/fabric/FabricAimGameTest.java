package io.github.yoima.clearthirdpersonaim.gametest.fabric;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.gui.ConfigScreen;
import io.github.yoima.clearthirdpersonaim.gametest.AimScenario;
import io.github.yoima.clearthirdpersonaim.gametest.ClientTestDriver;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fabric のクライアントゲームテスト API で {@link AimScenario} を動かす (1.21.4 以降の Fabric)。
 * ワールド作成・入力・撮影は API に任せ、Mod Menu 連携の確認だけ Fabric 固有に追加する。
 */
public final class FabricAimGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Driver driver = new Driver(context);
        AimScenario scenario = new AimScenario(driver, "Fabric", FabricLoader.getInstance().getConfigDir());
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            driver.world = world;
            //? if >=26.2 {
            world.getConnection().waitForChunksRender();
            //?} elif >=26.1 {
            /*world.getClientLevel().waitForChunksRender();
            *///?} else {
            /*world.getClientWorld().waitForChunksRender();
            *///?}
            scenario.run();
            runModMenuCheck(context, scenario);
        } catch (RuntimeException | Error failure) {
            // finally でも結果を書き出すため、未捕捉例外を先に失敗として記録し、誤った PASS を防ぐ。
            scenario.check(false, "client test aborted: " + failure);
            throw failure;
        } finally {
            scenario.writeResults();
        }
        if (!scenario.failures().isEmpty()) {
            throw new AssertionError("Clear Third Person Aim client test failed:\n - " + String.join("\n - ", scenario.failures()));
        }
    }

    /**
     * Mod Menu が入っている構成 (-Pcta.withModMenu) だけ、Mod Menu の設定ボタンが作る画面が本 MOD の設定画面であることを確かめる。
     * Mod Menu はゲームテストのコンパイル時依存にしないので、エントリポイントをリフレクションで呼ぶ。
     */
    private static void runModMenuCheck(ClientGameTestContext context, AimScenario scenario) {
        var loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("modmenu")) {
            scenario.results().add("[modmenu] not installed; integration check skipped (the mod loaded without it)");
            return;
        }
        String screenClass;
        try {
            screenClass = context.<String, ReflectiveOperationException>computeOnClient(mc -> {
                for (var container : loader.getEntrypointContainers("modmenu", Object.class)) {
                    if (!container.getProvider().getMetadata().getId().equals(ClearThirdPersonAim.MOD_ID)) continue;
                    Object api = container.getEntrypoint();
                    Object factory = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
                    java.lang.reflect.Method create = factory.getClass().getMethod("create", Screen.class);
                    create.setAccessible(true);
                    return create.invoke(factory, (Object) null).getClass().getName();
                }
                return "none";
            });
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not call the Mod Menu entrypoint", e);
        }
        scenario.results().add("[modmenu] config screen factory creates: " + screenClass);
        scenario.check(ConfigScreen.class.getName().equals(screenClass), "Mod Menu should open ConfigScreen, got " + screenClass);
    }

    private static final class Driver implements ClientTestDriver {
        private final ClientGameTestContext context;
        private TestSingleplayerContext world;

        Driver(ClientGameTestContext context) {
            this.context = context;
        }

        @Override
        public void waitTicks(int ticks) {
            context.waitTicks(ticks);
        }

        @Override
        public <T> T computeOnClient(Function<Minecraft, T> function) {
            return context.computeOnClient(function::apply);
        }

        @Override
        public void runCommand(String command) {
            world.getServer().runCommand(command);
        }

        /**
         * Sodium が入っているときは、Fabric の撮影処理を使わず、直前に通常描画されたフレームを撮影する。
         * Fabric の撮影処理は GameRenderer#update を呼ばずに描画し直すため、Sodium 0.9.2 (26.1.2) が
         * 「terrain uniform が未更新」で例外を投げてテストが中断する (Fabric API issue #5466、KNOWN_ISSUES K-004)。
         * 自作ドライバと同じく、1 tick 進めて最新のフレームを読み戻す。それ以外の構成では Fabric の撮影処理をそのまま使う。
         */
        @Override
        public Path screenshot(String name, Path directory) {
            if (!FabricLoader.getInstance().isModLoaded("sodium")) {
                return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(directory));
            }
            Path file = directory.resolve(name + ".png");
            try {
                java.nio.file.Files.createDirectories(directory);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            context.waitTick();
            java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
            context.runOnClient(mc -> {
                //? if >=26.2 {
                var target = mc.gameRenderer.mainRenderTarget();
                //?} else {
                /*var target = mc.getMainRenderTarget();
                *///?}
                net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                    try (image) {
                        image.writeToFile(file);
                        done.complete(null);
                    } catch (Throwable t) {
                        done.completeExceptionally(t);
                    }
                });
            });
            // 読み戻しは非同期なので、完了するまで tick を進める。
            for (int i = 0; i < 200 && !done.isDone(); i++) context.waitTick();
            done.join();
            return file;
        }

        @Override
        public void holdUse(boolean down) {
            if (down) {
                context.getInput().holdKey(options -> options.keyUse);
            } else {
                context.getInput().releaseKey(options -> options.keyUse);
            }
        }

        @Override
        public void setScreen(Supplier<Screen> screen) {
            context.setScreen(screen::get);
        }

        @Override
        public void clickGui(int guiX, int guiY) {
            double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
            context.getInput().setCursorPos((guiX + 0.5) * scale, (guiY + 0.5) * scale);
            //? if >=26.3 {
            context.getInput().pressMouse(1); // SDL_BUTTON_LEFT
            //?} else {
            /*context.getInput().pressMouse(0); // GLFW_MOUSE_BUTTON_LEFT
            *///?}
        }

        @Override
        public void clickButton(String translationKey) {
            context.clickScreenButton(translationKey);
        }
    }
}
