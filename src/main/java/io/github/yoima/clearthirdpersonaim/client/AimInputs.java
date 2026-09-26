package io.github.yoima.clearthirdpersonaim.client;

import io.github.yoima.clearthirdpersonaim.core.AimInput;
import io.github.yoima.clearthirdpersonaim.core.HeldItem;
import io.github.yoima.clearthirdpersonaim.core.ItemKind;
import io.github.yoima.clearthirdpersonaim.core.Perspective;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;

/** MC の状態を MC 非依存の {@link AimInput} に変換する。 */
public final class AimInputs {
    private AimInputs() {
    }

    /** ワールド外・観戦で他エンティティ視点のときなど、判定対象が無ければ null。 */
    public static AimInput capture(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        // 観戦モードで他のエンティティに乗り移っているときは、自分のモデルが視界を塞ぐことはない。
        if (minecraft.getCameraEntity() != player) return null;

        AimInput.Hand using = null;
        if (player.isUsingItem()) {
            using = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? AimInput.Hand.MAIN : AimInput.Hand.OFF;
        }
        return new AimInput(perspective(minecraft.options.getCameraType()),
                held(player.getMainHandItem()), held(player.getOffhandItem()), using);
    }

    public static Perspective perspective(CameraType type) {
        return switch (type) {
            case FIRST_PERSON -> Perspective.FIRST_PERSON;
            case THIRD_PERSON_BACK -> Perspective.THIRD_PERSON_BACK;
            case THIRD_PERSON_FRONT -> Perspective.THIRD_PERSON_FRONT;
        };
    }

    public static HeldItem held(ItemStack stack) {
        if (stack.isEmpty()) return HeldItem.EMPTY;
        Item item = stack.getItem();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (item instanceof CrossbowItem) return new HeldItem(id, ItemKind.CROSSBOW, CrossbowItem.isCharged(stack));
        return HeldItem.of(id, kind(item));
    }

    /**
     * バニラのクラス階層で分類する。他 MOD の武器もバニラのクラスを継承していれば自動的に対象になり、
     * そうでない武器は設定の追加アイテム ID で拾う。
     */
    static ItemKind kind(Item item) {
        if (item instanceof BowItem) return ItemKind.BOW;
        if (item instanceof TridentItem) return ItemKind.TRIDENT;
        if (item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem
                || item instanceof ThrowablePotionItem || item instanceof ExperienceBottleItem || item instanceof WindChargeItem) {
            return ItemKind.THROWABLE;
        }
        return ItemKind.OTHER;
    }
}
