package io.github.yoima.clearthirdpersonaim.core;

/**
 * 「今、透明化を発動すべきか」を判定する純粋関数群。
 * 入力は {@link AimInput} (MC から抽出した値) と {@link Config} だけで、副作用を持たない。
 */
public final class AimState {
    private AimState() {
    }

    public static boolean shouldActivate(Config config, AimInput input) {
        if (!config.enabled) return false;
        // 一人称ではモデル自体が描画されないので何もしない (余計な描画パスも走らせない)。
        if (input.perspective() == Perspective.FIRST_PERSON) return false;
        // 前面三人称ではカメラとエイム方向が逆向きなので、自分の体が照準を塞ぐことはない。既定では無効。
        if (input.perspective() == Perspective.THIRD_PERSON_FRONT && !config.activeInFrontView) return false;

        HeldItem used = input.usedItem();
        if (used != null && isUseTrigger(config, used)) return true;

        return isHoldTrigger(config, input.mainHand()) || isHoldTrigger(config, input.offHand());
    }

    /** 使用中 (長押し中) のアイテムが発動条件を満たすか。 */
    static boolean isUseTrigger(Config config, HeldItem item) {
        switch (item.kind()) {
            case BOW:
                if (config.triggerBowDrawing) return true;
                break;
            case CROSSBOW:
                // 装填済みクロスボウを「使用」すると即発射になるので、使用中 = 装填中とみなせる。
                if (!item.crossbowCharged() && config.triggerCrossbowCharging) return true;
                break;
            case TRIDENT:
                if (config.triggerTridentAiming) return true;
                break;
            default:
                break;
        }
        // 「使用中のみ」と「所持中」のどちらのリストに入っていても、使用中なら発動する。
        return config.extraItemsWhileUsing.contains(item.id()) || config.extraItemsWhileHolding.contains(item.id());
    }

    /** 持っているだけで発動条件を満たすか。 */
    static boolean isHoldTrigger(Config config, HeldItem item) {
        if (item.isEmpty()) return false;
        if (item.kind() == ItemKind.CROSSBOW && item.crossbowCharged() && config.triggerCrossbowLoaded) return true;
        if (item.kind() == ItemKind.THROWABLE && config.triggerThrowableHeld) return true;
        return config.extraItemsWhileHolding.contains(item.id());
    }
}
