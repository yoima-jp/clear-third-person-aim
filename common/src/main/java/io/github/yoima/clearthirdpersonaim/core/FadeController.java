package io.github.yoima.clearthirdpersonaim.core;

/**
 * 透明化の進行度 (0 = 通常表示, 1 = 完全に透明化した状態) を管理する。
 *
 * <p>進行度は線形値として tick ごとに進め、描画時に partialTick で線形補間してからイージングを掛ける。
 * 線形値を状態として持つので、フェード途中で構え直し/解除しても現在の線形値からそのまま逆向きに進み、
 * イージング後の値も連続する (値が飛ばない)。イージング後の値を状態にすると、反転時に
 * 逆関数を取る必要があり誤差で段差が出るため採用しない。
 *
 * <p>MC のゲームループと同じく 1 tick = 50ms として時間を換算する。
 */
public final class FadeController {
    public static final float TICK_MS = 50f;
    private static final float SNAP_EPSILON = 1e-4f;

    private float previous;
    private float current;

    /**
     * 1 tick 進める。
     *
     * @param active    このtick で透明化を発動すべきか
     * @param mode      切り替え方
     * @param fadeInMs  0→1 にかける時間
     * @param fadeOutMs 1→0 にかける時間
     */
    public void tick(boolean active, TransitionMode mode, int fadeInMs, int fadeOutMs) {
        float target = active ? 1f : 0f;
        int duration = active ? fadeInMs : fadeOutMs;
        if (mode == TransitionMode.INSTANT || duration <= 0) {
            // 即時切り替えでは previous も揃えて、partialTick 補間による 1 tick 分の中間値を出さない。
            previous = target;
            current = target;
            return;
        }
        previous = current;
        float step = TICK_MS / duration;
        if (current < target) {
            current = Math.min(target, current + step);
        } else if (current > target) {
            current = Math.max(target, current - step);
        }
        // 浮動小数の累積誤差で 0 や 1 に届かず、idle にならないまま追加描画が続くのを防ぐ。
        if (Math.abs(current - target) < SNAP_EPSILON) current = target;
    }

    /** 描画時の進行度 (イージング済み)。 */
    public float progress(float partialTick) {
        float t = Math.max(0f, Math.min(1f, partialTick));
        return easeInOut(previous + (current - previous) * t);
    }

    /** 線形進行度 (テスト・デバッグ用)。 */
    public float linear() {
        return current;
    }

    /**
     * 追加の描画処理が不要か。完全に通常表示で、かつ次のフレームでも通常表示のままであることを表す。
     * これが true の間はバニラと同じ描画パスを通す (性能要件 4.3)。
     */
    public boolean isIdle() {
        return current == 0f && previous == 0f;
    }

    public void reset() {
        previous = 0f;
        current = 0f;
    }

    /** ease-in-out (二次)。t=0.5 で速度最大、両端で速度 0 になり、開始・終了がなめらかに見える。 */
    public static float easeInOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }
}
