package io.github.yoima.clearthirdpersonaim.client.gui;

import io.github.yoima.clearthirdpersonaim.client.ClearAimClient;
import io.github.yoima.clearthirdpersonaim.core.Config;
import io.github.yoima.clearthirdpersonaim.core.FrameParams;
import io.github.yoima.clearthirdpersonaim.core.TransitionMode;
import io.github.yoima.clearthirdpersonaim.core.TransparencyMode;
import io.github.yoima.clearthirdpersonaim.render.RenderHooks;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * バニラのウィジェットだけで作った設定画面。
 *
 * <p>ウィジェットは {@link Config} のフィールドを直接書き換えるので、変更は次のフレームから描画に反映される。
 * ワールド内で開いたときはプレビューを有効にし、背面三人称に切り替え、背景をぼかさず、ゲームも止めない
 * (フェードの動きまで確認できるように)。ウィジェットは左端の列にまとめ、画面中央 (クロスヘアと自分のモデル) を空けておく。
 * 「完了」で保存、「キャンセル」/ Esc 以外で閉じたときも保存し、「キャンセル」だけは開いた時点の値に戻す。
 */
public final class ConfigScreen extends Screen {
    private enum Page {
        TRIGGERS("triggers"),
        APPEARANCE("appearance"),
        TRANSITION("transition");

        final String key;

        Page(String key) {
            this.key = key;
        }
    }

    private static final int COLUMN_X = 8;
    private static final int COLUMN_WIDTH = 190;
    private static final int ROW_HEIGHT = 19;
    private static final int MAX_ROW_SPACING = 22;
    /** 1 ページの最大行数 (見た目ページの 9 行)。高さ 240 でもフッターと重ならないようにする。 */
    private static final int MAX_ROWS = 9;
    private static final int FIRST_ROW_Y = 40;

    private final Screen parent;
    private final Config config;
    private final Config original;
    private Page page = Page.TRIGGERS;
    private CameraType cameraBeforePreview;
    private boolean cancelled;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("config.clear_third_person_aim.title"));
        this.parent = parent;
        this.config = ClearAimClient.get().config();
        this.original = config.copy();
    }

    private boolean inWorld() {
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }

    @Override
    protected void init() {
        if (inWorld()) {
            ClearAimClient.get().setPreviewActive(true);
            if (cameraBeforePreview == null) {
                cameraBeforePreview = minecraft.options.getCameraType();
                // 一人称では自分のモデルが描かれずプレビューにならないので、開いている間だけ背面三人称にする。
                if (cameraBeforePreview.isFirstPerson()) minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }

        int tabWidth = (COLUMN_WIDTH - 4) / 3;
        for (Page p : Page.values()) {
            Button tab = Button.builder(Component.translatable("config.clear_third_person_aim.page." + p.key), b -> {
                page = p;
                rebuildWidgets();
            }).bounds(COLUMN_X + p.ordinal() * (tabWidth + 2), 18, tabWidth, ROW_HEIGHT).build();
            tab.active = p != page;
            addRenderableWidget(tab);
        }

        Rows rows = new Rows();
        switch (page) {
            case TRIGGERS -> {
                rows.add(toggle("enabled", () -> config.enabled, v -> config.enabled = v));
                rows.add(toggle("trigger_bow", () -> config.triggerBowDrawing, v -> config.triggerBowDrawing = v));
                rows.add(toggle("trigger_crossbow_charging", () -> config.triggerCrossbowCharging, v -> config.triggerCrossbowCharging = v));
                rows.add(toggle("trigger_crossbow_loaded", () -> config.triggerCrossbowLoaded, v -> config.triggerCrossbowLoaded = v));
                rows.add(toggle("trigger_trident", () -> config.triggerTridentAiming, v -> config.triggerTridentAiming = v));
                rows.add(toggle("trigger_throwable", () -> config.triggerThrowableHeld, v -> config.triggerThrowableHeld = v));
                rows.add(toggle("front_view", () -> config.activeInFrontView, v -> config.activeInFrontView = v));
            }
            case APPEARANCE -> {
                rows.add(cycle("mode", List.of(TransparencyMode.values()), config.mode, v -> config.mode = v,
                        v -> Component.translatable("config.clear_third_person_aim.mode." + v.name().toLowerCase(Locale.ROOT))));
                rows.add(slider("remaining_opacity", 0, 1, 0.01, () -> config.remainingOpacity, v -> config.remainingOpacity = (float) v, ConfigScreen::percent));
                rows.add(slider("radius_x", Config.MIN_RADIUS, Config.MAX_RADIUS, 0.01, () -> config.ellipseRadiusX, v -> config.ellipseRadiusX = (float) v, ConfigScreen::decimal));
                rows.add(slider("radius_y", Config.MIN_RADIUS, Config.MAX_RADIUS, 0.01, () -> config.ellipseRadiusY, v -> config.ellipseRadiusY = (float) v, ConfigScreen::decimal));
                rows.add(slider("feather", 0, Config.MAX_FEATHER, 0.01, () -> config.feather, v -> config.feather = (float) v, ConfigScreen::decimal));
                rows.add(toggle("distance_compensation", () -> config.distanceCompensation, v -> config.distanceCompensation = v));
                rows.add(toggle("hide_held_items", () -> config.hideHeldItems, v -> config.hideHeldItems = v));
                rows.add(toggle("show_crosshair", () -> config.showCrosshair, v -> config.showCrosshair = v));
                rows.add(toggle("force_fallback", () -> config.forceFallbackRenderer, v -> config.forceFallbackRenderer = v));
            }
            case TRANSITION -> {
                rows.add(cycle("transition", List.of(TransitionMode.values()), config.transition, v -> config.transition = v,
                        v -> Component.translatable("config.clear_third_person_aim.transition." + v.name().toLowerCase(Locale.ROOT))));
                rows.add(slider("fade_in", 0, Config.MAX_FADE_MS, 10, () -> config.fadeInMs, v -> config.fadeInMs = (int) Math.round(v), ConfigScreen::millis));
                rows.add(slider("fade_out", 0, Config.MAX_FADE_MS, 10, () -> config.fadeOutMs, v -> config.fadeOutMs = (int) Math.round(v), ConfigScreen::millis));
                rows.label(optionName("extra_using"));
                rows.add(itemList("extra_using", () -> config.extraItemsWhileUsing, v -> config.extraItemsWhileUsing = v));
                rows.label(optionName("extra_holding"));
                rows.add(itemList("extra_holding", () -> config.extraItemsWhileHolding, v -> config.extraItemsWhileHolding = v));
            }
        }

        int buttonWidth = (COLUMN_WIDTH - 4) / 3;
        int buttonY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("config.clear_third_person_aim.reset"), b -> {
            config.copyFrom(new Config());
            rebuildWidgets();
        }).bounds(COLUMN_X, buttonY, buttonWidth, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
            cancelled = true;
            onClose();
        }).bounds(COLUMN_X + buttonWidth + 2, buttonY, buttonWidth, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(COLUMN_X + (buttonWidth + 2) * 2, buttonY, buttonWidth, ROW_HEIGHT).build());
    }

    /** 1 列に上から並べるための行カウンタ。入力欄の上には項目名のラベルを置く (EditBox は自分の名前を表示しないため)。 */
    private final class Rows {
        private int y = FIRST_ROW_Y;
        // 最小の GUI 高さ (240) でもフッターのボタンと重ならないよう、画面の高さから行間を決める。
        private final int spacing = Math.max(ROW_HEIGHT, Math.min(MAX_ROW_SPACING, (height - 28 - FIRST_ROW_Y) / MAX_ROWS));

        void add(net.minecraft.client.gui.components.AbstractWidget widget) {
            widget.setX(COLUMN_X);
            widget.setY(y);
            widget.setWidth(COLUMN_WIDTH);
            addRenderableWidget(widget);
            y += spacing;
        }

        void label(Component text) {
            labels.add(new Label(y + 1, text));
            y += 11;
        }
    }

    private record Label(int y, Component text) {
    }

    private final List<Label> labels = new java.util.ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        labels.clear();
        super.rebuildWidgets();
    }

    private static Component optionName(String key) {
        return Component.translatable("config.clear_third_person_aim.option." + key);
    }

    private static Tooltip tooltip(String key) {
        return Tooltip.create(Component.translatable("config.clear_third_person_aim.option." + key + ".tooltip"));
    }

    private CycleButton<Boolean> toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(getter.get())
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, optionName(key), (b, value) -> setter.accept(value));
        button.setTooltip(tooltip(key));
        return button;
    }

    private <T> CycleButton<T> cycle(String key, List<T> values, T current, Consumer<T> setter, java.util.function.Function<T, Component> names) {
        //? if >=1.21.11 {
        CycleButton<T> button = CycleButton.builder(names, current).withValues(values)
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, optionName(key), (b, value) -> setter.accept(value));
        //?} else {
        /*CycleButton<T> button = CycleButton.builder(names).withValues(values).withInitialValue(current)
                .create(0, 0, COLUMN_WIDTH, ROW_HEIGHT, optionName(key), (b, value) -> setter.accept(value));
        *///?}
        button.setTooltip(tooltip(key));
        return button;
    }

    private Slider slider(String key, double min, double max, double step, Supplier<Number> getter, java.util.function.DoubleConsumer setter,
                          DoubleFunction<String> format) {
        Slider slider = new Slider(optionName(key), min, max, step, getter.get().doubleValue(), setter, format);
        slider.setTooltip(tooltip(key));
        return slider;
    }

    private EditBox itemList(String key, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        EditBox box = new EditBox(font, 0, 0, COLUMN_WIDTH, ROW_HEIGHT, optionName(key));
        box.setMaxLength(1024);
        box.setValue(String.join(", ", getter.get()));
        // 入力途中の不正な ID は捨てられるが、入力欄の文字列はそのまま残す (打ち直しを強いない)。
        box.setResponder(text -> setter.accept(Config.parseIdList(text)));
        box.setTooltip(tooltip(key));
        return box;
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String decimal(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String millis(double v) {
        return Math.round(v) + " ms";
    }

    /** 値の範囲とステップを持つ汎用スライダー。AbstractSliderButton は 0..1 の値で動くので換算する。 */
    private static final class Slider extends AbstractSliderButton {
        private final Component name;
        private final double min;
        private final double max;
        private final double step;
        private final java.util.function.DoubleConsumer setter;
        private final DoubleFunction<String> format;

        Slider(Component name, double min, double max, double step, double initial, java.util.function.DoubleConsumer setter, DoubleFunction<String> format) {
            super(0, 0, COLUMN_WIDTH, ROW_HEIGHT, Component.empty(), (initial - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.format = format;
            updateMessage();
        }

        private double actual() {
            double raw = min + value * (max - min);
            double snapped = Math.round(raw / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("config.clear_third_person_aim.slider", name, format.apply(actual())));
        }

        @Override
        protected void applyValue() {
            setter.accept(actual());
        }
    }

    @Override
    public boolean isPauseScreen() {
        // プレビュー中にフェードや姿勢の変化を確認できるよう、ワールド内ではゲームを止めない。
        return !inWorld();
    }

    @Override
    public void onClose() {
        //? if >=26.2 {
        minecraft.gui.setScreen(parent);
        //?} else {
        /*minecraft.setScreen(parent);
        *///?}
    }

    /**
     * 画面が外されたとき (完了・キャンセル・Esc のほか、切断などで別の画面に置き換えられたときも) に呼ばれる。
     * 後片付けをここに集めて、どの閉じ方でもプレビューとカメラが元に戻り、設定が保存されるようにする。
     */
    @Override
    public void removed() {
        super.removed();
        if (cancelled) config.copyFrom(original);
        ClearAimClient.get().saveConfig();
        ClearAimClient.get().setPreviewActive(false);
        if (cameraBeforePreview != null) {
            minecraft.options.setCameraType(cameraBeforePreview);
            cameraBeforePreview = null;
        }
    }

    private Component statusLine() {
        var selector = ClearAimClient.get().rendererSelector();
        String method = selector.currentMethod().name().toLowerCase(Locale.ROOT);
        Component name = Component.translatable("config.clear_third_person_aim.method." + method);
        String reason = selector.reasonTranslationKey();
        if (reason != null) name = Component.translatable("config.clear_third_person_aim.method_with_reason", name, Component.translatable(reason));
        return Component.translatable("config.clear_third_person_aim.status", name);
    }

    private int statusX() {
        return COLUMN_X * 3 + COLUMN_WIDTH;
    }

    private record StatusLine(net.minecraft.util.FormattedCharSequence text, int color) {
    }

    /** 右側の状態表示。狭い画面でもはみ出さないよう、残りの幅で折り返す。 */
    private List<StatusLine> statusLines() {
        int maxWidth = Math.max(60, width - statusX() - 6);
        List<StatusLine> lines = new java.util.ArrayList<>();
        for (var part : font.split(statusLine(), maxWidth)) lines.add(new StatusLine(part, TEXT_COLOR));
        // プレビューが使えないとき (タイトル画面から開いたとき) だけ、その旨を出す。
        if (!inWorld()) {
            Component hint = Component.translatable("config.clear_third_person_aim.preview_unavailable");
            for (var part : font.split(hint, maxWidth)) lines.add(new StatusLine(part, SUBTLE_TEXT_COLOR));
        }
        return lines;
    }

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTLE_TEXT_COLOR = 0xFFB0B0B0;
    private static final int PANEL_COLOR = 0xB0000000;
    private static final int ELLIPSE_COLOR = 0xC0FFE060;

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!inWorld()) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        // ワールドをぼかさず、左の操作列の後ろだけを暗くして、中央のプレビューを見やすく保つ。
        graphics.fill(0, 0, COLUMN_X * 2 + COLUMN_WIDTH, height, PANEL_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, COLUMN_X, 6, TEXT_COLOR);
        for (Label label : labels) graphics.text(font, label.text(), COLUMN_X, label.y(), SUBTLE_TEXT_COLOR);
        int y = 6;
        for (var line : statusLines()) {
            graphics.text(font, line.text(), statusX(), y, line.color());
            y += 10;
        }
        if (inWorld()) drawEllipseGuide((x0, y0, x1, y1) -> graphics.fill(x0, y0, x1, y1, ELLIPSE_COLOR));
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!inWorld()) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        graphics.fill(0, 0, COLUMN_X * 2 + COLUMN_WIDTH, height, PANEL_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, COLUMN_X, 6, TEXT_COLOR);
        for (Label label : labels) graphics.drawString(font, label.text(), COLUMN_X, label.y(), SUBTLE_TEXT_COLOR);
        int y = 6;
        for (var line : statusLines()) {
            graphics.drawString(font, line.text(), statusX(), y, line.color());
            y += 10;
        }
        if (inWorld()) drawEllipseGuide((x0, y0, x1, y1) -> graphics.fill(x0, y0, x1, y1, ELLIPSE_COLOR));
    }
    *///?}

    private interface RectFiller {
        void fill(int x0, int y0, int x1, int y1);
    }

    /**
     * AIM_ELLIPSE の楕円の輪郭を点線で描く。半径は距離補正込みの実際の値を使うので、スライダー操作や
     * カメラの寄りや視野角の変化に合わせて輪郭が動く。楕円の内側は塗らない (透け具合そのものを見せるため)。
     */
    private void drawEllipseGuide(RectFiller filler) {
        FrameParams params = RenderHooks.frame();
        if (params == null || config.mode != TransparencyMode.AIM_ELLIPSE) return;
        float cx = width / 2f, cy = height / 2f;
        float rx = params.radiusX() * height, ry = params.radiusY() * height;
        int dots = 96;
        for (int i = 0; i < dots; i += 2) {
            double a = Math.PI * 2 * i / dots;
            int x = Math.round(cx + (float) Math.cos(a) * rx);
            int y = Math.round(cy + (float) Math.sin(a) * ry);
            filler.fill(x, y, x + 1, y + 1);
        }
    }
}
