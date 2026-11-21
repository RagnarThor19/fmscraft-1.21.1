package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Ragnars Magic Mod's Tome of TPA: never in our loot, and never into or out of a boss's arena. */
public class TpaGameTests implements FabricGameTest {
    private static final String TPA = "net.ragnar.ragnarsmagicmod.tpa.Tpa";

    private static Item tome() {
        return Registries.ITEM.get(Identifier.of(MagicTomes.MOD_ID, "tome_of_tpa"));
    }

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
        player.getInventory().setStack(0, new ItemStack(tome()));
        return player;
    }

    private static void intoArena(ServerPlayerEntity player) {
        ServerWorld arena = Arenas.world(player.getServer(), Sin.SLOTH);
        Vec3d at = Arenas.layout(Sin.SLOTH).playerSpawn();
        player.teleport(arena, at.x, at.y, at.z, 0f, 0f);
    }

    private static String request(ServerPlayerEntity requester, ServerPlayerEntity target) throws ReflectiveOperationException {
        Method m = Class.forName(TPA).getMethod("request", ServerPlayerEntity.class, ServerPlayerEntity.class);
        return (String) m.invoke(null, requester, target);
    }

    private static String accept(ServerPlayerEntity target) throws ReflectiveOperationException {
        Method m = Class.forName(TPA).getMethod("accept", ServerPlayerEntity.class);
        return (String) m.invoke(null, target);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theTomeOfTpaIsNeverLoot(TestContext ctx) {
        ctx.assertTrue(tome() != Items.AIR, "Ragnars Magic Mod's Tome of TPA should be loaded");
        for (WheelTier tier : WheelTier.values()) {
            ctx.assertFalse(MagicTomes.of(tier).contains(tome()), "the Tome of TPA is in the " + tier + " pool");
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "tpa_arena")
    public void tpaNeverCrossesIntoAnArena(TestContext ctx) throws ReflectiveOperationException {
        MinecraftServer server = ctx.getWorld().getServer();
        ServerPlayerEntity outside = player(ctx, "fs-tpa-out");
        ServerPlayerEntity inside = player(ctx, "fs-tpa-in");
        try {
            intoArena(inside);
            ServerWorld arena = inside.getServerWorld();
            ctx.assertTrue(Arenas.sinOf(arena) == Sin.SLOTH, "should be in Sloth's arena");

            // Asking to go to someone in an arena, or out of one, is refused
            ctx.assertTrue(request(outside, inside) != null, "asked to tpa into an arena");
            ctx.assertTrue(request(inside, outside) != null, "asked to tpa out of an arena");

            // Asked from outside, then went into an arena before it was accepted: they stay put
            inside.teleport(ctx.getWorld(), outside.getX(), outside.getY(), outside.getZ(), 0f, 0f);
            ctx.assertTrue(request(inside, outside) == null, "a request between two players outside should go through");
            intoArena(inside);
            ctx.assertTrue(accept(outside) != null, "accepted a request from someone in an arena");
            ctx.assertTrue(inside.getServerWorld() == arena, "taken out of the arena by /acc");

            // And someone in an arena can't accept one either
            inside.teleport(ctx.getWorld(), outside.getX(), outside.getY(), outside.getZ(), 0f, 0f);
            ctx.assertTrue(request(outside, inside) == null, "a request between two players outside should go through");
            intoArena(inside);
            ctx.assertTrue(accept(inside) != null, "accepted a request while in an arena");
            ctx.assertTrue(outside.getServerWorld() == ctx.getWorld(), "pulled into the arena by /acc");
        } finally {
            server.getPlayerManager().remove(outside);
            server.getPlayerManager().remove(inside);
        }
        ctx.complete();
    }
}
