package io.github.yoima.clearthirdpersonaim.core;

import java.util.Objects;

/**
 * 片手に持っているアイテムの判定用スナップショット。
 *
 * @param id              "namespace:path" 形式のアイテム ID。空手は {@link #EMPTY}
 * @param kind            アイテム分類
 * @param crossbowCharged クロスボウが装填済みか (CROSSBOW 以外では無視)
 */
public record HeldItem(String id, ItemKind kind, boolean crossbowCharged) {
    public static final HeldItem EMPTY = new HeldItem("minecraft:air", ItemKind.NONE, false);

    public HeldItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }

    public static HeldItem of(String id, ItemKind kind) {
        return new HeldItem(id, kind, false);
    }

    public boolean isEmpty() {
        return kind == ItemKind.NONE;
    }
}
