package io.github.yoima.clearthirdpersonaim.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimStateTest {
    private static final HeldItem BOW = HeldItem.of("minecraft:bow", ItemKind.BOW);
    private static final HeldItem CROSSBOW_EMPTY = new HeldItem("minecraft:crossbow", ItemKind.CROSSBOW, false);
    private static final HeldItem CROSSBOW_LOADED = new HeldItem("minecraft:crossbow", ItemKind.CROSSBOW, true);
    private static final HeldItem TRIDENT = HeldItem.of("minecraft:trident", ItemKind.TRIDENT);
    private static final HeldItem SNOWBALL = HeldItem.of("minecraft:snowball", ItemKind.THROWABLE);
    private static final HeldItem LONGBOW = HeldItem.of("somemod:longbow", ItemKind.OTHER);
    private static final HeldItem SWORD = HeldItem.of("minecraft:diamond_sword", ItemKind.OTHER);

    private static AimInput back(HeldItem main, HeldItem off, AimInput.Hand using) {
        return new AimInput(Perspective.THIRD_PERSON_BACK, main, off, using);
    }

    private static AimInput usingMain(HeldItem main) {
        return back(main, HeldItem.EMPTY, AimInput.Hand.MAIN);
    }

    private static AimInput holdingMain(HeldItem main) {
        return back(main, HeldItem.EMPTY, null);
    }

    @Test
    void bowDrawingTrigger() {
        Config c = new Config();
        assertTrue(AimState.shouldActivate(c, usingMain(BOW)));
        assertFalse(AimState.shouldActivate(c, holdingMain(BOW)), "holding a bow without drawing must not trigger");
        c.triggerBowDrawing = false;
        assertFalse(AimState.shouldActivate(c, usingMain(BOW)));
    }

    @Test
    void crossbowChargingTrigger() {
        Config c = new Config();
        assertTrue(AimState.shouldActivate(c, usingMain(CROSSBOW_EMPTY)));
        c.triggerCrossbowCharging = false;
        assertFalse(AimState.shouldActivate(c, usingMain(CROSSBOW_EMPTY)));
        assertFalse(AimState.shouldActivate(c, holdingMain(CROSSBOW_EMPTY)));
    }

    @Test
    void crossbowLoadedTrigger() {
        Config c = new Config();
        assertTrue(AimState.shouldActivate(c, holdingMain(CROSSBOW_LOADED)));
        // オフハンドの装填済みクロスボウでも発動する
        assertTrue(AimState.shouldActivate(c, back(SWORD, CROSSBOW_LOADED, null)));
        c.triggerCrossbowLoaded = false;
        assertFalse(AimState.shouldActivate(c, holdingMain(CROSSBOW_LOADED)));
    }

    @Test
    void tridentTrigger() {
        Config c = new Config();
        assertTrue(AimState.shouldActivate(c, usingMain(TRIDENT)));
        assertFalse(AimState.shouldActivate(c, holdingMain(TRIDENT)));
        c.triggerTridentAiming = false;
        assertFalse(AimState.shouldActivate(c, usingMain(TRIDENT)));
    }

    @Test
    void throwableTriggerIsOffByDefault() {
        Config c = new Config();
        assertFalse(AimState.shouldActivate(c, holdingMain(SNOWBALL)));
        c.triggerThrowableHeld = true;
        assertTrue(AimState.shouldActivate(c, holdingMain(SNOWBALL)));
        assertTrue(AimState.shouldActivate(c, back(HeldItem.EMPTY, SNOWBALL, null)));
    }

    @Test
    void usingOtherHandItemUsesThatHand() {
        Config c = new Config();
        // メインに剣、オフハンドで弓を引いている
        assertTrue(AimState.shouldActivate(c, back(SWORD, BOW, AimInput.Hand.OFF)));
        // メインで剣を使用中 (ガード等)、オフハンドの弓は引いていない
        assertFalse(AimState.shouldActivate(c, back(SWORD, BOW, AimInput.Hand.MAIN)));
    }

    @Test
    void extraItemWhileUsing() {
        Config c = new Config();
        assertFalse(AimState.shouldActivate(c, usingMain(LONGBOW)));
        c.extraItemsWhileUsing = List.of("somemod:longbow");
        assertTrue(AimState.shouldActivate(c, usingMain(LONGBOW)));
        assertFalse(AimState.shouldActivate(c, holdingMain(LONGBOW)), "while-using entries must not trigger when only held");
    }

    @Test
    void extraItemWhileHolding() {
        Config c = new Config();
        c.extraItemsWhileHolding = List.of("somemod:longbow");
        assertTrue(AimState.shouldActivate(c, holdingMain(LONGBOW)));
        assertTrue(AimState.shouldActivate(c, usingMain(LONGBOW)));
        assertTrue(AimState.shouldActivate(c, back(HeldItem.EMPTY, LONGBOW, null)));
        assertFalse(AimState.shouldActivate(c, holdingMain(SWORD)));
    }

    @Test
    void firstPersonNeverActivates() {
        Config c = new Config();
        c.activeInFrontView = true;
        c.triggerThrowableHeld = true;
        AimInput input = new AimInput(Perspective.FIRST_PERSON, BOW, SNOWBALL, AimInput.Hand.MAIN);
        assertFalse(AimState.shouldActivate(c, input));
    }

    @Test
    void frontViewDisabledByDefault() {
        Config c = new Config();
        AimInput input = new AimInput(Perspective.THIRD_PERSON_FRONT, BOW, HeldItem.EMPTY, AimInput.Hand.MAIN);
        assertFalse(AimState.shouldActivate(c, input));
        c.activeInFrontView = true;
        assertTrue(AimState.shouldActivate(c, input));
    }

    @Test
    void globalToggleDisablesEverything() {
        Config c = new Config();
        c.enabled = false;
        assertFalse(AimState.shouldActivate(c, usingMain(BOW)));
        assertFalse(AimState.shouldActivate(c, holdingMain(CROSSBOW_LOADED)));
    }

    @Test
    void emptyHandsNeverActivate() {
        Config c = new Config();
        c.triggerThrowableHeld = true;
        assertFalse(AimState.shouldActivate(c, back(HeldItem.EMPTY, HeldItem.EMPTY, null)));
        assertFalse(AimState.shouldActivate(c, back(HeldItem.EMPTY, HeldItem.EMPTY, AimInput.Hand.MAIN)));
    }
}
