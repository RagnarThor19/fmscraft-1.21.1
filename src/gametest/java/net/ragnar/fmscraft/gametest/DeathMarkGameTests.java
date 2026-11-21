package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.DeathMarks;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.Optional;
import java.util.UUID;

/** DeathMarks: dying and leaving your things lying marks the spot until you're back; nothing for boss arenas. */
public class DeathMarkGameTests implements FabricGameTest {
    private static ServerPlayerEntity player(TestContext ctx, String name) {
        ServerWorld world = ctx.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 1)));
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static void kill(ServerPlayerEntity player) {
        player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE); // gets past the few seconds a player who just joined can't be hurt
    }

    private static Optional<GlobalPos> mark(ServerPlayerEntity player) {
        return FmsWorldState.get(player.getServer()).getDeathMark(player.getUuid());
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "death_marks")
    public void dyingWithThingsMarksTheSpot(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerPlayerEntity carrying = player(ctx, "fs-mark-full");
        ServerPlayerEntity empty = player(ctx, "fs-mark-empty");
        // Not dying next to the other one's things
        Vec3d away = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(7, 1, 7)));
        empty.refreshPositionAndAngles(away.x, away.y, away.z, 0, 0);
        try {
            carrying.getInventory().setStack(0, new ItemStack(Items.IRON_PICKAXE));
            BlockPos where = carrying.getBlockPos();
            kill(carrying);
            kill(empty);
            ctx.assertTrue(carrying.isDead() && empty.isDead(), "they should have died");
            ctx.assertTrue(mark(carrying).equals(Optional.of(GlobalPos.create(ctx.getWorld().getRegistryKey(), where))),
                    "the spot they died should be marked, got " + mark(carrying));
            ctx.assertTrue(mark(empty).isEmpty(), "nothing dropped, so nothing to find");
        } finally {
            FmsWorldState.get(server).clearDeathMark(carrying.getUuid());
            server.getPlayerManager().remove(carrying);
            server.getPlayerManager().remove(empty);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "death_marks_arena")
    public void noMarkInABossArena(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerPlayerEntity player = player(ctx, "fs-mark-arena");
        try {
            ServerWorld arena = Arenas.world(server, Sin.SLOTH);
            Vec3d at = Arenas.layout(Sin.SLOTH).playerSpawn();
            // Outside a fight, a death in an arena chests their things by where they came from: here
            FmsWorldState.get(server).setReturn(player.getUuid(), GlobalPos.create(ctx.getWorld().getRegistryKey(), ctx.getAbsolutePos(new BlockPos(3, 1, 3))));
            player.teleport(arena, at.x, at.y, at.z, 0f, 0f);
            player.onTeleportationDone();
            player.getInventory().setStack(0, new ItemStack(Items.IRON_PICKAXE));
            kill(player);
            ctx.assertTrue(player.isDead(), "they should have died");
            ctx.assertTrue(mark(player).isEmpty(), "a death in a boss's arena shouldn't leave a mark");
        } finally {
            FmsWorldState.get(server).clearDeathMark(player.getUuid());
            FmsWorldState.get(server).clearReturn(player.getUuid());
            server.getPlayerManager().remove(player);
        }
        ctx.complete();
    }

    /** Dies in {@code world} at x, z (on the ground) with a pickaxe, and returns where. */
    private static BlockPos dieIn(ServerPlayerEntity player, ServerWorld world, int x, int z) {
        world.getChunk(x >> 4, z >> 4); // generated and loaded, so there's ground to stand (and be buried) on
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
        if (y <= world.getBottomY()) y = 80;
        player.teleport(world, x + 0.5, y, z + 0.5, 0f, 0f);
        player.onTeleportationDone();
        player.getInventory().setStack(0, new ItemStack(Items.IRON_PICKAXE));
        BlockPos where = player.getBlockPos();
        kill(player);
        return where;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "death_marks_twilight")
    public void aGraveInTheTwilightIsMarkedToo(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerWorld twilight = server.getWorld(Sin.SinWorlds.TWILIGHT);
        ctx.assertTrue(twilight != null, "the twilight dimension should be loaded");
        ServerPlayerEntity player = player(ctx, "fs-mark-twilight");
        try {
            BlockPos where = dieIn(player, twilight, 3000, 3000);
            ctx.assertTrue(player.isDead(), "they should have died");
            StringBuilder chests = new StringBuilder();
            for (BlockPos pos : BlockPos.iterate(where.add(-10, -10, -10), where.add(10, 10, 10))) {
                if (twilight.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity) chests.append(pos.toShortString()).append("; ");
            }
            int items = twilight.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(where).expand(10), e -> true).size();
            ctx.assertTrue(chests.length() > 0, "the twilight should have buried them where they fell (died at " + where.toShortString()
                    + ", chests: " + chests + " items lying: " + items + ", block below " + twilight.getBlockState(where.down()) + ")");
            ctx.assertTrue(mark(player).equals(Optional.of(GlobalPos.create(Sin.SinWorlds.TWILIGHT, where))),
                    "their grave should be marked, got " + mark(player));
        } finally {
            FmsWorldState.get(server).clearDeathMark(player.getUuid());
            server.getPlayerManager().remove(player);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "death_marks_end")
    public void aDeathInTheEndIsMarked(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerPlayerEntity player = player(ctx, "fs-mark-end");
        try {
            BlockPos where = dieIn(player, server.getWorld(net.minecraft.world.World.END), 0, 0);
            ctx.assertTrue(player.isDead(), "they should have died");
            ctx.assertTrue(mark(player).equals(Optional.of(GlobalPos.create(net.minecraft.world.World.END, where))),
                    "the spot in the End should be marked, got " + mark(player));
        } finally {
            FmsWorldState.get(server).clearDeathMark(player.getUuid());
            server.getPlayerManager().remove(player);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "death_marks_back")
    public void theMarkGoesOnceYoureBack(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerPlayerEntity player = player(ctx, "fs-mark-back");
        BlockPos spot = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
        FmsWorldState.get(server).setDeathMark(player.getUuid(), GlobalPos.create(ctx.getWorld().getRegistryKey(), spot));
        ctx.waitAndRun(25, () -> {
            boolean keptWhileAway = mark(player).isPresent();
            player.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 1.5, 0, 0);
            ctx.waitAndRun(25, () -> {
                boolean gone = mark(player).isEmpty();
                FmsWorldState.get(server).clearDeathMark(player.getUuid());
                server.getPlayerManager().remove(player);
                ctx.assertTrue(keptWhileAway, "the mark went while they were still away from it");
                ctx.assertTrue(gone, "the mark should go once they're back by their things");
                ctx.complete();
            });
        });
    }
}
