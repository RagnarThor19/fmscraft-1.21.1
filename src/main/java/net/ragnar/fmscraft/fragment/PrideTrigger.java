package net.ragnar.fmscraft.fragment;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.function.CopyComponentsLootFunction;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pride: a trophy. Put a mob's skull or head (tag {@code fmscraft:pride_skulls}: skeleton, wither skeleton, zombie,
 * creeper, piglin, dragon; not player heads) on an armor stand and a Pride Fragment drops. Each skull works once:
 * it's marked as spent, and keeps the mark wherever it goes, even placed as a block and broken again (the skulls'
 * block loot is made to copy it back). Called from ArmorStandEntityMixin whenever the stand's equipment changes.
 */
public final class PrideTrigger {
    public static final int FRAGMENTS = 1;
    public static final TagKey<Item> SKULLS = TagKey.of(RegistryKeys.ITEM, Identifier.of(FmsCraft.MOD_ID, "pride_skulls"));
    /** The mark on a spent skull, in its custom data. */
    public static final String SPENT = "fmscraft:pride_spent";

    /** The skulls placed as blocks: their loot carries the mark from the block back to the item. */
    private static final Set<RegistryKey<LootTable>> SKULL_LOOT = Stream.of(Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL,
                    Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD, Blocks.PIGLIN_HEAD, Blocks.DRAGON_HEAD)
            .map(Block::getLootTableKey).collect(Collectors.toSet());

    private PrideTrigger() {
    }

    public static void register() {
        // A placed skull keeps its item's custom data in its block entity; vanilla's loot just doesn't copy it back
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!source.isBuiltin() || !SKULL_LOOT.contains(key)) return;
            builder.modifyPools(pool -> pool.apply(
                    CopyComponentsLootFunction.builder(CopyComponentsLootFunction.Source.BLOCK_ENTITY).include(DataComponentTypes.CUSTOM_DATA)));
        });
    }

    public static boolean isSpent(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(SPENT);
    }

    public static void onEquipped(ArmorStandEntity stand, EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD || !(stand.getWorld() instanceof ServerWorld world)) return;
        ItemStack head = stand.getEquippedStack(EquipmentSlot.HEAD);
        if (!head.isIn(SKULLS) || isSpent(head)) return;

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, head, nbt -> nbt.putBoolean(SPENT, true));
        double top = stand.getY() + stand.getHeight();
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, stand.getX(), top, stand.getZ(), 30, 0.25, 0.3, 0.25, 0.25);
        FragmentEffects.drop(world, stand.getPos().add(0, stand.getHeight() + 0.3, 0), Sin.PRIDE, FRAGMENTS);
    }
}
