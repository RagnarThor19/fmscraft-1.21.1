package net.ragnar.fmscraft.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

public class ModItemGroups {
    public static final ItemGroup FMSCRAFT = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(FmsCraft.MOD_ID, "fmscraft"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.fmscraft.fmscraft"))
                    .icon(() -> new ItemStack(ModItems.KEYS.get(Sin.WRATH)))
                    .entries((context, entries) -> {
                        for (Sin sin : Sin.values()) {
                            entries.add(ModItems.FRAGMENTS.get(sin));
                            entries.add(ModItems.KEYS.get(sin));
                            entries.add(ModItems.SOULS.get(sin));
                        }
                    })
                    .build());

    public static void initialize() {
    }
}
