package io.github.yoima.clearthirdpersonaim.gametest.selftest;

import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.gametest.ClientTestDriver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 自作のテストドライバ。テスト用スレッドからクライアントスレッドへ処理を投げ、
 * {@link ClearAimClient#clientTicks()} (クライアント tick の終わりで増える) を数えて待つ。
 * 入力はキーマッピングと画面のマウスイベントを直接呼んで再現する (OS のマウス・キーボードは使わない)。
 */
final class SelfTestDriver implements ClientTestDriver {
    private static final long CLIENT_TIMEOUT_SECONDS = 60;

    private final Minecraft mc;

    SelfTestDriver(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public void waitTicks(int ticks) {
        long target = ClearAimClient.get().clientTicks() + ticks;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLIENT_TIMEOUT_SECONDS + ticks / 20);
        while (ClearAimClient.get().clientTicks() < target) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("client did not tick for too long");
            sleep();
        }
    }

    void waitFor(String what, Predicate<Minecraft> condition, int timeoutTicks) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutTicks * 50L);
        while (!computeOnClient(condition::test)) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("timed out waiting for " + what);
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> T computeOnClient(Function<Minecraft, T> function) {
        try {
            return mc.submit(() -> function.apply(mc)).get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("client task failed", e);
        }
    }

    @Override
    public void runCommand(String command) {
        MinecraftServer server = computeOnClient(Minecraft::getSingleplayerServer);
        try {
            server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command))
                    .get(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("command failed: " + command, e);
        }
        waitTicks(1);
    }

    @Override
    public Path screenshot(String name, Path directory) {
        Path file = directory.resolve(name + ".png");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // 1 tick 待って、直前の設定変更が描画に反映されたフレームを撮る。
        waitTicks(1);
        CompletableFuture<Void> done = new CompletableFuture<>();
        runOnClient(m -> {
            //? if >=26.2 {
            var target = m.gameRenderer.mainRenderTarget();
            //?} else {
            /*var target = m.getMainRenderTarget();
            *///?}
            //? if >=1.21.5 {
            Screenshot.takeScreenshot(target, image -> {
                try (image) {
                    image.writeToFile(file);
                    done.complete(null);
                } catch (Throwable t) {
                    done.completeExceptionally(t);
                }
            });
            //?} else {
            /*try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(file);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
            *///?}
        });
        // 非同期の読み戻しはクライアントが進まないと完了しないので、tick を回しながら待つ。
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLIENT_TIMEOUT_SECONDS);
        while (!done.isDone()) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("screenshot timed out: " + name);
            waitTicks(1);
        }
        done.join();
        return file;
    }

    @Override
    public void holdUse(boolean down) {
        runOnClient(m -> m.options.keyUse.setDown(down));
    }

    @Override
    public void setScreen(Supplier<Screen> screen) {
        runOnClient(m -> {
            //? if >=26.2 {
            m.gui.setScreen(screen.get());
            //?} else {
            /*m.setScreen(screen.get());
            *///?}
        });
    }

    @Override
    public void clickGui(int guiX, int guiY) {
        runOnClient(m -> click(currentScreen(m), guiX + 0.5, guiY + 0.5));
        waitTicks(1);
    }

    @Override
    public void clickButton(String translationKey) {
        runOnClient(m -> {
            for (var child : currentScreen(m).children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.active
                        && widget.getMessage().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(translationKey)) {
                    click(currentScreen(m), widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
                    return;
                }
            }
            throw new IllegalStateException("no active button with key " + translationKey + " on " + currentScreen(m));
        });
        waitTicks(1);
    }

    /** リソース読み込み画面などのオーバーレイが出ていないか。 */
    static boolean hasNoOverlay(Minecraft m) {
        //? if >=26.2 {
        return m.gui.overlay() == null;
        //?} else {
        /*return m.getOverlay() == null;
        *///?}
    }

    static Screen currentScreen(Minecraft m) {
        //? if >=26.2 {
        return m.gui.screen();
        //?} else {
        /*return m.screen;
        *///?}
    }

    /** 画面にマウスの押下と解放を送る (GUI 座標)。 */
    private static void click(Screen screen, double x, double y) {
        //? if >=1.21.9 {
        var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(LEFT_BUTTON, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        //?} else {
        /*screen.mouseClicked(x, y, LEFT_BUTTON);
        screen.mouseReleased(x, y, LEFT_BUTTON);
        *///?}
    }

    //? if >=26.3 {
    private static final int LEFT_BUTTON = 1; // SDL_BUTTON_LEFT
    //?} else {
    /*private static final int LEFT_BUTTON = 0; // GLFW_MOUSE_BUTTON_LEFT
    *///?}

    /**
     * スーパーフラット・クリエイティブ・コマンド有効のワールドを作って入る。
     * Fabric のゲームテスト API と同じく、構造物なし・固定シードにして毎回同じ地形にする。
     */
    void createFlatWorld() {
        String name = "cta_selftest_" + System.currentTimeMillis();
        runOnClient(m -> {
            //? if >=26.1 {
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
            //?} else {
            /*LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true,
                    new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
            *///?}
            m.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1L, false, false),
                    registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
        });
    }
}
