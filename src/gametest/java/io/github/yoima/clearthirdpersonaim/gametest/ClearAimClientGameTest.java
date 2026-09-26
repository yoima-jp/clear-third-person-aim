package io.github.yoima.clearthirdpersonaim.gametest;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.client.gui.ConfigScreen;
import io.github.yoima.clearthirdpersonaim.core.Config;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.MaskMath;
import io.github.yoima.clearthirdpersonaim.core.TransitionMode;
import io.github.yoima.clearthirdpersonaim.core.TransparencyMode;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 実際のクライアントを起動して、透明化の見た目をピクセル単位で検証する。
 *
 * <p>シーン: スーパーフラットで、プレイヤーの 12 ブロック前に白コンクリートの壁を立て、背面三人称で壁の方を向く。
 * 背景が一様なので、プレイヤーのシルエットと透明化による色の変化を安定して取り出せる。
 *
 * <p>比較の基準は「MOD を無効にした同じ姿勢のスクリーンショット」。プレイヤーの有無は透明化エフェクト
 * (パーティクル非表示) で切り替えてシルエットを得る。スクリーンショットと数値の結果は
 * {@code build/test-screenshots/<version>-fabric/} に保存し、TEST_REPORT.md から参照する。
 */
public final class ClearAimClientGameTest implements FabricClientGameTest {
    private static final int DRAW_TICKS = 30;
    private static final int SETTLE_TICKS = 3;

    private final List<String> results = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private Path outputDir;

    @Override
    public void runTest(ClientGameTestContext context) {
        outputDir = Path.of(System.getProperty("cta.screenshotDir", "screenshots-clear-third-person-aim"));
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        results.add("Clear Third Person Aim client gametest (MC " + ClearThirdPersonAim.MINECRAFT + ", Fabric)");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            setUpScene(context, world);
            runThirdPersonChecks(context, world);
            runConfigScreenCheck(context);
            runFirstPersonCheck(context);
        } finally {
            writeResults();
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Clear Third Person Aim pixel checks failed:\n - " + String.join("\n - ", failures));
        }
    }

    private void setUpScene(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("gamemode creative @a");
        server.runCommand("time set noon");
        server.runCommand("weather clear");
        server.runCommand("fill -12 -61 12 12 -45 12 minecraft:white_concrete");
        server.runCommand("fill -12 -61 -20 12 -61 11 minecraft:white_concrete");
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:bow");
        world.getConnection().waitForChunksRender();
        context.runOnClient(mc -> {
            // HUD (クロスヘア・ホットバー) を隠し、ピクセル比較にプレイヤーと背景だけが写るようにする。
            //? if >=26.3 {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            //?} else {
            /*mc.options.hideGui = true;
            *///?}
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.getInventory().setSelectedSlot(0);
            Config config = ClearAimClient.get().config();
            config.enabled = false;
            // 足元付近を「楕円の外」として検証できるよう、テストでは楕円を小さめにする (既定 0.16 / 0.26)。
            config.ellipseRadiusX = 0.12f;
            config.ellipseRadiusY = 0.16f;
            config.feather = 0.06f;
            config.remainingOpacity = 0.2f;
            config.forceFallbackRenderer = false;
            config.hideHeldItems = false;
        });
        context.waitTicks(20);
    }

    private Path shot(ClientGameTestContext context, String name) {
        return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(outputDir));
    }

    private void configure(ClientGameTestContext context, boolean enabled, TransparencyMode mode, TransitionMode transition) {
        context.runOnClient(mc -> {
            Config config = ClearAimClient.get().config();
            config.enabled = enabled;
            config.mode = mode;
            config.transition = transition;
            config.fadeInMs = 150;
            config.fadeOutMs = 250;
        });
    }

    private void runThirdPersonChecks(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        BufferedImage idleRef = ScreenshotAnalysis.load(shot(context, "00_idle_reference"));

        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(DRAW_TICKS);
        BufferedImage drawRef = ScreenshotAnalysis.load(shot(context, "01_drawing_reference_mod_off"));

        server.runCommand("effect give @a minecraft:invisibility infinite 0 true");
        context.waitTicks(SETTLE_TICKS);
        BufferedImage noPlayer = ScreenshotAnalysis.load(shot(context, "02_drawing_invisible_effect"));
        server.runCommand("effect clear @a");
        context.waitTicks(SETTLE_TICKS);

        boolean[][] silhouette = ScreenshotAnalysis.silhouette(drawRef, noPlayer);
        int[] bounds = ScreenshotAnalysis.bounds(silhouette);
        results.add("silhouette bounds (x0,y0,x1,y1) = " + bounds[0] + "," + bounds[1] + "," + bounds[2] + "," + bounds[3]);
        check(bounds[2] > bounds[0] && bounds[3] > bounds[1], "player silhouette was not found (is the player on screen?)");

        for (TransitionMode transition : TransitionMode.values()) {
            for (TransparencyMode mode : TransparencyMode.values()) {
                runModeCheck(context, transition, mode, drawRef, silhouette, bounds);
            }
        }

        runOverlapCheck(context, drawRef, noPlayer, silhouette, bounds);
        runOcclusionCheck(context, world, drawRef, silhouette);

        // フォールバック描画の記録 (目視確認用)。シルエットのどこかが変化していることだけを確認する。
        context.runOnClient(mc -> ClearAimClient.get().config().forceFallbackRenderer = true);
        configure(context, true, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.waitTicks(SETTLE_TICKS);
        BufferedImage fallback = ScreenshotAnalysis.load(shot(context, "40_fallback_full_body"));
        var fallbackStats = ScreenshotAnalysis.compare(fallback, drawRef, silhouette, null);
        results.add("fallback FULL_BODY silhouette: " + fallbackStats);
        check(fallbackStats.changedFraction() >= 0.3, "fallback renderer did not change the player body: " + fallbackStats);
        context.runOnClient(mc -> ClearAimClient.get().config().forceFallbackRenderer = false);

        // 使用解除: フェードアウト時間経過後、MOD 無効の待機姿勢とほぼ一致すること。
        configure(context, true, TransparencyMode.AIM_ELLIPSE, TransitionMode.FADE);
        context.waitTicks(SETTLE_TICKS);
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(250 / 50 + 10);
        server.runCommand("kill @e[type=minecraft:arrow]");
        context.waitTicks(SETTLE_TICKS);
        BufferedImage released = ScreenshotAnalysis.load(shot(context, "50_after_release"));
        var releaseStats = ScreenshotAnalysis.compare(released, idleRef, null, null);
        results.add("after release vs idle reference (whole screen): " + releaseStats);
        check(releaseStats.meanDiff() <= ScreenshotAnalysis.MAX_MEAN_DIFF_IDENTICAL
                        && releaseStats.changedFraction() <= ScreenshotAnalysis.MAX_CHANGED_FRACTION_IDENTICAL,
                "after release the screen should match the idle reference: " + releaseStats);
    }

    private void runModeCheck(ClientGameTestContext context, TransitionMode transition, TransparencyMode mode,
                              BufferedImage drawRef, boolean[][] silhouette, int[] bounds) {
        String tag = mode.name().toLowerCase(Locale.ROOT) + "_" + transition.name().toLowerCase(Locale.ROOT);
        // 前のモードの透明化が完全に戻るのを待ってから切り替える。
        configure(context, false, mode, transition);
        context.waitTicks(250 / 50 + SETTLE_TICKS);
        configure(context, true, mode, transition);
        if (transition == TransitionMode.FADE) {
            context.waitTicks(2);
            shot(context, "1x_" + tag + "_mid_fade");
            context.waitTicks(150 / 50 + SETTLE_TICKS);
        } else {
            context.waitTicks(2);
        }
        BufferedImage active = ScreenshotAnalysis.load(shot(context, "2x_" + tag + "_active"));
        FrameParams params = context.computeOnClient(mc -> RenderHooks.frame());
        String method = context.computeOnClient(mc -> ClearAimClient.get().renderer().currentMethod().name());
        results.add("[" + tag + "] renderer=" + method + " params=" + params);
        check(params != null, tag + ": transparency did not activate while drawing the bow");
        if (params == null) return;
        check("OFFSCREEN_COMPOSITE".equals(method), tag + ": expected offscreen compositing but got " + method);

        int width = active.getWidth(), height = active.getHeight();
        switch (mode) {
            case FULL_BODY -> {
                var stats = ScreenshotAnalysis.compare(active, drawRef, silhouette, null);
                results.add("[" + tag + "] whole silhouette: " + stats);
                expectTransparent(tag + " whole body", stats);
            }
            case AIM_ELLIPSE -> {
                float rx = params.radiusX(), ry = params.radiusY(), f = params.feather();
                var inside = ScreenshotAnalysis.compare(active, drawRef, silhouette, (x, y) ->
                        MaskMath.ellipseDistance(x + 0.5f, y + 0.5f, width, height, rx, ry) < 1f - f - 0.05f);
                var outside = ScreenshotAnalysis.compare(active, drawRef, silhouette, (x, y) ->
                        MaskMath.ellipseDistance(x + 0.5f, y + 0.5f, width, height, rx, ry) > 1f + f + 0.05f);
                results.add("[" + tag + "] inside ellipse: " + inside);
                results.add("[" + tag + "] outside ellipse (feet): " + outside);
                expectTransparent(tag + " inside ellipse", inside);
                expectUnchanged(tag + " outside ellipse", outside);
            }
            case UPPER_BODY -> {
                int top = bounds[1], bottom = bounds[3], span = bottom - top;
                // 画面上で頭〜胸 (外接矩形の上 40%) と、足首付近 (下 20%) を比較する。腰付近はぼかし幅があるので除外。
                var upper = ScreenshotAnalysis.compare(active, drawRef, silhouette, (x, y) -> y < top + span * 0.40);
                var legs = ScreenshotAnalysis.compare(active, drawRef, silhouette, (x, y) -> y > bottom - span * 0.20);
                results.add("[" + tag + "] upper body: " + upper);
                results.add("[" + tag + "] legs: " + legs);
                expectTransparent(tag + " upper body", upper);
                expectUnchanged(tag + " legs", legs);
            }
        }
    }

    /**
     * 不透明度 0.5 の全身モードが「不透明なプレイヤー画像と背景を 50% で混ぜた画像」と一致することを確かめる。
     * パーツを個別に半透明で描くと、胴体の裏の腕やスキン 2 層目の内側が透けてこの理想値からずれるので、
     * 重なり問題の自動検出になる。足元の影は透明化エフェクト側の画像に写らないので、シルエット下部は除外する。
     */
    private void runOverlapCheck(ClientGameTestContext context, BufferedImage drawRef, BufferedImage noPlayer,
                                 boolean[][] silhouette, int[] bounds) {
        context.runOnClient(mc -> ClearAimClient.get().config().remainingOpacity = 0.5f);
        configure(context, true, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.waitTicks(SETTLE_TICKS);
        BufferedImage half = ScreenshotAnalysis.load(shot(context, "30_full_body_opacity_050"));
        context.runOnClient(mc -> ClearAimClient.get().config().remainingOpacity = 0.2f);
        int limitY = bounds[1] + (int) ((bounds[3] - bounds[1]) * 0.85);
        var stats = ScreenshotAnalysis.compareToBlend(half, drawRef, noPlayer, 0.5, silhouette, (x, y) -> y < limitY);
        results.add("[overlap] opacity 0.5 vs ideal 50% blend: " + stats);
        check(stats.pixels() >= ScreenshotAnalysis.MIN_REGION_PIXELS && stats.meanDiff() <= ScreenshotAnalysis.MAX_MEAN_DIFF_BLEND,
                "opacity 0.5 should look like the opaque player blended at 50% (no inner parts showing through): " + stats);
    }

    /**
     * 手前のブロックに正しく隠れることを確かめる。カメラとプレイヤーの間に高さ 1 ブロックの柱を置き、
     * 柱に隠れている部分が MOD の有無で変わらない (合成したプレイヤーが柱の上に描かれない) ことを見る。
     * 柱を目の高さより低くしているのは、三人称カメラの衝突判定でカメラが前に押し出されないようにするため。
     */
    private void runOcclusionCheck(ClientGameTestContext context, TestSingleplayerContext world, BufferedImage drawRef, boolean[][] silhouette) {
        var server = world.getServer();
        server.runCommand("fill -1 -60 -1 1 -60 -1 minecraft:red_concrete");
        configure(context, false, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.waitTicks(SETTLE_TICKS);
        BufferedImage occludedRef = ScreenshotAnalysis.load(shot(context, "31_occluder_reference_mod_off"));
        configure(context, true, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.waitTicks(SETTLE_TICKS);
        BufferedImage occluded = ScreenshotAnalysis.load(shot(context, "32_occluder_full_body"));
        server.runCommand("fill -1 -60 -1 1 -60 -1 minecraft:air");
        context.waitTicks(SETTLE_TICKS);
        // 柱が写っている領域 = 柱なし/ありの参照画像の差。そのうちプレイヤーのシルエットと重なる部分を評価する。
        boolean[][] pillar = ScreenshotAnalysis.silhouette(drawRef, occludedRef);
        var stats = ScreenshotAnalysis.compare(occluded, occludedRef, pillar, (x, y) -> silhouette[y][x]);
        results.add("[occlusion] pillar in front of the player: " + stats);
        expectUnchanged("occluder in front of the player", stats);
    }

    /**
     * 設定画面: ワールド内で開くとプレビューが発動すること、ウィジェットのクリックで値が変わり即座に描画へ反映されること、
     * 「完了」でファイルに保存され「キャンセル」で元に戻ることを確かめる。
     */
    private void runConfigScreenCheck(ClientGameTestContext context) {
        Path configFile = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve(Config.FILE_NAME);
        configure(context, true, TransparencyMode.AIM_ELLIPSE, TransitionMode.INSTANT);
        context.setScreen(() -> new ConfigScreen(null));
        context.waitTicks(10);
        shot(context, "70_config_screen_triggers");
        boolean previewActive = context.computeOnClient(mc -> RenderHooks.frame() != null);
        check(previewActive, "config screen preview should activate transparency without aiming");

        context.clickScreenButton("config.clear_third_person_aim.page.appearance");
        context.waitTicks(2);
        shot(context, "71_config_screen_appearance");
        // 「見た目」ページの先頭行 (モード) を実際にマウスでクリックする。
        clickGui(context, 8 + 95, 44 + 10);
        context.waitTicks(3);
        TransparencyMode afterClick = context.computeOnClient(mc -> ClearAimClient.get().config().mode);
        TransparencyMode rendered = context.computeOnClient(mc -> RenderHooks.frame() == null ? null : RenderHooks.frame().mode());
        results.add("[config] mode after clicking the mode button: " + afterClick + " (rendered: " + rendered + ")");
        check(afterClick == TransparencyMode.FULL_BODY, "clicking the mode button should cycle AIM_ELLIPSE -> FULL_BODY, got " + afterClick);
        check(rendered == TransparencyMode.FULL_BODY, "mode change should be reflected in rendering immediately, got " + rendered);
        shot(context, "72_config_screen_mode_changed_preview");

        context.clickScreenButton("gui.done");
        context.waitTicks(2);
        Config saved = Config.load(configFile).config();
        results.add("[config] saved mode after Done: " + saved.mode);
        check(saved.mode == TransparencyMode.FULL_BODY, "Done should save the config file, saved mode = " + saved.mode);

        context.setScreen(() -> new ConfigScreen(null));
        context.waitTicks(2);
        context.clickScreenButton("config.clear_third_person_aim.page.appearance");
        context.waitTicks(2);
        clickGui(context, 8 + 95, 44 + 10);
        context.waitTicks(2);
        context.clickScreenButton("gui.cancel");
        context.waitTicks(2);
        TransparencyMode afterCancel = context.computeOnClient(mc -> ClearAimClient.get().config().mode);
        results.add("[config] mode after Cancel: " + afterCancel);
        check(afterCancel == TransparencyMode.FULL_BODY, "Cancel should restore the previous value, got " + afterCancel);
        boolean previewOff = context.computeOnClient(mc -> !ClearAimClient.get().isPreviewActive());
        check(previewOff, "preview should stop when the config screen closes");
        context.runOnClient(mc -> {
            ClearAimClient.get().config().mode = TransparencyMode.AIM_ELLIPSE;
            ClearAimClient.get().saveConfig();
        });
    }

    /** GUI 座標 (GUI スケール適用前) をクリックする。 */
    private static void clickGui(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
        context.getInput().setCursorPos((guiX + 0.5) * scale, (guiY + 0.5) * scale);
        //? if >=26.3 {
        context.getInput().pressMouse(1); // SDL_BUTTON_LEFT
        //?} else {
        /*context.getInput().pressMouse(0); // GLFW_MOUSE_BUTTON_LEFT
        *///?}
    }

    private void runFirstPersonCheck(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        configure(context, false, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(DRAW_TICKS);
        BufferedImage off = ScreenshotAnalysis.load(shot(context, "60_first_person_mod_off"));
        configure(context, true, TransparencyMode.FULL_BODY, TransitionMode.INSTANT);
        context.waitTicks(SETTLE_TICKS);
        BufferedImage on = ScreenshotAnalysis.load(shot(context, "61_first_person_mod_on"));
        context.getInput().releaseKey(options -> options.keyUse);
        var stats = ScreenshotAnalysis.compare(on, off, null, null);
        results.add("first person mod on vs off (whole screen): " + stats);
        check(stats.meanDiff() <= ScreenshotAnalysis.MAX_MEAN_DIFF_IDENTICAL
                        && stats.changedFraction() <= ScreenshotAnalysis.MAX_CHANGED_FRACTION_IDENTICAL,
                "first person rendering must not change: " + stats);
        boolean activeInFirstPerson = context.computeOnClient(mc -> RenderHooks.frame() != null);
        check(!activeInFirstPerson, "transparency must not activate in first person");
    }

    private void expectTransparent(String what, ScreenshotAnalysis.RegionStats stats) {
        check(stats.pixels() >= ScreenshotAnalysis.MIN_REGION_PIXELS, what + ": region too small (" + stats + ")");
        check(stats.changedFraction() >= ScreenshotAnalysis.MIN_CHANGED_FRACTION_TRANSPARENT,
                what + ": expected the player to become transparent (" + stats + ")");
    }

    private void expectUnchanged(String what, ScreenshotAnalysis.RegionStats stats) {
        check(stats.pixels() >= ScreenshotAnalysis.MIN_REGION_PIXELS, what + ": region too small (" + stats + ")");
        check(stats.meanDiff() <= ScreenshotAnalysis.MAX_MEAN_DIFF_UNCHANGED
                        && stats.changedFraction() <= ScreenshotAnalysis.MAX_CHANGED_FRACTION_UNCHANGED,
                what + ": expected no visible change (" + stats + ")");
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
            results.add("FAIL: " + message);
        }
    }

    private void writeResults() {
        results.add(failures.isEmpty() ? "RESULT: PASS" : "RESULT: FAIL (" + failures.size() + ")");
        try {
            Files.write(outputDir.resolve("results.txt"), results, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClearThirdPersonAim.LOGGER.error("Could not write gametest results", e);
        }
        results.forEach(line -> ClearThirdPersonAim.LOGGER.info("[gametest] {}", line));
    }
}
