package io.github.yoima.clearthirdpersonaim.core;

/**
 * トリガー判定に必要なアイテム分類。
 * MC 側のアダプタが ItemStack をこの分類に変換する (instanceof BowItem など)。
 * 他 MOD の弓がバニラの BowItem を継承していれば BOW として扱われ、そうでなければ追加アイテム ID で拾う。
 */
public enum ItemKind {
    NONE,
    BOW,
    CROSSBOW,
    TRIDENT,
    THROWABLE,
    OTHER
}
