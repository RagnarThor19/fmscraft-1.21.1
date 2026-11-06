package net.ragnar.fmscraft.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The traffic cone CONEMAN wore, dropped when he's beaten. Worn on the head like a carved pumpkin (use it, or put it in
 * the helmet slot); drawn by the game's own head layer from its cube model (models/item/traffic_cone.json).
 */
public class TrafficConeItem extends Item implements Equipment {
    public TrafficConeItem(Settings settings) {
        super(settings);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }
}
