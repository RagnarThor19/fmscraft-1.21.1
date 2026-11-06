package net.ragnar.fmscraft.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.compat.MagicTomes;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /gurt (operators only): a boss-testing kit. Full netherite armor with Protection IV (put on, if the slot is free), a
 * Sharpness V netherite sword, a Power V bow and 128 arrows, 32 golden apples, 32 steaks, and Ragnars Magic Mod's
 * netherite staff with Quickcast III and Attunement (if the mod is there).
 */
public final class GurtCommand {
    private GurtCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("gurt").requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> giveKit(ctx.getSource().getPlayerOrThrow()))));
    }

    /** Gives {@code player} the kit. */
    public static int giveKit(ServerPlayerEntity player) {
        Registry<Enchantment> enchantments = player.getServerWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        armor(player, enchantments, EquipmentSlot.HEAD, Items.NETHERITE_HELMET);
        armor(player, enchantments, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE);
        armor(player, enchantments, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS);
        armor(player, enchantments, EquipmentSlot.FEET, Items.NETHERITE_BOOTS);

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        enchant(sword, enchantments, Enchantments.SHARPNESS, 5);
        give(player, sword);
        ItemStack bow = new ItemStack(Items.BOW);
        enchant(bow, enchantments, Enchantments.POWER, 5);
        give(player, bow);
        give(player, new ItemStack(Items.ARROW, 64));
        give(player, new ItemStack(Items.ARROW, 64));
        give(player, new ItemStack(Items.GOLDEN_APPLE, 32));
        give(player, new ItemStack(Items.COOKED_BEEF, 32));

        Item staffItem = Registries.ITEM.getOrEmpty(Identifier.of(MagicTomes.MOD_ID, "netherite_staff")).orElse(null);
        if (staffItem != null) {
            ItemStack staff = new ItemStack(staffItem);
            enchant(staff, enchantments, key("quickcast"), 3);
            enchant(staff, enchantments, key("attunement"), 1);
            give(player, staff);
        } else {
            player.sendMessage(Text.literal("No netherite staff: Ragnars Magic Mod isn't loaded").formatted(Formatting.GRAY), false);
        }

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(),
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.literal("Gurt kit given. Good luck.").formatted(Formatting.GOLD), false);
        return 1;
    }

    private static RegistryKey<Enchantment> key(String magicEnchantment) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(MagicTomes.MOD_ID, magicEnchantment));
    }

    private static void armor(ServerPlayerEntity player, Registry<Enchantment> enchantments, EquipmentSlot slot, Item item) {
        ItemStack stack = new ItemStack(item);
        enchant(stack, enchantments, Enchantments.PROTECTION, 4);
        if (player.getEquippedStack(slot).isEmpty()) player.equipStack(slot, stack);
        else give(player, stack);
    }

    private static void enchant(ItemStack stack, Registry<Enchantment> enchantments, RegistryKey<Enchantment> key, int level) {
        enchantments.getEntry(key).ifPresent(entry -> stack.addEnchantment(entry, level));
    }

    /** Into the inventory, or dropped at their feet if it's full. */
    private static void give(ServerPlayerEntity player, ItemStack stack) {
        player.getInventory().offerOrDrop(stack);
    }
}
