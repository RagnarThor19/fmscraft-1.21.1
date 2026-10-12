package net.ragnar.fmscraft.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModItemGroups {
    // The mod's own creative tab. Swap the icon for one of your items once you have some
    public static final ItemGroup FMSCRAFT = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(FmsCraft.MOD_ID, "fmscraft"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.fmscraft.fmscraft"))
                    .icon(() -> new ItemStack(Items.DIAMOND))
                    .entries((context, entries) -> {
                        // entries.add(ModItems.EXAMPLE);
                    })
                    .build());

    public static void initialize() {
    }
}
