package net.ragnar.fmscraft.fragment;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.ragnar.fmscraft.sin.Sin;

/** Gluttony: eating an enchanted golden apple gives 1 fragment. Called from PlayerEntityMixin. */
public final class GluttonyTrigger {
    private GluttonyTrigger() {
    }

    public static void onEat(PlayerEntity player, ItemStack food) {
        if (player.getWorld().isClient || !food.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return;
        FragmentEffects.give(player, Sin.GLUTTONY, 1);
    }
}
