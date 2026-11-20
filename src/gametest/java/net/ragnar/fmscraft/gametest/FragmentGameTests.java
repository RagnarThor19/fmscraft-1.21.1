package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
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
import net.ragnar.fmscraft.fragment.LuckyStrike;
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
    public void greedIsNoLongerInAncientCityChests(TestContext ctx) {
        ctx.assertTrue(lootRolls(ctx, LootTables.ANCIENT_CITY_CHEST, Sin.GREED, 500) == 0, "Greed fragment in an ancient city chest");
        ctx.assertTrue(lootRolls(ctx, LootTables.ANCIENT_CITY_CHEST, Sin.ENVY, 300) == 0, "Envy fragment in an ancient city chest");
        ctx.complete();
    }

    // ---- Greed: a lucky strike ----

    /**
     * A survival player holding {@code tool}, who breaks blocks like a real one (TestContext's mock player always
     * counts as creative, and creative players get no drops).
     */
    private static ServerPlayerEntity miner(TestContext ctx, ItemStack tool) {
        ServerWorld world = ctx.getWorld();
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-miner");
        net.minecraft.server.network.ConnectedClientData data = net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        net.minecraft.network.ClientConnection connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d at = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5)));
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        player.setInvulnerable(true);
        // Thousands of ores would wear any pickaxe out
        if (tool.isDamageable()) tool.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(true));
        player.setStackInHand(Hand.MAIN_HAND, tool);
        return player;
    }

    private static void leave(TestContext ctx, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) ctx.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** Mines {@code ore} at {@code pos} {@code times} times; returns {Greed fragments, other items} it dropped. */
    private static int[] mine(TestContext ctx, ServerPlayerEntity player, Block ore, BlockPos pos, int times, boolean placed) {
        BlockPos at = ctx.getAbsolutePos(pos);
        Box box = new Box(at).expand(3);
        FmsWorldState state = FmsWorldState.get(ctx.getWorld().getServer());
        int fragments = 0, drops = 0;
        for (int i = 0; i < times; i++) {
            ctx.getWorld().setBlockState(at, ore.getDefaultState());
            if (placed) state.rememberPlacedOre(ctx.getWorld().getRegistryKey(), at);
            player.interactionManager.tryBreakBlock(at);
            for (ItemEntity item : ctx.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> true)) {
                if (item.getStack().isOf(fragment(Sin.GREED))) fragments += item.getStack().getCount();
                else drops += item.getStack().getCount();
                item.discard();
            }
            ctx.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, box, e -> true).forEach(Entity::discard);
        }
        return new int[]{fragments, drops};
    }

    /** The same, a couple of hundred a tick so the server (and the tests beside this one) don't stall. */
    private static void mineOverTicks(TestContext ctx, ServerPlayerEntity player, Block ore, BlockPos pos, int times, boolean placed,
                                      int[] totals, Runnable then) {
        int now = Math.min(200, times);
        int[] result = mine(ctx, player, ore, pos, now, placed);
        totals[0] += result[0];
        totals[1] += result[1];
        if (times > now) ctx.waitAndRun(1, () -> mineOverTicks(ctx, player, ore, pos, times - now, placed, totals, then));
        else then.run();
    }

    private static ItemStack silkTouchPickaxe(TestContext ctx) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.addEnchantment(ctx.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), 1);
        return pick;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void greedLuckyStrikeChances(TestContext ctx) {
        ctx.assertTrue(LuckyStrike.chance(Blocks.DIAMOND_ORE.getDefaultState()) == 0.01f, "diamond ore 1%");
        ctx.assertTrue(LuckyStrike.chance(Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState()) == 0.01f, "deepslate diamond ore 1%");
        ctx.assertTrue(LuckyStrike.chance(Blocks.ANCIENT_DEBRIS.getDefaultState()) == 0.01f, "ancient debris 1%");
        for (Block ore : List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)) {
            ctx.assertTrue(LuckyStrike.chance(ore.getDefaultState()) == 0.005f, ore + " 0.5%");
        }
        for (Block other : List.of(Blocks.STONE, Blocks.NETHER_GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.EMERALD_ORE, Blocks.DIAMOND_BLOCK)) {
            ctx.assertTrue(LuckyStrike.chance(other.getDefaultState()) == 0, other + " doesn't count");
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void greedLuckyStrikeDropsAlongsideTheOre(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerPlayerEntity player = miner(ctx, new ItemStack(Items.NETHERITE_PICKAXE));
        int[] diamond = new int[2];
        mineOverTicks(ctx, player, Blocks.DIAMOND_ORE, pos, 3000, false, diamond, () -> {
            leave(ctx, player);
            ctx.assertTrue(diamond[0] >= 12 && diamond[0] <= 52, "expected ~30 Greed fragments from 3000 diamond ores (1%), got " + diamond[0]);
            ctx.assertEquals(diamond[1], 3000, "every diamond ore still drops its diamond");
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void greedLuckyStrikeOnlyFromOreTheWorldMade(TestContext ctx) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerPlayerEntity silk = miner(ctx, silkTouchPickaxe(ctx));
        ServerPlayerEntity wooden = miner(ctx, new ItemStack(Items.WOODEN_PICKAXE));
        ServerPlayerEntity placer = miner(ctx, new ItemStack(Items.ANCIENT_DEBRIS, 64));
        ServerPlayerEntity miner = miner(ctx, new ItemStack(Items.NETHERITE_PICKAXE));
        FmsWorldState state = FmsWorldState.get(ctx.getWorld().getServer());

        // A block of ancient debris a player places is remembered as placed
        ctx.setBlockState(1, 1, 4, Blocks.STONE);
        ctx.useBlock(new BlockPos(1, 1, 4), placer);
        BlockPos placed = ctx.getAbsolutePos(new BlockPos(1, 1, 3));
        boolean wasPlaced = ctx.getWorld().getBlockState(placed).isOf(Blocks.ANCIENT_DEBRIS);
        boolean remembered = state.forgetPlacedOre(ctx.getWorld().getRegistryKey(), placed);

        int[] withSilk = new int[2], withWood = new int[2], fromPlaced = new int[2];
        mineOverTicks(ctx, silk, Blocks.DIAMOND_ORE, pos, 1500, false, withSilk,
                () -> mineOverTicks(ctx, wooden, Blocks.DIAMOND_ORE, pos, 1500, false, withWood,
                        () -> mineOverTicks(ctx, miner, Blocks.ANCIENT_DEBRIS, pos, 1500, true, fromPlaced, () -> {
                            leave(ctx, silk, wooden, placer, miner);
                            ctx.assertTrue(wasPlaced && remembered, "placed debris is remembered as placed");
                            ctx.assertEquals(withSilk[0], 0, "no fragments with Silk Touch");
                            ctx.assertEquals(withWood[0], 0, "no fragments with a pickaxe that can't harvest it");
                            ctx.assertEquals(fromPlaced[0], 0, "placed ore never gives one");
                            ctx.assertEquals(fromPlaced[1], 1500, "though it still drops itself");
                            ctx.assertFalse(state.forgetPlacedOre(ctx.getWorld().getRegistryKey(), ctx.getAbsolutePos(pos)), "and is forgotten once mined");
                            ctx.complete();
                        })));
    }

    /** Villagers no longer sell Greed Fragments, and one that did before loses the trade when it's loaded. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void villagersNoLongerSellGreed(TestContext ctx) {
        for (int i = 0; i < 300; i++) {
            VillagerEntity villager = EntityType.VILLAGER.create(ctx.getWorld());
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.CLERIC).withLevel(1 + i % 5));
            ctx.assertTrue(villager.getOffers().stream().noneMatch(o -> o.getSellItem().isOf(fragment(Sin.GREED))), "no villager sells a Greed Fragment");
        }

        VillagerEntity old = EntityType.VILLAGER.create(ctx.getWorld());
        old.setVillagerData(old.getVillagerData().withProfession(VillagerProfession.CLERIC).withLevel(4));
        old.getOffers().add(new TradeOffer(new TradedItem(Items.DIAMOND, 32), new ItemStack(fragment(Sin.GREED)), 3, 30, 0.05f));
        int before = old.getOffers().size();
        NbtCompound saved = new NbtCompound();
        old.writeNbt(saved);
        VillagerEntity loaded = EntityType.VILLAGER.create(ctx.getWorld());
        loaded.readNbt(saved);
        ctx.assertEquals(loaded.getOffers().size(), before - 1, "only the Greed trade is gone");
        ctx.assertTrue(loaded.getOffers().stream().noneMatch(o -> o.getSellItem().isOf(fragment(Sin.GREED))), "the old Greed trade is gone");
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prideSkullOnAStandGivesOneFragmentOnce(TestContext ctx) {
        ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, 2, 2, 2);
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), PrideTrigger.FRAGMENTS, "a fragment for the skull");
        ItemStack worn = stand.getEquippedStack(EquipmentSlot.HEAD);
        ctx.assertTrue(worn.isOf(Items.SKELETON_SKULL) && PrideTrigger.isSpent(worn), "the skull stays on, spent");

        // Taken off and put back, or onto another stand: nothing more
        stand.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        stand.equipStack(EquipmentSlot.HEAD, worn);
        ArmorStandEntity other = ctx.spawnEntity(EntityType.ARMOR_STAND, 5, 2, 5);
        other.equipStack(EquipmentSlot.HEAD, worn.copy());
        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), PrideTrigger.FRAGMENTS, "a spent skull gives no more");

        // A player's head, or a skull in the stand's hand, doesn't count; any other mob's head does
        other.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
        other.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.ZOMBIE_HEAD));
        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), PrideTrigger.FRAGMENTS, "not for a player head or a held skull");
        other.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CREEPER_HEAD));
        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), PrideTrigger.FRAGMENTS * 2, "a creeper head gives one");
        ctx.complete();
    }

    /** Placing a spent skull as a block and breaking it again doesn't make it new. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prideSpentSkullStaysSpentPlacedAndBroken(TestContext ctx) {
        ItemStack spent = new ItemStack(Items.WITHER_SKELETON_SKULL);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, spent, nbt -> nbt.putBoolean(PrideTrigger.SPENT, true));
        ServerPlayerEntity player = miner(ctx, spent);
        try {
            ctx.setBlockState(1, 1, 4, Blocks.STONE);
            ctx.useBlock(new BlockPos(1, 1, 4), player);
            BlockPos placed = ctx.getAbsolutePos(new BlockPos(1, 1, 3));
            ctx.assertTrue(ctx.getWorld().getBlockState(placed).isOf(Blocks.WITHER_SKELETON_WALL_SKULL)
                    || ctx.getWorld().getBlockState(placed).isOf(Blocks.WITHER_SKELETON_SKULL), "the skull was placed");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryBreakBlock(placed);
            List<ItemEntity> drops = ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(placed).expand(2),
                    e -> e.getStack().isOf(Items.WITHER_SKELETON_SKULL));
            ctx.assertEquals(drops.size(), 1, "the skull drops");
            ctx.assertTrue(PrideTrigger.isSpent(drops.get(0).getStack()), "still spent");
            drops.get(0).discard();
        } finally {
            leave(ctx, player);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void prideNetheriteArmorNoLongerCounts(TestContext ctx) {
        ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, 2, 2, 2);
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        stand.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        stand.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        ctx.assertEquals(droppedFragments(ctx, Sin.PRIDE), 0, "no fragments for netherite armor");
        ctx.assertTrue(stand.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.NETHERITE_CHESTPLATE), "and the armor stays");
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
