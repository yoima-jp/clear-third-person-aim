package io.github.yoima.clearthirdpersonaim.core;

/**
 * トリガー判定・フェード・設定をまとめ、描画層に渡す {@link FrameParams} を作る。
 * MC 側はクライアント tick で {@link #tick} を、描画時に {@link #frame} を呼ぶだけでよい。
 */
public final class ClearAimController {
    private final Config config;
    private final FadeController fade = new FadeController();
    private boolean active;

    public ClearAimController(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }

    /**
     * @param input ワールドにいない・プレイヤーがいないときは null を渡す (即座に通常表示へ戻す)
     */
    public void tick(AimInput input) {
        tick(input, false);
    }

    /**
     * @param forcePreview 設定画面のプレビュー中。武器を構えていなくても発動させ、設定値の見た目をその場で確認できるようにする。
     *                     一人称・全体スイッチ OFF のときはプレビューでも発動しない (実際の見た目と食い違わないように)。
     */
    public void tick(AimInput input, boolean forcePreview) {
        if (input == null) {
            active = false;
            fade.reset();
            return;
        }
        boolean previewApplies = forcePreview && config.enabled && input.perspective() != Perspective.FIRST_PERSON;
        active = previewApplies || AimState.shouldActivate(config, input);
        fade.tick(active, config.transition, config.fadeInMs, config.fadeOutMs);
    }

    public boolean isActive() {
        return active;
    }

    public FadeController fade() {
        return fade;
    }

    /** 一人称へ切り替えた瞬間などに、フェードを待たず即座に通常表示へ戻す。 */
    public void resetFade() {
        fade.reset();
    }

    /**
     * このフレームの透明化パラメータ。追加の描画が不要なら null (バニラと同じ描画パスを通す)。
     *
     * @param cameraDistance 三人称カメラとプレイヤーの距離 (ブロック)
     * @param currentFovDegrees 実際にワールド描画に使う視野角 (度)
     * @param baseFovDegrees    ユーザー設定の視野角 (度)
     * @param playerScale    プレイヤーのスケール属性 (通常 1)
     * @param crouching      しゃがんでいるか
     */
    public FrameParams frame(float partialTick, float cameraDistance, float currentFovDegrees, float baseFovDegrees,
                             float playerScale, boolean crouching) {
        if (fade.isIdle()) return null;
        float progress = fade.progress(partialTick);
        if (progress <= 0f) return null;
        float scale = config.distanceCompensation ? MaskMath.projectionScale(cameraDistance, currentFovDegrees, baseFovDegrees) : 1f;
        return new FrameParams(config.mode, progress, config.remainingOpacity,
                config.ellipseRadiusX * scale, config.ellipseRadiusY * scale, config.feather,
                MaskMath.waistHeight(playerScale, crouching), config.hideHeldItems);
    }
}
