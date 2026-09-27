package io.github.yoima.clearthirdpersonaim.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskMathTest {
    private static final float EPS = 1e-4f;
    private static final float W = 1920f;
    private static final float H = 1080f;

    private static float alpha(float x, float y, float rx, float ry, float feather) {
        return MaskMath.ellipseOpacity(x, y, W, H, rx, ry, feather, 0.2f, 1f);
    }

    @Test
    void centerIsMinimumOpacity() {
        assertEquals(0.2f, alpha(W / 2, H / 2, 0.16f, 0.26f, 0.06f), EPS);
        assertEquals(0f, MaskMath.ellipseDistance(W / 2, H / 2, W, H, 0.16f, 0.26f), EPS);
    }

    @Test
    void boundaryIsHalfway() {
        // 楕円境界 (d=1) は smoothstep のちょうど中間
        float rx = 0.16f;
        float x = W / 2 + rx * H;
        assertEquals(1f, MaskMath.ellipseDistance(x, H / 2, W, H, rx, 0.26f), EPS);
        assertEquals(0.6f, alpha(x, H / 2, rx, 0.26f, 0.06f), EPS);
        float ry = 0.26f;
        float y = H / 2 - ry * H;
        assertEquals(1f, MaskMath.ellipseDistance(W / 2, y, W, H, 0.16f, ry), EPS);
    }

    @Test
    void outsideIsFullyVisible() {
        // d >= 1 + feather で完全表示
        float x = W / 2 + 0.16f * H * 1.07f;
        assertEquals(1f, alpha(x, H / 2, 0.16f, 0.26f, 0.06f), EPS);
        assertEquals(1f, alpha(0, 0, 0.16f, 0.26f, 0.06f), EPS);
        // d <= 1 - feather で最小値
        float inside = W / 2 + 0.16f * H * 0.93f;
        assertEquals(0.2f, alpha(inside, H / 2, 0.16f, 0.26f, 0.06f), EPS);
    }

    @Test
    void featherIsMonotonic() {
        float prev = 0f;
        for (int i = 0; i <= 200; i++) {
            float d = i / 100f;
            float a = MaskMath.opacity(0.2f, 1f, MaskMath.ellipseWeight(d, 0.06f));
            assertTrue(a >= prev - 1e-6f, "opacity must not decrease with distance");
            prev = a;
        }
    }

    @Test
    void zeroFeatherIsHardEdge() {
        assertEquals(1f, MaskMath.ellipseWeight(0.999f, 0f), EPS);
        assertEquals(0f, MaskMath.ellipseWeight(1.001f, 0f), EPS);
    }

    @Test
    void aspectRatioIsCorrected() {
        // rx = ry のとき、画面上で真円になる: 横に r*H px と縦に r*H px 離れた点が同じ距離。
        float r = 0.2f;
        float dxPoint = MaskMath.ellipseDistance(W / 2 + r * H, H / 2, W, H, r, r);
        float dyPoint = MaskMath.ellipseDistance(W / 2, H / 2 + r * H, W, H, r, r);
        assertEquals(dxPoint, dyPoint, EPS);
        assertEquals(1f, dxPoint, EPS);
        // 縦長ウィンドウでも同様
        float w2 = 600f, h2 = 1000f;
        assertEquals(1f, MaskMath.ellipseDistance(w2 / 2 + r * h2, h2 / 2, w2, h2, r, r), EPS);
        assertEquals(1f, MaskMath.ellipseDistance(w2 / 2, h2 / 2 + r * h2, w2, h2, r, r), EPS);
    }

    @Test
    void progressScalesTransparency() {
        assertEquals(1f, MaskMath.ellipseOpacity(W / 2, H / 2, W, H, 0.16f, 0.26f, 0.06f, 0.2f, 0f), EPS);
        assertEquals(0.6f, MaskMath.ellipseOpacity(W / 2, H / 2, W, H, 0.16f, 0.26f, 0.06f, 0.2f, 0.5f), EPS);
        assertEquals(0f, MaskMath.ellipseOpacity(W / 2, H / 2, W, H, 0.16f, 0.26f, 0.06f, 0f, 1f), EPS);
    }

    @Test
    void distanceCompensation() {
        assertEquals(1f, MaskMath.distanceScale(MaskMath.REFERENCE_CAMERA_DISTANCE), EPS);
        // カメラが近い (半分の距離) ほど半径を広げる
        assertEquals(2f, MaskMath.distanceScale(2f), EPS);
        assertEquals(0.5f, MaskMath.distanceScale(8f), EPS);
        // 極端な値はクランプ
        assertEquals(MaskMath.MAX_DISTANCE_SCALE, MaskMath.distanceScale(0.1f), EPS);
        assertEquals(MaskMath.MAX_DISTANCE_SCALE, MaskMath.distanceScale(0f), EPS);
        assertEquals(MaskMath.MIN_DISTANCE_SCALE, MaskMath.distanceScale(100f), EPS);
    }

    @Test
    void controllerAppliesProjectionCompensation() {
        Config config = new Config();
        config.transition = TransitionMode.INSTANT;
        ClearAimController controller = new ClearAimController(config);
        controller.tick(new AimInput(Perspective.THIRD_PERSON_BACK, HeldItem.of("minecraft:bow", ItemKind.BOW), HeldItem.EMPTY, AimInput.Hand.MAIN));
        FrameParams near = controller.frame(0f, 2f, 70f, 70f, 1f, false);
        assertEquals(0.20f, near.radiusX(), EPS);
        assertEquals(0.30f, near.radiusY(), EPS);
        FrameParams far = controller.frame(0f, 8f, 70f, 70f, 1f, false);
        assertEquals(0.05f, far.radiusX(), EPS);
        assertEquals(0.075f, far.radiusY(), EPS);
        FrameParams bowZoom = controller.frame(0f, 4f, 59.5f, 70f, 1f, false);
        assertTrue(bowZoom.radiusX() > config.ellipseRadiusX);
        assertTrue(bowZoom.radiusY() > config.ellipseRadiusY);
        config.distanceCompensation = false;
        FrameParams off = controller.frame(0f, 2f, 59.5f, 70f, 1f, false);
        assertEquals(0.10f, off.radiusX(), EPS);
        assertEquals(0.15f, off.radiusY(), EPS);
    }

    @Test
    void upperBodyWeightSplitsAtWaist() {
        float waist = MaskMath.waistHeight(1f, false);
        assertEquals(0.75f, waist, EPS);
        assertEquals(0f, MaskMath.upperBodyWeight(0.3f, waist, MaskMath.WAIST_FEATHER), EPS);
        assertEquals(0.5f, MaskMath.upperBodyWeight(waist, waist, MaskMath.WAIST_FEATHER), EPS);
        assertEquals(1f, MaskMath.upperBodyWeight(1.5f, waist, MaskMath.WAIST_FEATHER), EPS);
        assertTrue(MaskMath.waistHeight(1f, true) < waist);
        assertEquals(1.5f, MaskMath.waistHeight(2f, false), EPS);
    }

    @Test
    void idleControllerProducesNoFrame() {
        ClearAimController controller = new ClearAimController(new Config());
        controller.tick(new AimInput(Perspective.THIRD_PERSON_BACK, HeldItem.EMPTY, HeldItem.EMPTY, null));
        assertEquals(null, controller.frame(0.5f, 4f, 70f, 70f, 1f, false));
    }

    @Test
    void previewForcesActivationOnlyInThirdPerson() {
        Config config = new Config();
        config.transition = TransitionMode.INSTANT;
        ClearAimController controller = new ClearAimController(config);
        AimInput idleBack = new AimInput(Perspective.THIRD_PERSON_BACK, HeldItem.EMPTY, HeldItem.EMPTY, null);
        controller.tick(idleBack, true);
        assertTrue(controller.isActive());
        controller.tick(new AimInput(Perspective.FIRST_PERSON, HeldItem.EMPTY, HeldItem.EMPTY, null), true);
        assertTrue(!controller.isActive());
        config.enabled = false;
        controller.tick(idleBack, true);
        assertTrue(!controller.isActive());
    }
}
