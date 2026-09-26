package io.github.yoima.clearthirdpersonaim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @TempDir
    Path dir;

    @Test
    void defaultsMatchSpec() {
        Config c = new Config();
        assertTrue(c.enabled);
        assertTrue(c.triggerBowDrawing);
        assertTrue(c.triggerCrossbowCharging);
        assertTrue(c.triggerCrossbowLoaded);
        assertTrue(c.triggerTridentAiming);
        assertFalse(c.triggerThrowableHeld);
        assertTrue(c.extraItemsWhileUsing.isEmpty());
        assertTrue(c.extraItemsWhileHolding.isEmpty());
        assertFalse(c.activeInFrontView);
        assertEquals(TransparencyMode.AIM_ELLIPSE, c.mode);
        assertEquals(0.2f, c.remainingOpacity);
        assertEquals(0.16f, c.ellipseRadiusX);
        assertEquals(0.26f, c.ellipseRadiusY);
        assertEquals(0.06f, c.feather);
        assertTrue(c.distanceCompensation);
        assertFalse(c.hideHeldItems);
        assertEquals(TransitionMode.FADE, c.transition);
        assertEquals(150, c.fadeInMs);
        assertEquals(250, c.fadeOutMs);
    }

    @Test
    void outOfRangeValuesAreClamped() {
        List<String> warnings = new ArrayList<>();
        Config c = Config.fromJson("""
                {"remainingOpacity": 1.7, "ellipseRadiusX": -3, "ellipseRadiusY": 9,
                 "feather": 2, "fadeInMs": -10, "fadeOutMs": 999999}
                """, warnings::add);
        assertEquals(1f, c.remainingOpacity);
        assertEquals(Config.MIN_RADIUS, c.ellipseRadiusX);
        assertEquals(Config.MAX_RADIUS, c.ellipseRadiusY);
        assertEquals(Config.MAX_FEATHER, c.feather);
        assertEquals(0, c.fadeInMs);
        assertEquals(Config.MAX_FADE_MS, c.fadeOutMs);
        assertEquals(6, warnings.size());
    }

    @Test
    void unknownEnumFallsBackToDefault() {
        List<String> warnings = new ArrayList<>();
        Config c = Config.fromJson("{\"mode\": \"SIDEWAYS\", \"transition\": \"WARP\"}", warnings::add);
        assertEquals(TransparencyMode.AIM_ELLIPSE, c.mode);
        assertEquals(TransitionMode.FADE, c.transition);
        assertEquals(2, warnings.size());
    }

    @Test
    void missingKeysKeepDefaults() {
        Config c = Config.fromJson("{\"mode\": \"UPPER_BODY\"}", w -> { });
        assertEquals(TransparencyMode.UPPER_BODY, c.mode);
        assertEquals(0.2f, c.remainingOpacity);
        assertTrue(c.triggerBowDrawing);
    }

    @Test
    void itemIdsAreNormalized() {
        List<String> warnings = new ArrayList<>();
        Config c = Config.fromJson("""
                {"extraItemsWhileUsing": [" SomeMod:LongBow ", "bow", "bad id!", "", "somemod:longbow", null]}
                """, warnings::add);
        assertEquals(List.of("somemod:longbow", "minecraft:bow"), c.extraItemsWhileUsing);
        assertEquals(1, warnings.size());
        assertEquals(List.of("a:b", "minecraft:c", "x:y/z"), Config.parseIdList("a:b, c\n x:y/z,,"));
    }

    @Test
    void missingFileCreatesDefaults() throws Exception {
        Path file = dir.resolve("config").resolve(Config.FILE_NAME);
        Config.LoadResult result = Config.load(file);
        assertTrue(result.rewritten());
        assertTrue(Files.exists(file));
        assertEquals(new Config().toJson(), result.config().toJson());
    }

    @Test
    void brokenJsonRecoversWithDefaults() throws Exception {
        Path file = dir.resolve(Config.FILE_NAME);
        Files.writeString(file, "{ \"remainingOpacity\": 0.5, oops", StandardCharsets.UTF_8);
        Config.LoadResult result = Config.load(file);
        assertTrue(result.rewritten());
        assertFalse(result.warnings().isEmpty());
        assertEquals(new Config().toJson(), result.config().toJson());
        // 壊れたファイルはデフォルトで上書きされ、元の内容は .broken に退避される
        assertEquals(new Config().toJson(), Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(Files.readString(dir.resolve(Config.FILE_NAME + ".broken")).contains("oops"));
    }

    @Test
    void wrongTypesRecoverWithDefaults() throws Exception {
        Path file = dir.resolve(Config.FILE_NAME);
        Files.writeString(file, "{\"remainingOpacity\": \"very\"}", StandardCharsets.UTF_8);
        Config.LoadResult result = Config.load(file);
        assertTrue(result.rewritten());
        assertEquals(0.2f, result.config().remainingOpacity);

        Files.writeString(file, "[1, 2, 3]", StandardCharsets.UTF_8);
        assertTrue(Config.load(file).rewritten());
    }

    @Test
    void clampedValuesAreWrittenBack() throws Exception {
        Path file = dir.resolve(Config.FILE_NAME);
        Files.writeString(file, "{\"remainingOpacity\": 3}", StandardCharsets.UTF_8);
        Config.LoadResult result = Config.load(file);
        assertFalse(result.rewritten());
        assertEquals(1, result.warnings().size());
        assertEquals(1f, Config.load(file).config().remainingOpacity);
        assertTrue(Config.load(file).warnings().isEmpty());
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        Config c = new Config();
        c.enabled = false;
        c.triggerBowDrawing = false;
        c.triggerCrossbowCharging = false;
        c.triggerCrossbowLoaded = false;
        c.triggerTridentAiming = false;
        c.triggerThrowableHeld = true;
        c.extraItemsWhileUsing = new ArrayList<>(List.of("somemod:longbow"));
        c.extraItemsWhileHolding = new ArrayList<>(List.of("othermod:gun", "minecraft:ender_pearl"));
        c.activeInFrontView = true;
        c.mode = TransparencyMode.UPPER_BODY;
        c.remainingOpacity = 0.35f;
        c.ellipseRadiusX = 0.3f;
        c.ellipseRadiusY = 0.4f;
        c.feather = 0.1f;
        c.distanceCompensation = false;
        c.hideHeldItems = true;
        c.transition = TransitionMode.INSTANT;
        c.fadeInMs = 400;
        c.fadeOutMs = 800;
        c.forceFallbackRenderer = true;

        Path file = dir.resolve(Config.FILE_NAME);
        c.save(file);
        Config.LoadResult result = Config.load(file);
        assertFalse(result.rewritten());
        assertTrue(result.warnings().isEmpty());
        assertEquals(c.toJson(), result.config().toJson());
    }

    @Test
    void copyFromRestoresAllFields() {
        Config original = new Config();
        Config edited = original.copy();
        edited.mode = TransparencyMode.FULL_BODY;
        edited.remainingOpacity = 0.9f;
        edited.extraItemsWhileHolding = new ArrayList<>(List.of("a:b"));
        edited.forceFallbackRenderer = true;
        edited.copyFrom(original);
        assertEquals(original.toJson(), edited.toJson());
    }
}
