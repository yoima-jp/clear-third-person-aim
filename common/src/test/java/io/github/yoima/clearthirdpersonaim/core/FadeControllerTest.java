package io.github.yoima.clearthirdpersonaim.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FadeControllerTest {
    private static final float EPS = 1e-5f;

    @Test
    void instantJumpsWithoutIntermediateFrames() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.INSTANT, 150, 250);
        // 即時モードでは partialTick がいくつでも 1 (1 tick 分の補間による中間値を出さない)。
        assertEquals(1f, fade.progress(0f), EPS);
        assertEquals(1f, fade.progress(0.5f), EPS);
        fade.tick(false, TransitionMode.INSTANT, 150, 250);
        assertEquals(0f, fade.progress(0f), EPS);
        assertTrue(fade.isIdle());
    }

    @Test
    void fadeInTakesConfiguredTime() {
        FadeController fade = new FadeController();
        // 150ms = 3 tick
        fade.tick(true, TransitionMode.FADE, 150, 250);
        assertEquals(1f / 3f, fade.linear(), EPS);
        fade.tick(true, TransitionMode.FADE, 150, 250);
        fade.tick(true, TransitionMode.FADE, 150, 250);
        assertEquals(1f, fade.linear(), EPS);
        assertEquals(1f, fade.progress(1f), EPS);
        fade.tick(true, TransitionMode.FADE, 150, 250);
        assertEquals(1f, fade.linear(), EPS);
    }

    @Test
    void fadeOutUsesItsOwnDuration() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.INSTANT, 0, 0);
        // 250ms = 5 tick
        for (int i = 0; i < 4; i++) fade.tick(false, TransitionMode.FADE, 150, 250);
        assertEquals(0.2f, fade.linear(), EPS);
        assertFalse(fade.isIdle());
        fade.tick(false, TransitionMode.FADE, 150, 250);
        assertEquals(0f, fade.linear(), EPS);
        // previous はまだ 0.2 なので、補間中のフレームがある。次の tick で idle になる。
        assertFalse(fade.isIdle());
        fade.tick(false, TransitionMode.FADE, 150, 250);
        assertTrue(fade.isIdle());
    }

    @Test
    void reversalMidFadeIsContinuous() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.FADE, 200, 200); // 4 tick
        fade.tick(true, TransitionMode.FADE, 200, 200);
        float beforeReverse = fade.progress(1f);
        assertEquals(FadeController.easeInOut(0.5f), beforeReverse, EPS);

        // 途中で解除: 直前フレーム (partialTick=1) と、次 tick の partialTick=0 が一致すること (値が飛ばない)。
        fade.tick(false, TransitionMode.FADE, 200, 200);
        assertEquals(beforeReverse, fade.progress(0f), EPS);
        assertTrue(fade.progress(1f) < beforeReverse);

        // 再び構え直しても同様に連続。
        float beforeReverse2 = fade.progress(1f);
        fade.tick(true, TransitionMode.FADE, 200, 200);
        assertEquals(beforeReverse2, fade.progress(0f), EPS);
        assertTrue(fade.progress(1f) > beforeReverse2);
    }

    @Test
    void reversalWithDifferentDurationsIsContinuous() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.FADE, 150, 250);
        fade.tick(true, TransitionMode.FADE, 150, 250);
        float last = fade.progress(1f);
        fade.tick(false, TransitionMode.FADE, 150, 250);
        assertEquals(last, fade.progress(0f), EPS);
        // フェードアウトは 1 tick あたり 0.2 進む
        assertEquals(2f / 3f - 0.2f, fade.linear(), EPS);
    }

    @Test
    void partialTickInterpolatesSmoothly() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.FADE, 500, 500); // 1 tick で 0.1
        fade.tick(true, TransitionMode.FADE, 500, 500);
        // previous=0.1, current=0.2
        float prev = -1f;
        for (int i = 0; i <= 10; i++) {
            float p = fade.progress(i / 10f);
            assertTrue(p > prev, "progress must increase monotonically within a tick");
            prev = p;
        }
        assertEquals(FadeController.easeInOut(0.1f), fade.progress(0f), EPS);
        assertEquals(FadeController.easeInOut(0.15f), fade.progress(0.5f), EPS);
        assertEquals(FadeController.easeInOut(0.2f), fade.progress(1f), EPS);
        // 範囲外の partialTick はクランプ
        assertEquals(fade.progress(1f), fade.progress(3f), EPS);
        assertEquals(fade.progress(0f), fade.progress(-1f), EPS);
    }

    @Test
    void zeroDurationBehavesLikeInstant() {
        FadeController fade = new FadeController();
        fade.tick(true, TransitionMode.FADE, 0, 250);
        assertEquals(1f, fade.progress(0f), EPS);
        fade.tick(false, TransitionMode.FADE, 0, 0);
        assertEquals(0f, fade.progress(0.5f), EPS);
        assertTrue(fade.isIdle());
    }

    @Test
    void easingIsSymmetricAndBounded() {
        assertEquals(0f, FadeController.easeInOut(0f), EPS);
        assertEquals(0.5f, FadeController.easeInOut(0.5f), EPS);
        assertEquals(1f, FadeController.easeInOut(1f), EPS);
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            assertEquals(1f - FadeController.easeInOut(1f - t), FadeController.easeInOut(t), 1e-4f);
        }
    }
}
