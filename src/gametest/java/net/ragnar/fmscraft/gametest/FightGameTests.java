package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.DeathChests;
import net.ragnar.fmscraft.fight.FightBot;
import net.ragnar.fmscraft.fight.FightBots;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.reward.TomeWheel;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.ArrayList;
import java.util.List;

/** Each test uses a different sin so they can run at the same time. Join windows are shortened to 1 second. */
public class FightGameTests implements FabricGameTest {
    private static final int JOIN = 20;
    /** Server tick until which a test holds Sloth (a test that completes drops its own tasks, so no task frees it). */
    private static int slothClaimedUntil = -1;

    /**
     * Runs {@code body} once no Sloth fight is open and no other test holds Sloth, holding it for {@code holdTicks}
     * (a fight the body opens holds it from then on by itself).
     */
    private static void withSloth(TestContext ctx, int holdTicks, Runnable body) {
        int now = ctx.getWorld().getServer().getTicks();
        if (now < slothClaimedUntil || BossFights.session(Sin.SLOTH) != null) {
            ctx.waitAndRun(1, () -> withSloth(ctx, holdTicks, body));
            return;
        }
        slothClaimedUntil = now + holdTicks + 1;
        body.run();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deathChestsHoldEverything(TestContext ctx) {
        List<ItemStack> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item != Items.AIR && items.size() < 40) items.add(new ItemStack(item));
        }
        List<BlockPos> chests = DeathChests.store(ctx.getWorld(), ctx.getAbsolutePos(new BlockPos(3, 1, 3)), items);

        ctx.assertEquals(chests.size(), 2, "chests for 40 stacks");
        int stored = 0;
        for (BlockPos pos : chests) {
            ChestBlockEntity chest = (ChestBlockEntity) ctx.getWorld().getBlockEntity(pos);
            for (int i = 0; i < chest.size(); i++) if (!chest.getStack(i).isEmpty()) stored++;
        }
        ctx.assertEquals(stored, 40, "stacks stored");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeFramesPlaceTheirOwnSin(TestContext ctx) {
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        for (Sin sin : Sin.values()) {
            ItemStack stack = ModItems.SIN_PORTAL_FRAME.stack(sin);
            ctx.assertEquals(stack.getName().getString(), sin.displayName() + " Portal Frame", "item name");

            BlockPos below = ctx.getAbsolutePos(new BlockPos(1 + sin.ordinal(), 0, 2));
            ctx.getWorld().setBlockState(below, Blocks.STONE.getDefaultState());
            player.setStackInHand(Hand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(below.up().toCenterPos(), Direction.UP, below, false);
            ModItems.SIN_PORTAL_FRAME.place(new ItemPlacementContext(ctx.getWorld(), player, Hand.MAIN_HAND, stack, hit));

            BlockState placed = ctx.getWorld().getBlockState(below.up());
            ctx.assertTrue(placed.isOf(ModBlocks.SIN_PORTAL_FRAME) && placed.get(SinPortalFrameBlock.SIN) == sin,
                    "the " + sin.displayName() + " frame item should place that sin's frame, got " + placed);
        }
        ctx.complete();
    }

    /** /gurt: the boss-testing kit, enchanted as it should be. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gurtGivesTheTestingKit(TestContext ctx) {
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        net.ragnar.fmscraft.command.GurtCommand.giveKit(player);
        var enchantments = ctx.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
        java.util.function.BiFunction<ItemStack, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment>, Integer> level =
                (stack, key) -> enchantments.getEntry(key).map(e -> net.minecraft.enchantment.EnchantmentHelper.getLevel(e, stack)).orElse(-1);
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack worn = player.getEquippedStack(slot);
            ctx.assertTrue(worn.getItem() instanceof net.minecraft.item.ArmorItem armor && armor.getMaterial().matches(net.minecraft.item.ArmorMaterials.NETHERITE),
                    "netherite on " + slot + ", got " + worn);
            ctx.assertEquals(level.apply(worn, net.minecraft.enchantment.Enchantments.PROTECTION), 4, "Protection IV on " + slot);
        }
        var inv = player.getInventory();
        int arrows = 0, apples = 0, steaks = 0;
        ItemStack sword = ItemStack.EMPTY, bow = ItemStack.EMPTY, staff = ItemStack.EMPTY;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.ARROW)) arrows += s.getCount();
            if (s.isOf(Items.GOLDEN_APPLE)) apples += s.getCount();
            if (s.isOf(Items.COOKED_BEEF)) steaks += s.getCount();
            if (s.isOf(Items.NETHERITE_SWORD)) sword = s;
            if (s.isOf(Items.BOW)) bow = s;
            if (net.minecraft.registry.Registries.ITEM.getId(s.getItem()).equals(net.minecraft.util.Identifier.of("ragnarsmagicmod", "netherite_staff"))) staff = s;
        }
        ctx.assertEquals(arrows, 128, "arrows");
        ctx.assertEquals(apples, 32, "golden apples");
        ctx.assertEquals(steaks, 32, "steaks");
        ctx.assertEquals(level.apply(sword, net.minecraft.enchantment.Enchantments.SHARPNESS), 5, "Sharpness V");
        ctx.assertEquals(level.apply(bow, net.minecraft.enchantment.Enchantments.POWER), 5, "Power V");
        ctx.assertFalse(staff.isEmpty(), "the netherite staff");
        var magic = (java.util.function.Function<String, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment>>) id ->
                net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("ragnarsmagicmod", id));
        ctx.assertEquals(level.apply(staff, magic.apply("quickcast")), 3, "Quickcast III");
        ctx.assertEquals(level.apply(staff, magic.apply("attunement")), 1, "Attunement");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void magicModTomesAreFoundForEveryTier(TestContext ctx) {
        for (WheelTier tier : WheelTier.values()) {
            ctx.assertTrue(MagicTomes.of(tier).size() >= 5, "expected Ragnars Magic Mod " + tier + " tomes, found " + MagicTomes.of(tier).size());
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1500)
    public void emptyPortalClosesWithoutAFight(TestContext ctx) {
        // One Sloth fight at a time, and other tests' batches overlap this one: wait for Sloth to be free
        withSloth(ctx, 1, () -> {
            ServerWorld world = ctx.getWorld();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
            ctx.assertTrue(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN), "portal should open");
            ctx.assertTrue(world.getBlockState(base.up(2).east()).isOf(ModBlocks.SIN_PORTAL), "2x3 portal placed");
            ctx.assertFalse(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN), "second key while open");

            ctx.waitAndRun(JOIN + 5, () -> {
                ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "nobody entered, so no fight");
                ctx.assertTrue(world.getBlockState(base).isAir(), "portal should close");
                ctx.complete();
            });
        });
    }

    /** Sloth still fights the stand-in test boss, which can be beaten in one hit; its own batch, so no other Sloth fight is open. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1500, batchId = "fight_win")
    public void killingTheBossWinsTheFight(TestContext ctx) {
        // One Sloth fight at a time, and other tests' batches overlap this one: wait for Sloth to be free
        withSloth(ctx, 1, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
            BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN);
            ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
            BossFights.enter(player, Sin.SLOTH);

            ctx.waitAndRun(JOIN + 10, () -> {
                ServerWorld arena = Arenas.world(server, Sin.SLOTH);
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
                ctx.assertTrue(player.getServerWorld() == arena, "player should be in the arena");
                ctx.assertTrue(world.getBlockState(base).isAir(), "entry portal should be closed");

                Entity boss = arena.getEntity(session.bossId());
                ctx.assertTrue(boss instanceof SinBossEntity, "boss should be spawned, got " + boss);
                ((LivingEntity) boss).damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);

                ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.SLOTH), "Sloth should be marked defeated");
                ctx.assertTrue(session.stage() == FightSession.Stage.WON, "fight should be won");
                ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(boss.getBlockPos()).expand(3),
                        e -> e.getStack().isOf(ModItems.SOULS.get(Sin.SLOTH))).isEmpty(), "Soul of Sloth should drop");
                ctx.assertTrue(arena.getBlockState(Arenas.EXIT_PORTAL).isOf(ModBlocks.SIN_PORTAL), "exit portal should open");
            });

            // The tome wheel pays out (instantly here, since the mock player has no client to animate it)
            ctx.waitAndRun(JOIN + 10 + TomeWheel.SPIN_TICKS + 10, () -> {
                int tomes = 0;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    for (WheelTier tier : WheelTier.values()) if (MagicTomes.of(tier).contains(stack.getItem())) tomes++;
                }
                ctx.assertEquals(tomes, 1, "tomes won from the wheel");
                BossFights.forceEnd(server, Sin.SLOTH);
                FmsWorldState.get(server).clearDefeated(Sin.SLOTH);
                ctx.complete();
            });
        });
    }

    /** Dead in a boss's arena with no fight on (after a win, or never came through the portal): never left lying there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1500, batchId = "fight_stray_death")
    public void aDeathInAnArenaOutsideAFightStillChestsTheLoot(TestContext ctx) {
        // One Sloth fight at a time, and other tests' batches overlap this one: wait for Sloth to be free
        withSloth(ctx, 10, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
            ServerWorld arena = Arenas.world(server, Sin.SLOTH);
            ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "no Sloth fight on");
            ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
            FmsWorldState.get(server).setReturn(player.getUuid(), net.minecraft.util.math.GlobalPos.create(world.getRegistryKey(), base));
            arena.setChunkForced(0, 0, true);
            player.teleport(arena, 0.5, 120, 0.5, 0, 0);
            player.onTeleportationDone();
            player.getInventory().insertStack(new ItemStack(Items.DIAMOND_SWORD));
            player.damage(player.getDamageSources().outOfWorld(), Float.MAX_VALUE);
            ctx.waitAndRun(2, () -> {
                boolean lying = !arena.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(0, 0, 0, 1, 256, 1).expand(20),
                        e -> e.getStack().isOf(Items.DIAMOND_SWORD)).isEmpty();
                boolean swordInChest = false;
                for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 3, 7))) {
                    if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                        for (int i = 0; i < chest.size(); i++) swordInChest |= chest.getStack(i).isOf(Items.DIAMOND_SWORD);
                    }
                }
                arena.setChunkForced(0, 0, false);
                FmsWorldState.get(server).clearReturn(player.getUuid());
                server.getPlayerManager().remove(player);
                ctx.assertFalse(lying, "nothing left lying in the arena");
                ctx.assertTrue(swordInChest, "the sword is in a chest by the portal they came in through");
                ctx.complete();
            });
        });
    }

    // Its own batch: Sloth's portal can only be open once at a time
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1500, batchId = "fight_death")
    public void aDeathEndsTheFightAndChestsTheLoot(TestContext ctx) {
        // One Sloth fight at a time, and other tests' batches overlap this one: wait for Sloth to be free
        withSloth(ctx, 1, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
            BossFights.open(Sin.SLOTH, world, base, Direction.Axis.Z, JOIN);
            ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
            BossFights.enter(player, Sin.SLOTH);

            ctx.waitAndRun(JOIN + 10, () -> {
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
                Entity boss = Arenas.world(server, Sin.SLOTH).getEntity(session.bossId());

                player.getInventory().insertStack(new ItemStack(Items.DIAMOND_SWORD));
                player.onTeleportationDone(); // a real client confirms the teleport; until then players can't be hurt
                player.damage(player.getDamageSources().outOfWorld(), Float.MAX_VALUE);

                ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "fight should end when someone dies");
                ctx.assertTrue(boss == null || boss.isRemoved(), "boss should be removed");
                ctx.assertFalse(FmsWorldState.get(server).isDefeated(Sin.SLOTH), "Sloth should not count as defeated");

                boolean swordInChest = false;
                for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 3, 7))) {
                    if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                        for (int i = 0; i < chest.size(); i++) swordInChest |= chest.getStack(i).isOf(Items.DIAMOND_SWORD);
                    }
                }
                ctx.assertTrue(swordInChest, "the fallen player's sword should be in a chest by the portal");
                ctx.complete();
            });
        });
    }

    // ---- /fmscraft fight <sin> AI|NoAI <count> <canDie> ----

    private static int count(ServerPlayerEntity player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) n += stack.getCount();
        }
        return n;
    }

    private static boolean chestNear(ServerWorld world, BlockPos base, Item item) {
        for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 3, 7))) {
            if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.size(); i++) if (chest.getStack(i).isOf(item)) return true;
            }
        }
        return false;
    }

    /** Bots join like players, walk in, and one dying ends it for everyone with its kit chested by the portal. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2000, batchId = "fight_bots")
    public void botsFightAndFallLikeFriends(TestContext ctx) {
        withSloth(ctx, 1, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
            int join = 60;
            ctx.assertTrue(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, join), "portal should open");
            List<FightBot> bots = FightBots.start(world, ctx.getAbsolute(new net.minecraft.util.math.Vec3d(1.5, 1, 1.5)), 0f, Sin.SLOTH, 2, true, true);
            ctx.assertEquals(bots.size(), 2, "bots joined");
            FightBot survivor = bots.get(0), fallen = bots.get(1);
            for (FightBot bot : bots) {
                ctx.assertTrue(server.getPlayerManager().getPlayer(bot.getUuid()) == bot, "bot is on the player list");
                ctx.assertTrue(bot.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.NETHERITE_CHESTPLATE), "netherite on");
                ctx.assertEquals(count(bot, Items.GOLDEN_APPLE), 32, "golden apples");
            }

            ctx.waitAndRun(join + 10, () -> {
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
                ServerWorld arena = Arenas.world(server, Sin.SLOTH);
                for (FightBot bot : bots) {
                    ctx.assertTrue(session.participants.contains(bot.getUuid()), bot.getName().getString() + " should be in the fight");
                    ctx.assertTrue(bot.getServerWorld() == arena, bot.getName().getString() + " should have walked in");
                }

                fallen.damage(fallen.getDamageSources().outOfWorld(), Float.MAX_VALUE);
                ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "a bot dying ends the fight");
                ctx.assertTrue(chestNear(world, base, Items.NETHERITE_CHESTPLATE), "the fallen bot's kit is chested by the portal");

                ctx.waitAndRun(3, () -> {
                    ctx.assertTrue(survivor.getServerWorld() == world && survivor.getBlockPos().getManhattanDistance(base) <= 3,
                            "the other bot is sent back to the portal, at " + survivor.getBlockPos() + " in " + survivor.getWorld().getRegistryKey().getValue());
                    ctx.waitAndRun(FightBots.LEAVE_AFTER + 5, () -> {
                        ctx.assertTrue(server.getPlayerManager().getPlayer(survivor.getUuid()) == null, "survivor logged off");
                        ctx.assertTrue(server.getPlayerManager().getPlayer(fallen.getUuid()) == null, "fallen bot logged off");
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** With canDie off a bot is left on half a heart, the fight goes on, and it eats a golden apple. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2000, batchId = "fight_bots_undying")
    public void undyingBotSurvivesAndEats(TestContext ctx) {
        withSloth(ctx, 1, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
            int join = 40;
            BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, join);
            FightBot bot = FightBots.start(world, ctx.getAbsolute(new net.minecraft.util.math.Vec3d(1.5, 1, 1.5)), 0f, Sin.SLOTH, 1, true, false).get(0);

            // Past the 3 seconds of protection every player gets on joining
            ctx.waitAndRun(join + 40, () -> {
                // (just after a hit from the boss, damage() reports false but still deals the difference)
                bot.damage(bot.getDamageSources().generic(), 1000f);
                ctx.assertTrue(bot.isAlive() && bot.getHealth() == 1f, "bot that can't die should be left on half a heart, health " + bot.getHealth()
                        + " (mode " + bot.interactionManager.getGameMode() + ", teleporting " + bot.isInTeleportationState() + ", age " + bot.age + ")");
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight goes on");

                ctx.waitAndRun(60, () -> {
                    int apples = count(bot, Items.GOLDEN_APPLE);
                    boolean ate = apples < 32;
                    BossFights.forceEnd(server, Sin.SLOTH);
                    FightBots.leave(bot);
                    ctx.assertTrue(ate, "bot should have eaten a golden apple when low, has " + apples + " left, health " + bot.getHealth());
                    ctx.complete();
                });
            });
        });
    }
}
