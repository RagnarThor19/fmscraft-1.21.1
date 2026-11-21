package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Losing a boss fight must never cost anyone their things: whoever falls has everything they carried put in chests by
 * the portal they came in through, and everyone else is sent safely back out. Real survival players (not creative mock
 * ones), fighting the one-hit stand-in boss in Sloth's arena, one test at a time (they queue for Sloth).
 */
public class DeathSafetyGameTests implements FabricGameTest {
    /** Longer than the 3 seconds a freshly joined player can't be hurt for. */
    private static final int JOIN = 70;
    private static final int LIMIT = 9000;
    private static final java.util.Map<UUID, ClientConnection> CONNECTIONS = new java.util.HashMap<>();
    /** Every player each test made, so they can all be sent off however the test ends. */
    private static final java.util.Map<TestContext, List<ServerPlayerEntity>> PLAYERS = new java.util.IdentityHashMap<>();

    // ---- Helpers ----

    /** A survival player on a connection of its own, standing in the test area. */
    private static ServerPlayerEntity survivor(TestContext ctx, String name) {
        ServerWorld world = ctx.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        CONNECTIONS.put(player.getUuid(), connection);
        PLAYERS.computeIfAbsent(ctx, k -> new ArrayList<>()).add(player);
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 1)));
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static void leave(MinecraftServer server, ServerPlayerEntity... players) {
        for (ServerPlayerEntity p : players) {
            CONNECTIONS.remove(p.getUuid());
            ServerPlayerEntity current = server.getPlayerManager().getPlayer(p.getUuid());
            if (current != null) server.getPlayerManager().remove(current);
        }
    }

    /**
     * Runs {@code body}; if it fails, the test's players leave first (and any fight it left open is ended), so a failed
     * test never leaves players behind to wander into other tests' fights.
     */
    private static Runnable guarded(TestContext ctx, List<ServerPlayerEntity> players, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RuntimeException | Error e) {
                MinecraftServer server = ctx.getWorld().getServer();
                if (BossFights.session(Sin.SLOTH) != null) BossFights.forceEnd(server, Sin.SLOTH);
                leave(server, PLAYERS.getOrDefault(ctx, List.of()).toArray(new ServerPlayerEntity[0]));
                throw e;
            }
        };
    }

    /** {@code body} {@code ticks} from now, guarded like the rest. */
    private static void later(TestContext ctx, int ticks, Runnable body) {
        ctx.waitAndRun(ticks, guarded(ctx, null, body));
    }

    /** Logs {@code player} out the way a real one goes: the connection closes and the server notices. */
    private static void logOut(ServerPlayerEntity player) {
        ClientConnection connection = CONNECTIONS.get(player.getUuid());
        connection.disconnect(Text.literal("left the game"));
        connection.handleDisconnection();
    }

    /** The same item, name and all, allowing for wear (armour takes damage from the killing blow, as in vanilla). */
    private static boolean sameIgnoringWear(ItemStack a, ItemStack b) {
        ItemStack x = a.copy(), y = b.copy();
        if (x.isDamageable()) x.setDamage(0);
        if (y.isDamageable()) y.setDamage(0);
        return ItemStack.areEqual(x, y);
    }

    /** A stone floor under the whole test area, as there would be under any real portal. */
    private static void floor(TestContext ctx) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlockState(x, 0, z, net.minecraft.block.Blocks.STONE);
    }

    private static ItemStack named(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    /**
     * Opens Sloth's portal at {@code base}, sends {@code players} in, and once the fight is on (or, with
     * {@code waitForBoss} false, a few seconds into the wait) runs {@code then} with the arena.
     */
    private static void fight(TestContext ctx, BlockPos base, int joinTicks, boolean waitForBoss, List<ServerPlayerEntity> players,
                              Consumer<ServerWorld> then) {
        floor(ctx);
        FightGameTests.withSloth(ctx, joinTicks + 400, true, guarded(ctx, players, () -> {
            ServerWorld world = ctx.getWorld();
            ctx.assertTrue(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, joinTicks), "portal should open");
            for (ServerPlayerEntity p : players) BossFights.enter(p, Sin.SLOTH);
            int wait = waitForBoss ? joinTicks + 5 : JOIN;
            later(ctx, wait, guarded(ctx, players, () -> {
                ServerWorld arena = Arenas.world(world.getServer(), Sin.SLOTH);
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null, "the fight should be open");
                if (waitForBoss) ctx.assertTrue(session.stage() == FightSession.Stage.ACTIVE, "the fight should be running");
                for (ServerPlayerEntity p : players) {
                    ctx.assertTrue(p.getServerWorld() == arena, p.getName().getString() + " should be in the arena");
                    p.onTeleportationDone(); // a real client confirms the teleport; until then players can't be hurt
                }
                then.accept(arena);
            }));
        }));
    }

    /** A hit from the boss (or plain damage before it has spawned) that kills even through netherite (not so hard it breaks it). */
    private static void killByBoss(ServerWorld arena, ServerPlayerEntity player) {
        FightSession session = BossFights.session(Sin.SLOTH);
        Entity boss = session != null && session.bossId() != null ? arena.getEntity(session.bossId()) : null;
        player.damage(boss instanceof LivingEntity l ? arena.getDamageSources().mobAttack(l) : arena.getDamageSources().generic(), 80f);
    }

    /** Everything in chests around the portal. */
    private static List<ItemStack> chested(ServerWorld world, BlockPos base) {
        List<ItemStack> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 8, 7))) {
            if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.size(); i++) if (!chest.getStack(i).isEmpty()) found.add(chest.getStack(i));
            }
        }
        return found;
    }

    private static boolean hasExactly(List<ItemStack> stacks, ItemStack wanted) {
        for (ItemStack s : stacks) if (sameIgnoringWear(s, wanted)) return true;
        return false;
    }

    /** Any of {@code carried} lying in the arena near {@code where} (the arena is shared, so only this test's things). */
    private static List<ItemStack> lying(ServerWorld arena, Vec3d where, List<ItemStack> carried) {
        List<ItemStack> found = new ArrayList<>();
        for (ItemEntity e : arena.getEntitiesByClass(ItemEntity.class, new Box(where, where).expand(48), e -> true)) {
            if (hasExactly(carried, e.getStack())) found.add(e.getStack());
        }
        return found;
    }

    /** Back by the portal, alive, unhurt, standing in open air on solid ground, and not about to take fall damage. */
    private static void assertSafeHome(TestContext ctx, ServerPlayerEntity p, BlockPos base) {
        assertSafeHome(ctx, p, base, true);
    }

    private static void assertSafeHome(TestContext ctx, ServerPlayerEntity p, BlockPos base, boolean unhurt) {
        String who = p.getName().getString();
        ctx.assertTrue(p.getServerWorld() == ctx.getWorld(), who + " should be back in the overworld, is in " + p.getWorld().getRegistryKey().getValue());
        ctx.assertTrue(p.getPos().distanceTo(Vec3d.ofBottomCenter(base)) < 2.5, who + " should be by the portal, is at " + p.getPos());
        ctx.assertTrue(p.isAlive() && (!unhurt || p.getHealth() == p.getMaxHealth()), who + " should be alive" + (unhurt ? " and unhurt" : "") + ", health " + p.getHealth());
        ctx.assertTrue(ctx.getWorld().isSpaceEmpty(p), who + " is stuck inside a block at " + p.getBlockPos());
        BlockPos below = BlockPos.ofFloored(p.getX(), p.getY() - 0.01, p.getZ());
        ctx.assertTrue(!ctx.getWorld().getBlockState(below).getCollisionShape(ctx.getWorld(), below).isEmpty(),
                who + " should be standing on solid ground, below is " + ctx.getWorld().getBlockState(below));
        ctx.assertTrue(p.fallDistance < 3, who + " carries " + p.fallDistance + " blocks of fall home and would be hurt on landing");
    }

    // ---- The tests ----

    /** A full inventory (41 stacks: armour, offhand, a filled shulker box, names, counts) all comes back, exactly. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_full")
    public void everyStackComesBackExactly(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity fallen = survivor(ctx, "fs-fallen");
        ServerPlayerEntity friend = survivor(ctx, "fs-friend");

        List<ItemStack> carried = new ArrayList<>();
        List<Item> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) if (item != Items.AIR && item.getMaxCount() == 64 && pool.size() < 36) pool.add(item);
        for (int i = 0; i < 36; i++) {
            ItemStack stack = named(pool.get(i), 1 + i, "slot " + i);
            if (i == 7) {
                stack = new ItemStack(Items.SHULKER_BOX);
                stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.DIAMOND_BLOCK, 64), new ItemStack(Items.NETHERITE_INGOT, 9))));
            }
            fallen.getInventory().setStack(i, stack);
            carried.add(stack.copy());
        }
        ItemStack[] armour = {named(Items.NETHERITE_HELMET, 1, "helm"), named(Items.NETHERITE_CHESTPLATE, 1, "chest"),
                named(Items.NETHERITE_LEGGINGS, 1, "legs"), named(Items.NETHERITE_BOOTS, 1, "boots")};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            armour[i].setDamage(100 + i);
            fallen.equipStack(slots[i], armour[i]);
            carried.add(armour[i].copy());
        }
        ItemStack shield = named(Items.SHIELD, 1, "offhand");
        fallen.equipStack(EquipmentSlot.OFFHAND, shield);
        carried.add(shield.copy());
        friend.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));

        fight(ctx, base, JOIN, true, List.of(fallen, friend), arena -> {
            Vec3d where = fallen.getPos();
            killByBoss(arena, fallen);
            ctx.assertTrue(fallen.isDead(), "the fallen player should be dead");
            ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "a death ends the fight");
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                for (ItemStack want : carried) {
                    ctx.assertTrue(hasExactly(chests, want), "missing from the chests: " + want + " " + want.getComponents());
                }
                ctx.assertEquals(chests.size(), carried.size(), "stacks in the chests");
                assertSafeHome(ctx, friend, base);
                ctx.assertTrue(friend.getInventory().count(Items.DIAMOND) == 3, "the survivor keeps their own things");

                ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(fallen, false, Entity.RemovalReason.KILLED);
                later(ctx, 2, () -> {
                    ctx.assertTrue(respawned.getServerWorld() != arena, "the fallen player respawns outside the arena");
                    List<ItemStack> left = lying(arena, where, carried);
                    leave(server, respawned, friend);
                    ctx.assertTrue(left.isEmpty(), "left lying in the arena: " + left);
                    ctx.complete();
                });
            });
        });
    }

    /** What's on the cursor and in the 2x2 crafting grid (inventory open mid-fight) is the player's too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_cursor")
    public void cursorAndCraftingGridAreNotLost(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity fallen = survivor(ctx, "fs-cursor");
        fallen.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));

        fight(ctx, base, JOIN, true, List.of(fallen), arena -> {
            // Swapping armour mid-fight: a chestplate on the cursor, golden apples and a totem in the crafting grid
            fallen.playerScreenHandler.setCursorStack(new ItemStack(Items.NETHERITE_CHESTPLATE));
            fallen.playerScreenHandler.getCraftingInput().setStack(0, new ItemStack(Items.GOLDEN_APPLE, 12));
            fallen.playerScreenHandler.getCraftingInput().setStack(3, new ItemStack(Items.TOTEM_OF_UNDYING));
            Vec3d where = fallen.getPos();
            killByBoss(arena, fallen);
            ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(fallen, false, Entity.RemovalReason.KILLED);
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                List<ItemStack> left = lying(arena, where, List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_CHESTPLATE),
                        new ItemStack(Items.GOLDEN_APPLE, 12), new ItemStack(Items.TOTEM_OF_UNDYING)));
                leave(server, respawned);
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.DIAMOND_SWORD)), "the sword should be chested");
                ctx.assertTrue(left.isEmpty(), "left lying in the arena where nobody can reach it: " + left);
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.NETHERITE_CHESTPLATE)), "the chestplate on the cursor should be chested");
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.GOLDEN_APPLE, 12)), "the apples in the crafting grid should be chested");
                ctx.complete();
            });
        });
    }

    /** A totem can't save anyone from the void (or /kill): holding one mustn't stop their things being chested. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_void_totem")
    public void holdingATotemIntoTheVoidStillChests(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity fallen = survivor(ctx, "fs-void");
        ServerPlayerEntity friend = survivor(ctx, "fs-voidfriend");
        fallen.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        fallen.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        fight(ctx, base, JOIN, true, List.of(fallen, friend), arena -> {
            Vec3d where = fallen.getPos();
            fallen.damage(arena.getDamageSources().outOfWorld(), 10_000f);
            ctx.assertTrue(fallen.isDead(), "a totem doesn't work against the void");
            ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(fallen, false, Entity.RemovalReason.KILLED);
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                List<ItemStack> left = lying(arena, where, List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.TOTEM_OF_UNDYING)));
                boolean fightOver = BossFights.session(Sin.SLOTH) == null;
                if (!fightOver) BossFights.forceEnd(server, Sin.SLOTH);
                leave(server, respawned, friend);
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.DIAMOND_SWORD)), "the sword should be chested, lying in the arena: " + left);
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.TOTEM_OF_UNDYING)), "the unused totem should be chested");
                ctx.assertTrue(fightOver, "the death should end the fight");
                ctx.complete();
            });
        });
    }

    /** A totem that does save someone: no death, so nothing is taken and the fight goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_totem")
    public void aTotemThatSavesYouKeepsTheFightGoing(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity saved = survivor(ctx, "fs-totem");
        saved.getInventory().setStack(1, new ItemStack(Items.DIAMOND_SWORD));
        saved.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        fight(ctx, base, JOIN, true, List.of(saved), arena -> {
            killByBoss(arena, saved);
            ctx.assertTrue(saved.isAlive(), "the totem should save them");
            FightSession session = BossFights.session(Sin.SLOTH);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "nobody died, so the fight goes on");
            ctx.assertTrue(saved.getInventory().count(Items.DIAMOND_SWORD) == 1, "nothing taken from them");
            ctx.assertTrue(chested(ctx.getWorld(), base).isEmpty(), "no chests for a death that didn't happen");
            BossFights.forceEnd(server, Sin.SLOTH);
            later(ctx, 3, () -> {
                assertSafeHome(ctx, saved, base, false);
                leave(server, saved);
                ctx.complete();
            });
        });
    }

    /** Ragnars Magic Mod's Tome of Living cancels a death: then it must be as if nobody died. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_living")
    public void theTomeOfLivingNeverLeavesAnyoneStranded(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        Item living = Registries.ITEM.get(Identifier.of("ragnarsmagicmod", "tome_of_living"));
        ctx.assertTrue(living != Items.AIR, "Ragnars Magic Mod's Tome of Living should be loaded");
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivor(ctx, "fs-living");
        ServerPlayerEntity friend = survivor(ctx, "fs-livingfriend");
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().setStack(5, new ItemStack(living));

        fight(ctx, base, JOIN, true, List.of(player, friend), arena -> {
            killByBoss(arena, player);
            boolean alive = player.isAlive();
            boolean fightOn = BossFights.session(Sin.SLOTH) != null;
            int swords = player.getInventory().count(Items.DIAMOND_SWORD);
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                boolean playerInArena = player.getServerWorld() == arena;
                if (BossFights.session(Sin.SLOTH) != null) BossFights.forceEnd(server, Sin.SLOTH);
                later(ctx, 3, () -> {
                    leave(server, player, friend);
                    String state = "alive=" + alive + ", fight still on=" + fightOn + ", swords on them=" + swords
                            + ", sword in a chest=" + hasExactly(chests, new ItemStack(Items.DIAMOND_SWORD)) + ", still in the arena=" + playerInArena;
                    if (alive) {
                        ctx.assertTrue(fightOn && swords == 1, "saved by the tome, but the fight was ended or their things taken: " + state);
                    } else {
                        ctx.assertTrue(!fightOn && hasExactly(chests, new ItemStack(Items.DIAMOND_SWORD)), "died, but not handled as a death: " + state);
                    }
                    ctx.complete();
                });
            });
        });
    }

    /** Players a stand-in "other mod" (registered after FMSCraft, the order that used to go wrong) saves from death. */
    private static final java.util.Set<UUID> SAVED_BY_ANOTHER_MOD = new java.util.HashSet<>();
    private static boolean otherModRegistered;

    /** Any mod may cancel a death, loading before or after FMSCraft: then nothing is chested and the fight goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_other_mod")
    public void aDeathAnotherModCancelsChangesNothing(TestContext ctx) {
        if (!otherModRegistered) {
            otherModRegistered = true;
            net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
                if (!SAVED_BY_ANOTHER_MOD.remove(entity.getUuid())) return true;
                ((LivingEntity) entity).setHealth(10f);
                return false;
            });
        }
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivor(ctx, "fs-othermod");
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));

        fight(ctx, base, JOIN, true, List.of(player), arena -> {
            player.playerScreenHandler.setCursorStack(new ItemStack(Items.GOLDEN_APPLE, 4)); // inventory open mid-fight
            SAVED_BY_ANOTHER_MOD.add(player.getUuid());
            killByBoss(arena, player);
            ctx.assertTrue(player.isAlive(), "the other mod should have saved them");
            FightSession session = BossFights.session(Sin.SLOTH);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "nobody died, so the fight goes on");
            ctx.assertTrue(player.getInventory().count(Items.DIAMOND_SWORD) == 1, "nothing taken from them");
            ctx.assertTrue(player.playerScreenHandler.getCursorStack().getCount() == 4, "not even what's on the cursor");
            ctx.assertTrue(chested(ctx.getWorld(), base).isEmpty(), "no chests for a death that didn't happen");
            BossFights.forceEnd(server, Sin.SLOTH);
            later(ctx, 3, () -> {
                assertSafeHome(ctx, player, base, false);
                leave(server, player);
                ctx.complete();
            });
        });
    }

    /** Two fall in the same tick (one big hit): both inventories chested, the third sent home. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_two")
    public void twoFallingAtOnceBothGetChests(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity a = survivor(ctx, "fs-a");
        ServerPlayerEntity b = survivor(ctx, "fs-b");
        ServerPlayerEntity c = survivor(ctx, "fs-c");
        a.getInventory().setStack(0, named(Items.DIAMOND_SWORD, 1, "a's sword"));
        b.getInventory().setStack(0, named(Items.NETHERITE_AXE, 1, "b's axe"));
        b.equipStack(EquipmentSlot.HEAD, named(Items.DIAMOND_HELMET, 1, "b's helmet"));

        fight(ctx, base, JOIN, true, List.of(a, b, c), arena -> {
            Vec3d where = a.getPos();
            killByBoss(arena, a);
            killByBoss(arena, b);
            ctx.assertTrue(a.isDead() && b.isDead(), "both should be dead");
            later(ctx, 3, () -> {
                ServerPlayerEntity ra = server.getPlayerManager().respawnPlayer(a, false, Entity.RemovalReason.KILLED);
                ServerPlayerEntity rb = server.getPlayerManager().respawnPlayer(b, false, Entity.RemovalReason.KILLED);
                later(ctx, 2, () -> {
                    List<ItemStack> chests = chested(ctx.getWorld(), base);
                    List<ItemStack> left = lying(arena, where, List.of(named(Items.DIAMOND_SWORD, 1, "a's sword"),
                            named(Items.NETHERITE_AXE, 1, "b's axe"), named(Items.DIAMOND_HELMET, 1, "b's helmet")));
                    boolean safe = ra.getServerWorld() != arena && rb.getServerWorld() != arena;
                    assertSafeHome(ctx, c, base);
                    leave(server, ra, rb, c);
                    ctx.assertTrue(hasExactly(chests, named(Items.DIAMOND_SWORD, 1, "a's sword")), "a's sword should be chested");
                    ctx.assertTrue(hasExactly(chests, named(Items.NETHERITE_AXE, 1, "b's axe")), "b's axe should be chested");
                    ctx.assertTrue(hasExactly(chests, named(Items.DIAMOND_HELMET, 1, "b's helmet")), "b's helmet should be chested");
                    ctx.assertTrue(left.isEmpty(), "left lying in the arena: " + left);
                    ctx.assertTrue(safe, "both respawn outside the arena");
                    ctx.complete();
                });
            });
        });
    }

    /** Logging out mid-fight counts as falling: their things are chested (so the save is empty) and the rest go home. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_logout")
    public void loggingOutChestsTheirThingsAndSendsTheRestHome(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity quitter = survivor(ctx, "fs-quit");
        ServerPlayerEntity friend = survivor(ctx, "fs-quitfriend");
        quitter.getInventory().setStack(0, named(Items.DIAMOND_PICKAXE, 1, "quitter's pick"));
        quitter.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));

        fight(ctx, base, JOIN, true, List.of(quitter, friend), arena -> {
            logOut(quitter);
            ctx.assertTrue(server.getPlayerManager().getPlayer(quitter.getUuid()) == null, "they should be gone");
            ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "logging out ends the fight");
            ctx.assertTrue(quitter.getInventory().isEmpty(), "nothing left on them (so nothing is saved with them to duplicate)");
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                assertSafeHome(ctx, friend, base);
                leave(server, friend);
                ctx.assertTrue(hasExactly(chests, named(Items.DIAMOND_PICKAXE, 1, "quitter's pick")), "the pickaxe should be chested");
                ctx.assertTrue(hasExactly(chests, new ItemStack(Items.ELYTRA)), "the elytra should be chested");
                ctx.complete();
            });
        });
    }

    /** Dying while still waiting for the boss: the portal shuts, the loot is chested, everyone else goes home. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_waiting")
    public void dyingBeforeTheBossArrivesIsHandledToo(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity fallen = survivor(ctx, "fs-early");
        ServerPlayerEntity friend = survivor(ctx, "fs-earlyfriend");
        fallen.getInventory().setStack(0, named(Items.BOW, 1, "early bow"));

        fight(ctx, base, 600, false, List.of(fallen, friend), arena -> {
            ctx.assertTrue(BossFights.session(Sin.SLOTH).stage() == FightSession.Stage.JOINING, "still waiting for the boss");
            Vec3d where = fallen.getPos();
            killByBoss(arena, fallen);
            ctx.assertTrue(BossFights.session(Sin.SLOTH) == null, "the attempt is over");
            ctx.assertTrue(ctx.getWorld().getBlockState(base.up()).isAir(), "the entry portal shuts");
            ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(fallen, false, Entity.RemovalReason.KILLED);
            later(ctx, 3, () -> {
                List<ItemStack> chests = chested(ctx.getWorld(), base);
                List<ItemStack> left = lying(arena, where, List.of(named(Items.BOW, 1, "early bow")));
                assertSafeHome(ctx, friend, base);
                leave(server, respawned, friend);
                ctx.assertTrue(hasExactly(chests, named(Items.BOW, 1, "early bow")), "the bow should be chested");
                ctx.assertTrue(left.isEmpty(), "left lying in the arena: " + left);
                ctx.complete();
            });
        });
    }

    /** Sent home mid-fall (knocked off a ledge as the fight ended): they mustn't land at the portal with the fall still on them. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = LIMIT, batchId = "death_safety_falling")
    public void sentHomeMidFallLandsSafely(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        ServerPlayerEntity fallen = survivor(ctx, "fs-fall");
        ServerPlayerEntity faller = survivor(ctx, "fs-faller");

        fight(ctx, base, JOIN, true, List.of(fallen, faller), arena -> {
            faller.fallDistance = 60; // well past a deadly fall
            faller.setOnFire(true);
            faller.setFireTicks(200);
            killByBoss(arena, fallen);
            ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(fallen, false, Entity.RemovalReason.KILLED);
            later(ctx, 3, () -> {
                float fall = faller.fallDistance;
                boolean home = faller.getServerWorld() == ctx.getWorld();
                leave(server, respawned, faller);
                ctx.assertTrue(home, "they should be sent home");
                ctx.assertTrue(fall < 3, "sent home still carrying " + fall + " blocks of fall: the first step at the portal would hurt or kill them");
                ctx.complete();
            });
        });
    }
}
