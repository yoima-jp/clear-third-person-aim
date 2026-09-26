package io.github.yoima.clearthirdpersonaim.core;

import java.util.Objects;

/**
 * 1 tick 分のトリガー判定入力。MC 側で毎 tick 組み立てて {@link AimState#shouldActivate} に渡す。
 *
 * @param perspective 現在の視点
 * @param mainHand    メインハンドのアイテム
 * @param offHand     オフハンドのアイテム
 * @param usingHand   使用中 (右クリック長押し中) の手。使用していなければ null
 */
public record AimInput(Perspective perspective, HeldItem mainHand, HeldItem offHand, Hand usingHand) {
    public enum Hand {
        MAIN,
        OFF
    }

    public AimInput {
        Objects.requireNonNull(perspective, "perspective");
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
    }

    public HeldItem usedItem() {
        if (usingHand == null) return null;
        return usingHand == Hand.MAIN ? mainHand : offHand;
    }
}
