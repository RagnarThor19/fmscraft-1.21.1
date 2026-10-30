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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void magicModTomesAreFoundForEveryTier(TestContext ctx) {
        for (WheelTier tier : WheelTier.values()) {
            ctx.assertTrue(MagicTomes.of(tier).size() >= 5, "expected Ragnars Magic Mod " + tier + " tomes, found " + MagicTomes.of(tier).size());
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyPortalClosesWithoutAFight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN), "portal should open");
        ctx.assertTrue(world.getBlockState(base.up(2).east()).isOf(ModBlocks.SIN_PORTAL), "2x3 portal placed");
        ctx.assertFalse(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN), "second key while open");

        ctx.runAtTick(JOIN + 5, () -> {
            ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "nobody entered, so no fight");
            ctx.assertTrue(world.getBlockState(base).isAir(), "portal should close");
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void killingTheBossWinsTheFight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BossFights.open(Sin.WRATH, world, base, Direction.Axis.X, JOIN);
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        BossFights.enter(player, Sin.WRATH);

        ctx.runAtTick(JOIN + 10, () -> {
            ServerWorld arena = Arenas.world(server, Sin.WRATH);
            FightSession session = BossFights.session(Sin.WRATH);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the arena");
            ctx.assertTrue(world.getBlockState(base).isAir(), "entry portal should be closed");

            Entity boss = arena.getEntity(session.bossId());
            ctx.assertTrue(boss instanceof SinBossEntity, "boss should be spawned, got " + boss);
            ((LivingEntity) boss).damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);

            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.WRATH), "Wrath should be marked defeated");
            ctx.assertTrue(session.stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(boss.getBlockPos()).expand(3),
                    e -> e.getStack().isOf(ModItems.SOULS.get(Sin.WRATH))).isEmpty(), "Soul of Wrath should drop");
            ctx.assertTrue(arena.getBlockState(Arenas.EXIT_PORTAL).isOf(ModBlocks.SIN_PORTAL), "exit portal should open");
        });

        // The tome wheel pays out (instantly here, since the mock player has no client to animate it)
        ctx.runAtTick(JOIN + 10 + TomeWheel.SPIN_TICKS + 10, () -> {
            int tomes = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                for (WheelTier tier : WheelTier.values()) if (MagicTomes.of(tier).contains(stack.getItem())) tomes++;
            }
            ctx.assertEquals(tomes, 1, "tomes won from the wheel");
            BossFights.forceEnd(server, Sin.WRATH);
            FmsWorldState.get(server).clearDefeated(Sin.WRATH);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aDeathEndsTheFightAndChestsTheLoot(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        BossFights.open(Sin.PRIDE, world, base, Direction.Axis.Z, JOIN);
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        BossFights.enter(player, Sin.PRIDE);

        ctx.runAtTick(JOIN + 10, () -> {
            FightSession session = BossFights.session(Sin.PRIDE);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            Entity boss = Arenas.world(server, Sin.PRIDE).getEntity(session.bossId());

            player.getInventory().insertStack(new ItemStack(Items.DIAMOND_SWORD));
            player.onTeleportationDone(); // a real client confirms the teleport; until then players can't be hurt
            player.damage(player.getDamageSources().outOfWorld(), Float.MAX_VALUE);

            ctx.assertTrue(BossFights.session(Sin.PRIDE) == null, "fight should end when someone dies");
            ctx.assertTrue(boss == null || boss.isRemoved(), "boss should be removed");
            ctx.assertFalse(FmsWorldState.get(server).isDefeated(Sin.PRIDE), "Pride should not count as defeated");

            boolean swordInChest = false;
            for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 3, 7))) {
                if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    for (int i = 0; i < chest.size(); i++) swordInChest |= chest.getStack(i).isOf(Items.DIAMOND_SWORD);
                }
            }
            ctx.assertTrue(swordInChest, "the fallen player's sword should be in a chest by the portal");
            ctx.complete();
        });
    }
}
