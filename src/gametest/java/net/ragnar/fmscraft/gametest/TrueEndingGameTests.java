package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SoulPedestalBlock;
import net.ragnar.fmscraft.ending.CreditsFacts;
import net.ragnar.fmscraft.ending.TrueEnding;
import net.ragnar.fmscraft.network.CreditsPayload;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.List;
import java.util.Map;

/**
 * The True Ending: the Soul Pedestals, the shrine being built, and the game ending when the seventh soul goes in.
 * Each test has its own batch, as they share the world's soul progress.
 */
public class TrueEndingGameTests implements FabricGameTest {

    private static ServerPlayerEntity survivor(TestContext ctx) {
        @SuppressWarnings("deprecation")
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    private static void use(TestContext ctx, ServerPlayerEntity player, BlockPos pos, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ctx.useBlock(pos, player);
    }

    /** A pedestal takes only its own sin's soul, keeps it, and counts it toward the ending. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "true_ending_pedestal")
    public void pedestalTakesOnlyItsOwnSoul(TestContext ctx) {
        FmsWorldState state = FmsWorldState.get(ctx.getWorld().getServer());
        state.clearSouls();
        BlockPos pos = new BlockPos(1, 1, 1);
        ctx.setBlockState(pos, SoulPedestalBlock.of(Sin.GREED, Direction.EAST));
        ServerPlayerEntity player = survivor(ctx);

        ItemStack wrong = new ItemStack(ModItems.SOULS.get(Sin.WRATH));
        use(ctx, player, pos, wrong);
        ctx.assertFalse(ctx.getBlockState(pos).get(SoulPedestalBlock.SOUL), "the Soul of Wrath doesn't fit Greed's pedestal");
        ctx.assertEquals(wrong.getCount(), 1, "the wrong soul is kept");

        ItemStack right = new ItemStack(ModItems.SOULS.get(Sin.GREED));
        use(ctx, player, pos, right);
        ctx.assertTrue(ctx.getBlockState(pos).get(SoulPedestalBlock.SOUL), "the Soul of Greed goes in");
        ctx.assertTrue(right.isEmpty(), "and is used up");
        ctx.assertTrue(state.soulsPlaced().containsKey(Sin.GREED), "it counts toward the True Ending");
        ctx.assertFalse(state.isFinished(), "one soul doesn't end the game");

        ItemStack again = new ItemStack(ModItems.SOULS.get(Sin.GREED));
        use(ctx, player, pos, again);
        ctx.assertEquals(again.getCount(), 1, "a filled pedestal takes no more");
        ctx.assertTrue(ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(pos)) != null, "it has a block entity to draw its beam");
        state.clearSouls();
        ctx.complete();
    }

    /** The seventh soul ends the game: the advancement for everyone, and the win is remembered. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "true_ending_finale", tickLimit = 400)
    public void sevenSoulsEndTheGame(TestContext ctx) {
        FmsWorldState state = FmsWorldState.get(ctx.getWorld().getServer());
        state.clearSouls();
        ServerPlayerEntity player = survivor(ctx);
        for (Sin sin : Sin.values()) {
            BlockPos pos = new BlockPos(sin.ordinal(), 1, 2);
            ctx.setBlockState(pos, SoulPedestalBlock.of(sin, Direction.SOUTH));
            ctx.assertFalse(state.isFinished(), "not over before the " + sin.displayName() + " soul");
            use(ctx, player, pos, new ItemStack(ModItems.SOULS.get(sin)));
        }
        ctx.assertTrue(state.allSoulsPlaced(), "all seven placed");
        ctx.assertTrue(state.isFinished(), "the game is won");
        AdvancementEntry advancement = ctx.getWorld().getServer().getAdvancementLoader().get(TrueEnding.ADVANCEMENT);
        ctx.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "True Ending granted");
        // Past the fireworks and the credits, which mustn't fail without a client
        ctx.waitAndRun(TrueEnding.CREDITS_DELAY + 10, () -> {
            state.clearSouls();
            ctx.complete();
        });
    }

    /**
     * The site finder picks land the right distance from spawn, and the shrine built there has all seven empty
     * pedestals, facing the way asked, with clear sky over them for their beams.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "true_ending_shrine", tickLimit = 200)
    public void shrineIsBuiltWithSevenPedestals(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos spawn = world.getSpawnPos();
        BlockPos site = TrueEnding.findSite(world, spawn);
        double distance = Math.sqrt(Math.pow(site.getX() - spawn.getX(), 2) + Math.pow(site.getZ() - spawn.getZ(), 2));
        ctx.assertTrue(distance >= TrueEnding.MIN_DISTANCE && distance <= TrueEnding.MAX_DISTANCE,
                "site " + Math.round(distance) + " blocks from spawn");
        ctx.assertEquals(TrueEnding.facingToward(new BlockPos(100, 0, 10), BlockPos.ORIGIN), Direction.WEST, "faces back toward spawn");

        // Built here instead, to keep the world round spawn as it is
        BlockPos here = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos center = TrueEnding.place(world, here, Direction.NORTH);
        Map<Sin, BlockPos> pedestals = TrueEnding.pedestals(world, center);
        ctx.assertEquals(pedestals.size(), 7, "seven pedestals");
        for (Map.Entry<Sin, BlockPos> pedestal : pedestals.entrySet()) {
            BlockState state = world.getBlockState(pedestal.getValue());
            ctx.assertFalse(state.get(SoulPedestalBlock.SOUL), pedestal.getKey().displayName() + " starts empty");
            ctx.assertEquals(state.get(SoulPedestalBlock.FACING), Direction.NORTH, pedestal.getKey().displayName() + " faces the way asked");
            ctx.assertTrue(world.isSkyVisible(pedestal.getValue().up()), pedestal.getKey().displayName() + " has open sky for its beam");
        }
        ctx.assertFalse(world.getBlockState(center).isOf(ModBlocks.SIN_PORTAL_FRAME), "no portal frames left in it");
        ctx.complete();
    }

    /** The credits' fun facts come from the players' saved stats: kill a lot of creepers and it shows. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "true_ending_facts")
    public void creditsFactsComeFromSavedStats(TestContext ctx) {
        ServerPlayerEntity player = survivor(ctx);
        String name = player.getGameProfile().getName();
        player.increaseStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME), 20 * 60 * 60 * 3);
        player.increaseStat(Stats.KILLED.getOrCreateStat(EntityType.CREEPER), 1_000_000);
        player.increaseStat(Stats.KILLED.getOrCreateStat(EntityType.TURTLE), 1);

        try {
            List<CreditsFacts.Player> players = CreditsFacts.load(ctx.getWorld().getServer());
            // (Every test's mock player has the same name, so look for this one by what it did)
            ctx.assertTrue(players.stream().anyMatch(p -> p.name().equals(name) && p.stat("killed", "creeper") == 1_000_000L),
                    "an online player's stats are saved and read back");

            List<CreditsPayload.Fact> facts = CreditsFacts.collect(ctx.getWorld().getServer());
            ctx.assertTrue(facts.size() <= CreditsFacts.MAX_FACTS, "at most " + CreditsFacts.MAX_FACTS + " facts");
            ctx.assertTrue(facts.contains(new CreditsPayload.Fact("credits.fmscraft.fact.creepers", List.of(name, "1,000,000"))),
                    "the creeper fact goes to them: " + facts);
            ctx.assertTrue(facts.contains(new CreditsPayload.Fact("credits.fmscraft.fact.turtles.one", List.of(name))),
                    "one turtle gets the singular text: " + facts);
        } finally {
            // Never leave the player behind: other tests' bosses would go for it
            ctx.getWorld().getServer().getPlayerManager().remove(player);
        }
        ctx.complete();
    }
}
