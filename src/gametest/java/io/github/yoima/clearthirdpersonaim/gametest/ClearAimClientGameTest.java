package io.github.yoima.clearthirdpersonaim.gametest;

import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
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
