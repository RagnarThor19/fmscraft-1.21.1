package net.ragnar.fmscraft.fragment;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Map;

/**
 * Pride: dress an armor stand in a full set of netherite armor. The stand admires itself, the armor crumbles,
 * and 5 Pride fragments are left behind. Consuming the armor stops one set from being reused forever.
 * Called from ArmorStandEntityMixin whenever the stand's equipment changes.
 */
public final class PrideTrigger {
    public static final int FRAGMENTS = 5;

    private static final Map<EquipmentSlot, Item> FULL_SET = Map.of(
            EquipmentSlot.HEAD, Items.NETHERITE_HELMET,
            EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE,
            EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS,
            EquipmentSlot.FEET, Items.NETHERITE_BOOTS);

    private PrideTrigger() {
    }

    public static void onEquipmentChanged(ArmorStandEntity stand) {
        if (!(stand.getWorld() instanceof ServerWorld world)) return;

        for (Map.Entry<EquipmentSlot, Item> piece : FULL_SET.entrySet()) {
            if (!stand.getEquippedStack(piece.getKey()).isOf(piece.getValue())) return;
        }

        for (EquipmentSlot slot : FULL_SET.keySet()) {
            stand.equipStack(slot, ItemStack.EMPTY);
        }
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, stand.getX(), stand.getY() + 1.2, stand.getZ(),
                60, 0.3, 0.7, 0.3, 0.3);
        FragmentEffects.drop(world, stand.getPos().add(0, 1, 0), Sin.PRIDE, FRAGMENTS);
    }
}
