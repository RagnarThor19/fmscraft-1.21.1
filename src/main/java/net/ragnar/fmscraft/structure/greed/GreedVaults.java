package net.ragnar.fmscraft.structure.greed;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.GreedStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.network.VaultFogPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What happens in and around a Greed Vault:
 * <ul>
 *   <li>Anyone down in its rooms (not up the pipe, so you can build up to the pipe to get in) has Mining Fatigue X,
 *       Slowness II and Block Barrier, so there's no digging, pillaring or walling off: you solve it on foot. They
 *       also see it through thick dark fog ({@link VaultFogPayload}).</li>
 *   <li><b>The levers</b>: when exactly levers 1, 5 and 7 are on ({@link GreedVaultLayout#CODE}, from the clue books),
 *       the vault is solved: a fanfare, the effects lift, the statues crumble away and the 4x4 iron door sinks into
 *       the floor a row at a time. Any other combination does nothing. It stays solved until the vault resets.</li>
 *   <li><b>The Statues of Greed</b>: three, raised from the smooth stone in the floor once someone is within
 *       {@link #ACTIVE_MARGIN} blocks, hunting whoever is inside ({@link GreedStatueEntity}).</li>
 *   <li><b>Resetting</b>: once nobody is within {@link #RESET_MARGIN} blocks (and no Greed fight is on), the statues
 *       sink back into their stones, the door closes, the levers go back off and any clue book that was taken
 *       reappears in its chest, so the next group finds the vault as it was built.</li>
 * </ul>
 * Nothing is saved: everything here is read back from the world, so a restart changes nothing.
 */
public final class GreedVaults {
    public static final int ACTIVE_MARGIN = 16;
    public static final int RESET_MARGIN = 48;
    /** Mining Fatigue X, Slowness II (amplifiers are one less). */
    public static final int FATIGUE = 9, SLOWNESS = 1;
    private static final int UPDATE_TICKS = 10;
    private static final int SCAN_TICKS = 20;
    private static final int SCAN_CHUNKS = 7;
    /** New statues won't rise out of a stone someone is standing this close to. */
    private static final double HOME_CLEARANCE = 4;
    public static final RegistryKey<Structure> VAULT = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "greed_vault"));

    /** A vault someone is near. */
    public static final class Vault {
        final ServerWorld world;
        final GreedVaultPiece piece;
        final UUID[] statues = new UUID[GreedVaultLayout.STATUE_HOMES.size()];
        /** The next door row to sink (0 = top), or -1 while the door isn't opening. */
        int opening = -1;
        boolean wasActive;
        /** Solved (the door was opened): no effects, no statues, until the vault resets. Null until first checked. */
        Boolean solved;
        private List<GreedVaultPiece.Clue> clues;

        Vault(ServerWorld world, GreedVaultPiece piece) {
            this.world = world;
            this.piece = piece;
        }

        public GreedVaultPiece piece() {
            return piece;
        }

        public BlockBox box() {
            return piece.getBoundingBox();
        }

        List<GreedVaultPiece.Clue> clues() {
            if (clues == null) clues = piece.clues(world.getRegistryManager());
            return clues;
        }
    }

    private record Key(RegistryKey<World> world, long id) {
    }

    private static final Map<Key, Vault> VAULTS = new HashMap<>();
    /** Players down in a vault right now, and those whose client was last told so. */
    private static final Set<UUID> IN_DARKNESS = new HashSet<>();
    private static final Set<UUID> TOLD_DARK = new HashSet<>();

    private GreedVaults() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GreedVaults::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VAULTS.clear();
            IN_DARKNESS.clear();
            TOLD_DARK.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD_DARK.remove(handler.player.getUuid()));
    }

    // ---- Finding vaults ----

    private static Structure structure(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(VAULT);
    }

    /** The vault whose structure covers {@code pos}, or null. */
    public static GreedVaultPiece pieceAt(ServerWorld world, BlockPos pos) {
        Structure structure = structure(world);
        if (structure == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
        return start.hasChildren() && start.getChildren().get(0) instanceof GreedVaultPiece piece ? piece : null;
    }

    public static Vault vault(ServerWorld world, GreedVaultPiece piece) {
        return VAULTS.computeIfAbsent(new Key(world.getRegistryKey(), piece.id()), k -> new Vault(world, piece));
    }

    /** Looks round every player for vaults. Ones nobody is near any more are forgotten. */
    private static void scan(MinecraftServer server) {
        Map<Key, Vault> found = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) continue;
            ServerWorld world = player.getServerWorld();
            Structure structure = structure(world);
            if (structure == null) continue;
            ChunkPos at = player.getChunkPos();
            for (int cx = at.x - SCAN_CHUNKS; cx <= at.x + SCAN_CHUNKS; cx++) {
                for (int cz = at.z - SCAN_CHUNKS; cz <= at.z + SCAN_CHUNKS; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    for (StructureStart start : world.getStructureAccessor().getStructureStarts(ChunkSectionPos.from(new ChunkPos(cx, cz), 0), structure)) {
                        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof GreedVaultPiece piece)) continue;
                        Key key = new Key(world.getRegistryKey(), piece.id());
                        found.computeIfAbsent(key, k -> VAULTS.containsKey(k) ? VAULTS.get(k) : new Vault(world, piece));
                    }
                }
            }
        }
        // Everyone left at once (teleported away): its statues sink now rather than whenever the chunks unload
        for (Map.Entry<Key, Vault> entry : VAULTS.entrySet()) {
            if (!found.containsKey(entry.getKey())) sinkStatues(entry.getValue());
        }
        VAULTS.clear();
        VAULTS.putAll(found);
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % SCAN_TICKS == 0) scan(server);
        if (server.getTicks() % UPDATE_TICKS != 0) return;
        IN_DARKNESS.clear();
        for (Vault vault : List.copyOf(VAULTS.values())) update(vault);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean dark = IN_DARKNESS.contains(player.getUuid());
            if (dark == TOLD_DARK.contains(player.getUuid())) continue;
            if (dark) TOLD_DARK.add(player.getUuid());
            else TOLD_DARK.remove(player.getUuid());
            if (ServerPlayNetworking.canSend(player, VaultFogPayload.ID)) ServerPlayNetworking.send(player, new VaultFogPayload(dark));
        }
    }

    /** Whether this player is down in a vault (and sees its fog). */
    public static boolean inDarkness(ServerPlayerEntity player) {
        return IN_DARKNESS.contains(player.getUuid());
    }

    /** Every half second, for each vault someone is near. */
    public static void update(Vault vault) {
        ServerWorld world = vault.world;
        BlockBox box = vault.box();
        if (vault.solved == null && loaded(vault, vault.piece.door())) vault.solved = doorOpen(vault); // after a restart
        boolean solved = Boolean.TRUE.equals(vault.solved);
        boolean active = false, nearby = false;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (box.expand(RESET_MARGIN).contains(at)) nearby = true;
            if (box.expand(ACTIVE_MARGIN).contains(at)) active = true;
            if (!vault.piece.inBody(at)) continue;
            IN_DARKNESS.add(player.getUuid());
            if (!player.isCreative() && !solved) {
                refresh(player, StatusEffects.MINING_FATIGUE, FATIGUE);
                refresh(player, StatusEffects.SLOWNESS, SLOWNESS);
                refresh(player, ModEffects.BLOCK_BARRIER, 0);
            }
        }

        if (active && !solved) {
            ensureStatues(vault);
            if (vault.opening < 0 && doorShut(vault)) {
                if (codeEntered(vault)) solve(vault, true);
                else if (someoneInside(vault)) solve(vault, false);
            }
        } else if (vault.wasActive) {
            sinkStatues(vault);
        }
        vault.wasActive = active;
        if (vault.opening >= 0) sinkDoorRow(vault);
        if (!nearby && BossFights.session(Sin.GREED) == null) reset(vault);
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(type);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 40) {
            player.addStatusEffect(new StatusEffectInstance(type, 80, amplifier, true, false, true));
        }
    }

    // ---- The levers and the door ----

    /** Which levers (1-8) are on. A lever that's gone counts as off. */
    public static Set<Integer> leversOn(Vault vault) {
        Set<Integer> on = new HashSet<>();
        List<BlockPos> levers = vault.piece.levers();
        for (int i = 0; i < levers.size(); i++) {
            BlockState state = vault.world.getBlockState(levers.get(i));
            if (state.isOf(Blocks.LEVER) && state.get(LeverBlock.POWERED)) on.add(i + 1);
        }
        return on;
    }

    private static boolean loaded(Vault vault, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) if (!vault.world.isChunkLoaded(pos)) return false;
        return true;
    }

    public static boolean codeEntered(Vault vault) {
        return loaded(vault, vault.piece.levers()) && GreedVaultLayout.opens(leversOn(vault));
    }

    public static boolean doorShut(Vault vault) {
        if (!loaded(vault, vault.piece.door())) return false;
        for (BlockPos pos : vault.piece.door()) if (vault.world.getBlockState(pos).isOf(Blocks.IRON_BLOCK)) return true;
        return false;
    }

    public static boolean doorOpen(Vault vault) {
        if (!loaded(vault, vault.piece.door())) return false;
        for (BlockPos pos : vault.piece.door()) if (!vault.world.getBlockState(pos).isAir()) return false;
        return true;
    }

    /** Someone behind the door while it's shut, who'd otherwise be stuck in there. */
    private static boolean someoneInside(Vault vault) {
        for (ServerPlayerEntity player : vault.world.getPlayers()) {
            if (!player.isSpectator() && vault.box().contains(player.getBlockPos())
                    && GreedVaultLayout.inner(vault.piece.toLocal(player.getBlockPos()))) return true;
        }
        return false;
    }

    /**
     * The vault gives in: the effects lift, the statues crumble and the door starts to sink. With {@code earned} (the
     * code was entered, rather than someone being let out) everyone in it hears the fanfare.
     */
    private static void solve(Vault vault, boolean earned) {
        vault.solved = true;
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SMOOTH_STONE.getDefaultState());
        for (GreedStatueEntity statue : statues(vault)) {
            vault.world.spawnParticles(dust, statue.getX(), statue.getBodyY(0.5), statue.getZ(), 40, 0.3, 0.8, 0.3, 0.1);
        }
        sinkStatues(vault);
        for (ServerPlayerEntity player : vault.world.getPlayers()) {
            if (!vault.box().expand(ACTIVE_MARGIN).contains(player.getBlockPos())) continue;
            for (RegistryEntry<StatusEffect> type : List.of(StatusEffects.MINING_FATIGUE, StatusEffects.SLOWNESS, ModEffects.BLOCK_BARRIER)) {
                StatusEffectInstance current = player.getStatusEffect(type);
                if (current != null && current.isAmbient()) player.removeStatusEffect(type);
            }
            if (earned) player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        startOpening(vault);
    }

    private static void startOpening(Vault vault) {
        vault.opening = 0;
        BlockPos middle = middleOfDoor(vault);
        vault.world.playSound(null, middle, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 2.0f, 0.5f);
        vault.world.playSound(null, middle, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.5f);
        Text message = Text.translatable("message.fmscraft.greed_door_open").withColor(0xE3C25B);
        for (ServerPlayerEntity player : vault.world.getPlayers()) {
            if (vault.box().expand(ACTIVE_MARGIN).contains(player.getBlockPos())) player.sendMessage(message, true);
        }
    }

    /** The door sinks into the floor one row at a time, top row first. */
    private static void sinkDoorRow(Vault vault) {
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.getDefaultState());
        for (BlockPos pos : vault.piece.doorRow(vault.opening)) {
            if (!vault.world.getBlockState(pos).isOf(Blocks.IRON_BLOCK)) continue;
            vault.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            vault.world.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        vault.world.playSound(null, middleOfDoor(vault), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2f, 0.5f);
        vault.opening++;
        if (vault.opening >= GreedVaultLayout.DOOR_SIZE) {
            vault.opening = -1;
            vault.world.playSound(null, middleOfDoor(vault), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.5f, 0.4f);
        }
    }

    private static BlockPos middleOfDoor(Vault vault) {
        List<BlockPos> door = vault.piece.door();
        return door.get(door.size() / 2);
    }

    // ---- Statues ----

    private static GreedStatueEntity statue(Vault vault, int i) {
        UUID id = vault.statues[i];
        return id != null && vault.world.getEntity(id) instanceof GreedStatueEntity statue && statue.isAlive() ? statue : null;
    }

    public static List<GreedStatueEntity> statues(Vault vault) {
        List<GreedStatueEntity> statues = new ArrayList<>();
        for (int i = 0; i < vault.statues.length; i++) {
            GreedStatueEntity statue = statue(vault, i);
            if (statue != null) statues.add(statue);
        }
        return statues;
    }

    /**
     * Any statue that's missing rises from its stone, unless someone is standing right on it. One still standing
     * about from before (the vault was forgotten while its chunks stayed loaded) is taken back instead.
     */
    private static void ensureStatues(Vault vault) {
        List<BlockPos> homes = vault.piece.statueHomes();
        for (int i = 0; i < homes.size(); i++) {
            if (statue(vault, i) != null) continue;
            BlockPos home = homes.get(i);
            // Not until entities can live there: one put in before then is lost track of, and another put in its place
            if (!vault.world.shouldTickEntity(home)) continue;
            List<GreedStatueEntity> left = vault.world.getEntitiesByClass(GreedStatueEntity.class, Box.from(vault.box()).expand(4),
                    s -> s.isAlive() && home.equals(s.getHome()));
            if (!left.isEmpty()) {
                vault.statues[i] = left.get(0).getUuid();
                for (GreedStatueEntity extra : left.subList(1, left.size())) extra.discard();
                continue;
            }
            boolean crowded = vault.world.getPlayers().stream()
                    .anyMatch(p -> !p.isSpectator() && p.squaredDistanceTo(home.toCenterPos()) < HOME_CLEARANCE * HOME_CLEARANCE);
            if (crowded) continue;
            GreedStatueEntity statue = ModEntities.GREED_STATUE.create(vault.world);
            if (statue == null) continue;
            float yaw = vault.world.getRandom().nextInt(4) * 90f;
            statue.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, yaw, 0);
            statue.setHeadYaw(yaw);
            statue.setBodyYaw(yaw);
            statue.bind(vault.box(), home);
            vault.world.spawnEntity(statue);
            vault.statues[i] = statue.getUuid();
            vault.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SMOOTH_STONE.getDefaultState()),
                    home.getX() + 0.5, home.getY() + 0.2, home.getZ() + 0.5, 30, 0.4, 0.1, 0.4, 0.1);
        }
    }

    /** Every statue of this vault goes, the ones it's tracking and any it lost track of (bound to one of its stones). */
    private static void sinkStatues(Vault vault) {
        for (int i = 0; i < vault.statues.length; i++) {
            GreedStatueEntity statue = statue(vault, i);
            if (statue != null) statue.discard();
            vault.statues[i] = null;
        }
        List<BlockPos> homes = vault.piece.statueHomes();
        for (GreedStatueEntity stray : vault.world.getEntitiesByClass(GreedStatueEntity.class, Box.from(vault.box()).expand(4),
                s -> homes.contains(s.getHome()))) {
            stray.discard();
        }
    }

    // ---- Resetting ----

    /** Puts the vault back the way it was built. Only touches what has changed, so it's cheap to run often. */
    public static void reset(Vault vault) {
        ServerWorld world = vault.world;
        vault.opening = -1;
        vault.solved = false;
        if (loaded(vault, vault.piece.door())) {
            for (BlockPos pos : vault.piece.door()) {
                if (world.getBlockState(pos).isAir() && world.getOtherEntities(null, new Box(pos)).isEmpty()) {
                    world.setBlockState(pos, Blocks.IRON_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        for (BlockPos pos : vault.piece.levers()) {
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.LEVER) && state.get(LeverBlock.POWERED)) ((LeverBlock) Blocks.LEVER).togglePower(state, world, pos, null);
        }
        for (GreedVaultPiece.Clue clue : vault.clues()) restock(world, clue);
    }

    /** Puts a clue book back where the template had it, if nobody's left a copy of it in there. */
    private static void restock(ServerWorld world, GreedVaultPiece.Clue clue) {
        if (!world.isChunkLoaded(clue.pos())) return;
        BlockEntity entity = world.getBlockEntity(clue.pos());
        if (entity instanceof DecoratedPotBlockEntity pot) {
            if (pot.getStack().isEmpty()) {
                pot.setStack(clue.book().copy());
                pot.markDirty();
            }
            return;
        }
        if (!(entity instanceof Inventory inventory)) return;
        for (int i = 0; i < inventory.size(); i++) {
            if (ItemStack.areItemsAndComponentsEqual(inventory.getStack(i), clue.book())) return;
        }
        int slot = clue.slot() < inventory.size() && inventory.getStack(clue.slot()).isEmpty() ? clue.slot() : -1;
        for (int i = 0; slot < 0 && i < inventory.size(); i++) if (inventory.getStack(i).isEmpty()) slot = i;
        if (slot < 0) return;
        inventory.setStack(slot, clue.book().copy());
        inventory.markDirty();
    }

    // ---- For tests and commands ----

    public static Vault tracked(ServerWorld world, GreedVaultPiece piece) {
        return VAULTS.get(new Key(world.getRegistryKey(), piece.id()));
    }

    public static boolean isStatueOf(Vault vault, Entity entity) {
        for (UUID id : vault.statues) if (entity.getUuid().equals(id)) return true;
        return false;
    }
}
