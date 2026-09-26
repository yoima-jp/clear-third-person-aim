package io.github.yoima.clearthirdpersonaim.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yoima.clearthirdpersonaim.ClearThirdPersonAim;
import io.github.yoima.clearthirdpersonaim.core.AimTransparencyRenderer;
import io.github.yoima.clearthirdpersonaim.core.ClearAimController;
import io.github.yoima.clearthirdpersonaim.core.Config;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.Perspective;
import io.github.yoima.clearthirdpersonaim.render.RenderMethodSelector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ローダーに依存しないクライアント側の中心。Fabric / NeoForge のエントリポイントは
 * {@link #init} とクライアント tick・キー登録の橋渡しだけを行う。
 */
public final class ClearAimClient {
    private static ClearAimClient instance;

    private final Path configPath;
    private final Config config;
    private final ClearAimController controller;
    private final RenderMethodSelector renderer = new RenderMethodSelector();
    private final KeyMapping toggleKey;
    /** 設定画面のプレビュー中は、構えていなくても透明化を発動させる。 */
    private boolean previewActive;
    /** クライアント tick の通算回数。自作テストドライバが「n tick 待つ」ために読む (描画スレッド以外からも読むので volatile)。 */
    private volatile long clientTicks;

    private ClearAimClient(Path configDir) {
        this.configPath = configDir.resolve(Config.FILE_NAME);
        Config.LoadResult result = Config.load(configPath);
        for (String warning : result.warnings()) ClearThirdPersonAim.LOGGER.warn("[config] {}", warning);
        this.config = result.config();
        this.controller = new ClearAimController(config);
        this.toggleKey = createToggleKey();
    }

    public static ClearAimClient init(Path configDir) {
        if (instance == null) instance = new ClearAimClient(configDir);
        return instance;
    }

    /** 初期化前 (MOD 読み込み順の都合で描画フックが先に呼ばれた場合など) は null。 */
    public static ClearAimClient get() {
        return instance;
    }

    public Config config() {
        return config;
    }

    public ClearAimController controller() {
        return controller;
    }

    public AimTransparencyRenderer renderer() {
        return renderer;
    }

    public RenderMethodSelector rendererSelector() {
        return renderer;
    }

    public KeyMapping toggleKey() {
        return toggleKey;
    }

    //? if >=1.21.9 {
    // Fabric はバニラの登録処理をそのまま使い、NeoForge は RegisterKeyMappingsEvent#registerCategory で登録する
    // (NeoForge ではバニラの Category.register を使うと並び順の管理が二重になる)。
    //? if fabric {
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(ClearThirdPersonAim.id("keys"));
    //?} else {
    /*public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(ClearThirdPersonAim.id("keys"));
    *///?}
    //?}

    // 既定では未割り当て。ユーザーが操作設定で割り当てる。
    private static KeyMapping createToggleKey() {
        int unbound = InputConstants.UNKNOWN.getValue();
        //? if >=1.21.9 {
        return new KeyMapping("key.clear_third_person_aim.toggle", unbound, KEY_CATEGORY);
        //?} else {
        /*return new KeyMapping("key.clear_third_person_aim.toggle", unbound, "key.categories.clear_third_person_aim");
        *///?}
    }

    /** クライアント tick の終わりに呼ぶ。 */
    public void tick(Minecraft minecraft) {
        clientTicks++;
        while (toggleKey.consumeClick()) toggleEnabled(minecraft);
        var input = AimInputs.capture(minecraft);
        // 一人称へ切り替えた瞬間はモデル自体が描かれなくなる。フェードの残りを持ち越して、
        // 三人称に戻した瞬間に透けた状態から始まらないようにリセットする。
        if (input != null && input.perspective() == Perspective.FIRST_PERSON) controller.resetFade();
        controller.tick(input, previewActive);
    }

    public long clientTicks() {
        return clientTicks;
    }

    public void setPreviewActive(boolean previewActive) {
        this.previewActive = previewActive;
    }

    public boolean isPreviewActive() {
        return previewActive;
    }

    private void toggleEnabled(Minecraft minecraft) {
        config.enabled = !config.enabled;
        saveConfig();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            Component message = Component.translatable(config.enabled
                    ? "message.clear_third_person_aim.enabled" : "message.clear_third_person_aim.disabled");
            //? if >=26.1 {
            player.sendOverlayMessage(message);
            //?} else {
            /*player.displayClientMessage(message, true);
            *///?}
        }
    }

    public void saveConfig() {
        try {
            config.save(configPath);
        } catch (IOException e) {
            ClearThirdPersonAim.LOGGER.error("Could not save config {}", configPath, e);
        }
    }

    /**
     * このフレームの透明化パラメータ。透明化が不要なら null で、呼び出し側はバニラの描画パスをそのまま通す。
     */
    public FrameParams frameParams(Minecraft minecraft, float partialTick) {
        LocalPlayer player = minecraft.player;
        if (player == null) return null;
        if (minecraft.options.getCameraType().isFirstPerson()) return null;
        //? if >=26.2 {
        Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
        //?} elif >=1.21.2 {
        /*Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().position();
        *///?} else {
        /*Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        *///?}
        Vec3 eyePos = player.getEyePosition(partialTick);
        float distance = (float) cameraPos.distanceTo(eyePos);
        return controller.frame(partialTick, distance, player.getScale(), player.isCrouching());
    }
}
