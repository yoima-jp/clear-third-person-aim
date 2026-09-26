package io.github.yoima.clearthirdpersonaim.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * MOD の全設定値。フィールドは設定画面から直接書き換えられ、次のフレームから描画に反映される。
 *
 * <p>外部の設定ライブラリには依存せず、MC が全対象バージョンで同梱している Gson で JSON 化する。
 * フィールド名がそのまま JSON キーになるので、改名すると既存ユーザーの設定が失われる点に注意。
 */
public final class Config {
    public static final String FILE_NAME = "clear_third_person_aim.json";

    // 範囲の上下限。設定画面のスライダー範囲もこの定数を使う。
    public static final float MIN_RADIUS = 0.02f;
    public static final float MAX_RADIUS = 1.0f;
    public static final float MAX_FEATHER = 0.5f;
    public static final int MAX_FADE_MS = 2000;

    /** キーバインドで切り替える全体スイッチ。 */
    public boolean enabled = true;

    public boolean triggerBowDrawing = true;
    public boolean triggerCrossbowCharging = true;
    public boolean triggerCrossbowLoaded = true;
    public boolean triggerTridentAiming = true;
    public boolean triggerThrowableHeld = false;
    /** 使用中 (長押し中) だけ発動する追加アイテム ID。例 "somemod:longbow" */
    public List<String> extraItemsWhileUsing = new ArrayList<>();
    /** 手に持っているだけで発動する追加アイテム ID。 */
    public List<String> extraItemsWhileHolding = new ArrayList<>();
    public boolean activeInFrontView = false;

    public TransparencyMode mode = TransparencyMode.AIM_ELLIPSE;
    /** 透明化した部分に残す不透明度。0 で完全に見えない。 */
    public float remainingOpacity = 0.2f;
    /** 楕円の横半径 (画面の高さに対する比率)。 */
    public float ellipseRadiusX = 0.16f;
    /** 楕円の縦半径 (画面の高さに対する比率)。 */
    public float ellipseRadiusY = 0.26f;
    /** 楕円境界のぼかし幅 (正規化距離 d に対する幅)。 */
    public float feather = 0.06f;
    public boolean distanceCompensation = true;
    /** true なら手に持った武器も本体と一緒に透ける。既定では弓を見えたままにする。 */
    public boolean hideHeldItems = false;

    public TransitionMode transition = TransitionMode.FADE;
    public int fadeInMs = 150;
    public int fadeOutMs = 250;

    /** デバッグ用: オフスクリーン合成が使える環境でもパーツ単位の半透明描画 (フォールバック) を使う。 */
    public boolean forceFallbackRenderer = false;

    public Config copy() {
        Config c = GSON.fromJson(GSON.toJsonTree(this), Config.class);
        c.clamp(ignored -> { });
        return c;
    }

    /** 全フィールドを {@code other} の値で置き換える (設定画面の「キャンセル」で元に戻すために使う)。 */
    public void copyFrom(Config other) {
        Config c = other.copy();
        enabled = c.enabled;
        triggerBowDrawing = c.triggerBowDrawing;
        triggerCrossbowCharging = c.triggerCrossbowCharging;
        triggerCrossbowLoaded = c.triggerCrossbowLoaded;
        triggerTridentAiming = c.triggerTridentAiming;
        triggerThrowableHeld = c.triggerThrowableHeld;
        extraItemsWhileUsing = c.extraItemsWhileUsing;
        extraItemsWhileHolding = c.extraItemsWhileHolding;
        activeInFrontView = c.activeInFrontView;
        mode = c.mode;
        remainingOpacity = c.remainingOpacity;
        ellipseRadiusX = c.ellipseRadiusX;
        ellipseRadiusY = c.ellipseRadiusY;
        feather = c.feather;
        distanceCompensation = c.distanceCompensation;
        hideHeldItems = c.hideHeldItems;
        transition = c.transition;
        fadeInMs = c.fadeInMs;
        fadeOutMs = c.fadeOutMs;
        forceFallbackRenderer = c.forceFallbackRenderer;
    }

    /**
     * 値を有効範囲に収める。範囲外・null があれば {@code warn} に内容を渡す。
     * 手で JSON を編集したユーザーの値を黙って捨てないよう、直した内容はログに残す。
     */
    public void clamp(Consumer<String> warn) {
        remainingOpacity = clampFloat("remainingOpacity", remainingOpacity, 0f, 1f, 0.2f, warn);
        ellipseRadiusX = clampFloat("ellipseRadiusX", ellipseRadiusX, MIN_RADIUS, MAX_RADIUS, 0.16f, warn);
        ellipseRadiusY = clampFloat("ellipseRadiusY", ellipseRadiusY, MIN_RADIUS, MAX_RADIUS, 0.26f, warn);
        feather = clampFloat("feather", feather, 0f, MAX_FEATHER, 0.06f, warn);
        fadeInMs = clampInt("fadeInMs", fadeInMs, 0, MAX_FADE_MS, warn);
        fadeOutMs = clampInt("fadeOutMs", fadeOutMs, 0, MAX_FADE_MS, warn);
        if (mode == null) {
            warn.accept("mode is missing or unknown; using AIM_ELLIPSE");
            mode = TransparencyMode.AIM_ELLIPSE;
        }
        if (transition == null) {
            warn.accept("transition is missing or unknown; using FADE");
            transition = TransitionMode.FADE;
        }
        extraItemsWhileUsing = normalizeIds("extraItemsWhileUsing", extraItemsWhileUsing, warn);
        extraItemsWhileHolding = normalizeIds("extraItemsWhileHolding", extraItemsWhileHolding, warn);
    }

    private static float clampFloat(String name, float value, float min, float max, float fallback, Consumer<String> warn) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            warn.accept(name + " is not a finite number; using " + fallback);
            return fallback;
        }
        if (value < min || value > max) {
            float clamped = Math.max(min, Math.min(max, value));
            warn.accept(name + "=" + value + " is out of range [" + min + ", " + max + "]; clamped to " + clamped);
            return clamped;
        }
        return value;
    }

    private static int clampInt(String name, int value, int min, int max, Consumer<String> warn) {
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            warn.accept(name + "=" + value + " is out of range [" + min + ", " + max + "]; clamped to " + clamped);
            return clamped;
        }
        return value;
    }

    /**
     * アイテム ID を小文字化・空白除去し、名前空間の無い ID には "minecraft:" を補う。
     * 重複と不正な文字を含む ID は取り除く (MC の Identifier と同じ許容文字)。
     */
    static List<String> normalizeIds(String name, List<String> ids, Consumer<String> warn) {
        if (ids == null) return new ArrayList<>();
        Set<String> result = new LinkedHashSet<>();
        for (String raw : ids) {
            if (raw == null) continue;
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!isValidId(id)) {
                warn.accept(name + ": ignoring invalid item id '" + raw + "'");
                continue;
            }
            result.add(id);
        }
        return new ArrayList<>(result);
    }

    static boolean isValidId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon != id.lastIndexOf(':') || colon == id.length() - 1) return false;
        for (int i = 0; i < id.length(); i++) {
            if (i == colon) continue;
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
                    || (i > colon && c == '/');
            if (!ok) return false;
        }
        return true;
    }

    /** "a:b, c:d" のようなカンマ/空白区切り文字列 (設定画面の入力欄) を正規化済み ID リストへ変換する。 */
    public static List<String> parseIdList(String text) {
        List<String> ids = new ArrayList<>();
        for (String part : text.split("[,\\s]+")) {
            if (!part.isBlank()) ids.add(part);
        }
        return normalizeIds("input", ids, ignored -> { });
    }

    // ---- JSON 入出力 ----

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * JSON 文字列から読み込む。構文エラーは {@link JsonParseException} として呼び出し元に伝える。
     * 欠けているキーはデフォルト値のまま残るので、新しいバージョンで設定項目が増えても既存ファイルを壊さない。
     */
    public static Config fromJson(String json, Consumer<String> warn) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new JsonParseException("root is not a JSON object");
        Config config = GSON.fromJson(root, Config.class);
        if (config == null) throw new JsonParseException("empty config");
        config.clamp(warn);
        return config;
    }

    /** 読み込み結果。{@code rewritten} はファイルをデフォルト値で作り直したかどうか。 */
    public record LoadResult(Config config, boolean rewritten, List<String> warnings) {
    }

    /**
     * ファイルから読み込む。ファイルが無ければデフォルトで作成し、壊れていればデフォルトで上書きする。
     * 壊れたファイルは原因を調べられるよう {@code .broken} として退避してから上書きする。
     */
    public static LoadResult load(Path path) {
        List<String> warnings = new ArrayList<>();
        if (!Files.exists(path)) {
            Config config = new Config();
            trySave(config, path, warnings);
            return new LoadResult(config, true, warnings);
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Config config = fromJson(json, warnings::add);
            if (!warnings.isEmpty()) {
                // クランプした値をファイルにも反映し、次回以降同じ警告を出さない。
                trySave(config, path, warnings);
            }
            return new LoadResult(config, false, warnings);
        } catch (IOException | RuntimeException e) {
            warnings.add("Config file " + path + " is broken (" + e.getMessage() + "); replacing it with defaults");
            try {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyError) {
                warnings.add("Could not back up broken config: " + copyError.getMessage());
            }
            Config config = new Config();
            trySave(config, path, warnings);
            return new LoadResult(config, true, warnings);
        }
    }

    public void save(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        // 書き込み途中でゲームが落ちても設定ファイルが空にならないよう、一時ファイルに書いてから置き換える。
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void trySave(Config config, Path path, List<String> warnings) {
        try {
            config.save(path);
        } catch (IOException e) {
            warnings.add("Could not write config " + path + ": " + e.getMessage());
        }
    }
}
