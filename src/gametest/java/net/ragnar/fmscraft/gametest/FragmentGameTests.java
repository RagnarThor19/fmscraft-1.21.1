package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.fragment.LustTrigger;
import net.ragnar.fmscraft.fragment.PrideTrigger;
import net.ragnar.fmscraft.fragment.WrathTrigger;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.ArrayList;
import java.util.List;

public class FragmentGameTests implements FabricGameTest {
    private static Item fragment(Sin sin) {
        return ModItems.FRAGMENTS.get(sin);
    }

    /** Fragments lying on the ground inside this test's area. */
    private static int droppedFragments(TestContext ctx, Sin sin) {
        return ctx.getWorld().getEntitiesByClass(ItemEntity.class, ctx.getTestBox().expand(2),
                        e -> e.getStack().isOf(fragment(sin)))
                .stream().mapToInt(e -> e.getStack().getCount()).sum();
    }

    // ---- Greed / Envy: loot ----

    private static int lootRolls(TestContext ctx, RegistryKey<LootTable> table, Sin sin, int rolls) {
        ServerWorld world = ctx.getWorld();
        LootTable loot = world.getServer().getReloadableRegistries().getLootTable(table);
        int[] found = {0};
        for (int i = 0; i < rolls; i++) {
            LootContextParameterSet params = new LootContextParameterSet.Builder(world)
                    .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .build(LootContextTypes.CHEST);
            loot.generateLoot(params, stack -> {
                if (stack.isOf(fragment(sin))) found[0] += stack.getCount();
            });
        }
        return found[0];
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void greedFoundInAboutTenPercentOfAncientCityChests(TestContext ctx) {
        int found = lootRolls(ctx, LootTables.ANCIENT_CITY_CHEST, Sin.GREED, 2000);
        ctx.assertTrue(found >= 140 && found <= 260, "expected ~200 Greed fragments in 2000 ancient city chests, got " + found);
        ctx.assertTrue(lootRolls(ctx, LootTables.ANCIENT_CITY_CHEST, Sin.ENVY, 300) == 0, "Envy fragment in an ancient city chest");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void envyIsRareInEndCitiesAndBastions(TestContext ctx) {
        int endCity = lootRolls(ctx, LootTables.END_CITY_TREASURE_CHEST, Sin.ENVY, 2000);
        ctx.assertTrue(endCity >= 55 && endCity <= 150, "expected ~100 Envy fragments in 2000 end city chests, got " + endCity);
        int bastion = lootRolls(ctx, LootTables.BASTION_TREASURE_CHEST, Sin.ENVY, 2000);
        ctx.assertTrue(bastion >= 110 && bastion <= 220, "expected ~160 Envy fragments in 2000 bastion treasure chests, got " + bastion);
        ctx.complete();
    }

    // ---- Wrath ----

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wrathThirdWardenDropsFragment(TestContext ctx) {
        WrathTrigger.reset();
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);

        for (int i = 1; i <= 3; i++) {
            WardenEntity warden = ctx.spawnEntity(EntityType.WARDEN, 1 + i, 2, 2);
            warden.damage(ctx.getWorld().getDamageSources().playerAttack(player), Float.MAX_VALUE);
            ctx.assertTrue(warden.isDead(), "warden " + i + " should be dead");
            if (i < 3) {
                ctx.assertEquals(WrathTrigger.kills(), i, "wrath kill count");
                ctx.assertEquals(droppedFragments(ctx, Sin.WRATH), 0, "fragment before the 3rd warden");
            }
        }
        ctx.assertEquals(droppedFragments(ctx, Sin.WRATH), 1, "Wrath fragments after 3 wardens");
        ctx.assertEquals(WrathTrigger.kills(), 0, "wrath timer should reset after completing");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wrathIgnoresWardensNotKilledByPlayers(TestContext ctx) {
        // Runs alongside the test above, so only check that no fragment shows up in this test's area
        for (int i = 0; i < 3; i++) {
            WardenEntity warden = ctx.spawnEntity(EntityType.WARDEN, 2 + i, 2, 2);
            warden.damage(ctx.getWorld().getDamageSources().generic(), Float.MAX_VALUE);
        }
        ctx.assertEquals(droppedFragments(ctx, Sin.WRATH), 0, "Wrath fragment from wardens nobody killed");
        ctx.complete();
    }

    // ---- Pride ----

    private static void dress(ArmorStandEntity stand, boolean withBoots) {
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        stand.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        stand.equipStack(EquipmentSlot.FEET, new ItemStack(withBoots ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prideFullNetheriteGivesFiveAndConsumesArmor(TestContext ctx) {
        ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, 2, 2, 2);
        dress(stand, true);

        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), PrideTrigger.FRAGMENTS, "Pride fragments");
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ctx.assertTrue(stand.getEquippedStack(slot).isEmpty(), "armor should be consumed: " + slot);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prideNeedsTheWholeSet(TestContext ctx) {
        ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, 2, 2, 2);
        dress(stand, false);

        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), 0, "Pride fragments with diamond boots");
        ctx.assertTrue(stand.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.NETHERITE_HELMET), "armor kept");
        ctx.complete();
    }

    // ---- Gluttony ----

    private static void eat(ServerPlayerEntity player, Item food) {
        ItemStack stack = new ItemStack(food);
        player.eatFood(player.getWorld(), stack, stack.get(DataComponentTypes.FOOD));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gluttonyEnchantedGoldenAppleGivesFragment(TestContext ctx) {
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        eat(player, Items.GOLDEN_APPLE);
        ctx.assertEquals(player.getInventory().count(fragment(Sin.GLUTTONY)), 0, "fragments from a normal golden apple");
        eat(player, Items.ENCHANTED_GOLDEN_APPLE);
        ctx.assertEquals(player.getInventory().count(fragment(Sin.GLUTTONY)), 1, "fragments from an enchanted golden apple");
        ctx.complete();
    }

    // ---- Lust ----

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lustEveryAnimalGivesFiveAndResets(TestContext ctx) {
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        List<String> required = new ArrayList<>(LustTrigger.requiredIds());
        ctx.assertEquals(required.size(), 25, "Two by Two animals in the tag");

        LustTrigger.onBred(player, EntityType.ZOMBIE); // not breedable, ignored
        for (int i = 0; i < required.size() - 1; i++) {
            LustTrigger.onBred(player, Registries.ENTITY_TYPE.get(Identifier.of(required.get(i))));
            LustTrigger.onBred(player, Registries.ENTITY_TYPE.get(Identifier.of(required.get(i)))); // duplicates don't count
        }
        ctx.assertEquals(player.getInventory().count(fragment(Sin.LUST)), 0, "fragments with one animal missing");

        LustTrigger.onBred(player, Registries.ENTITY_TYPE.get(Identifier.of(required.get(required.size() - 1))));
        ctx.assertEquals(player.getInventory().count(fragment(Sin.LUST)), LustTrigger.FRAGMENTS, "Lust fragments");
        ctx.assertTrue(FmsWorldState.get(ctx.getWorld().getServer()).getLustProgress(player.getUuid()).isEmpty(),
                "progress should reset after the reward");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lustCountsRealBreeding(TestContext ctx) {
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        CowEntity a = ctx.spawnEntity(EntityType.COW, 2, 2, 2);
        CowEntity b = ctx.spawnEntity(EntityType.COW, 3, 2, 2);
        a.lovePlayer(player);
        b.lovePlayer(player);
        a.breed(ctx.getWorld(), b);

        ctx.assertTrue(FmsWorldState.get(ctx.getWorld().getServer()).getLustProgress(player.getUuid()).contains("minecraft:cow"),
                "breeding cows should count toward Lust");
        ctx.complete();
    }

    // ---- Sloth ----

    private static int killChickens(TestContext ctx, WolfEntity wolf, int count) {
        for (int i = 0; i < count; i++) {
            ChickenEntity chicken = ctx.spawnEntity(EntityType.CHICKEN, 2, 2, 2);
            chicken.damage(ctx.getWorld().getDamageSources().mobAttack(wolf), 100f);
        }
        return droppedFragments(ctx, Sin.SLOTH);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slothTamedWolfKillsCanDropFragments(TestContext ctx) {
        ServerPlayerEntity owner = ctx.createMockCreativeServerPlayerInWorld();
        WolfEntity wolf = ctx.spawnEntity(EntityType.WOLF, 4, 2, 4);
        wolf.setOwner(owner);
        // 5% chance: 300 kills with no fragment would happen about once in 5 million runs
        int found = killChickens(ctx, wolf, 300);
        ctx.assertTrue(found >= 1 && found <= 40, "expected ~15 Sloth fragments from 300 tamed wolf kills, got " + found);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slothWildWolfKillsGiveNothing(TestContext ctx) {
        WolfEntity wolf = ctx.spawnEntity(EntityType.WOLF, 4, 2, 4);
        ctx.assertEquals(killChickens(ctx, wolf, 100), 0, "Sloth fragments from a wild wolf");
        ctx.complete();
    }

    // ---- Keys ----

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fiveFragmentsInAPlusCraftTheKey(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        for (Sin sin : Sin.values()) {
            ItemStack f = new ItemStack(fragment(sin));
            List<ItemStack> plus = List.of(
                    ItemStack.EMPTY, f.copy(), ItemStack.EMPTY,
                    f.copy(), f.copy(), f.copy(),
                    ItemStack.EMPTY, f.copy(), ItemStack.EMPTY);
            CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, plus);
            ItemStack result = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world)
                    .map(r -> r.value().craft(input, world.getRegistryManager()))
                    .orElse(ItemStack.EMPTY);
            ctx.assertTrue(result.isOf(ModItems.KEYS.get(sin)), sin.id() + " plus should craft its key, got " + result);

            List<ItemStack> missingOne = new ArrayList<>(plus);
            missingOne.set(7, ItemStack.EMPTY);
            ctx.assertTrue(world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING,
                    CraftingRecipeInput.create(3, 3, missingOne), world).isEmpty(), sin.id() + " key with 4 fragments");
        }
        ctx.complete();
    }
}
