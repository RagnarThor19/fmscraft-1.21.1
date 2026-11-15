package net.ragnar.fmscraft.item;

import net.minecraft.block.Block;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.ragnar.fmscraft.FmsCraft;

/**
 * The traffic cone CONEMAN wore, dropped (one time in ten) when he's beaten. Use it on a block to set it down there,
 * like a carved pumpkin; use it in the air (or put it in the helmet slot) to wear it, where it's as good as a netherite
 * helmet. Drawn on the head by the game's own head layer from its cube model (models/item/traffic_cone.json).
 */
public class TrafficConeItem extends BlockItem implements Equipment {
    /** A netherite helmet's armor, toughness and knockback resistance. */
    public static final double ARMOR = 3, TOUGHNESS = 3, KNOCKBACK_RESISTANCE = 0.1;

    public TrafficConeItem(Block block, Settings settings) {
        super(block, settings.attributeModifiers(attributes()));
    }

    private static AttributeModifiersComponent attributes() {
        Identifier id = Identifier.of(FmsCraft.MOD_ID, "traffic_cone");
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ARMOR, new EntityAttributeModifier(id, ARMOR, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.HEAD)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, new EntityAttributeModifier(id, TOUGHNESS, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.HEAD)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, new EntityAttributeModifier(id, KNOCKBACK_RESISTANCE, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.HEAD)
                .build();
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }
}
