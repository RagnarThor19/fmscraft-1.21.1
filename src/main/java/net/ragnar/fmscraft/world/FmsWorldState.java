package net.ragnar.fmscraft.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World-wide progress, saved with the overworld. This is the "tag that lets the world know a boss was defeated".
 * Also holds Lust breeding progress and where players in a boss arena return to.
 */
public class FmsWorldState extends PersistentState {
    private static final Type<FmsWorldState> TYPE = new Type<>(FmsWorldState::new, FmsWorldState::fromNbt, null);

    private final Set<Sin> defeated = EnumSet.noneOf(Sin.class);
    // Lust: entity type ids each player has bred since their last Lust reward
    private final Map<UUID, Set<String>> lustProgress = new HashMap<>();
    // Where each player in a boss arena goes back to (the overworld-side portal), kept across restarts
    private final Map<UUID, GlobalPos> returnPositions = new HashMap<>();
    // Where each player last died and left their things lying (DeathMarks), until they get back to them
    private final Map<UUID, GlobalPos> deathMarks = new HashMap<>();
    // Envy mazes (keyed by their corner position) and the statues that roam each one
    private final Map<Long, Set<UUID>> envyStatues = new HashMap<>();
    // The True Ending shrine: where it was built (null until then), who placed each soul, and whether the game is won
    private BlockPos shrine;
    private final Map<Sin, String> soulsPlaced = new java.util.EnumMap<>(Sin.class);
    private boolean finished;
    /** Ore blocks players put down, by dimension: they don't count for Greed's lucky strike (LuckyStrike). */
    private final Map<String, it.unimi.dsi.fastutil.longs.LongSet> placedOres = new HashMap<>();

    public static FmsWorldState get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, FmsCraft.MOD_ID);
    }

    public boolean isDefeated(Sin sin) {
        return defeated.contains(sin);
    }

    public void markDefeated(Sin sin) {
        if (defeated.add(sin)) markDirty();
    }

    public boolean allDefeated() {
        return defeated.size() == Sin.values().length;
    }

    /** Returns true if this animal is new for the player. */
    public boolean addLustProgress(UUID player, String entityTypeId) {
        boolean added = lustProgress.computeIfAbsent(player, k -> new HashSet<>()).add(entityTypeId);
        if (added) markDirty();
        return added;
    }

    public Set<String> getLustProgress(UUID player) {
        return new HashSet<>(lustProgress.getOrDefault(player, Set.of()));
    }

    public void clearLustProgress(UUID player) {
        if (lustProgress.remove(player) != null) markDirty();
    }

    public void setReturn(UUID player, GlobalPos pos) {
        returnPositions.put(player, pos);
        markDirty();
    }

    public Optional<GlobalPos> getReturn(UUID player) {
        return Optional.ofNullable(returnPositions.get(player));
    }

    public void clearReturn(UUID player) {
        if (returnPositions.remove(player) != null) markDirty();
    }

    public void setDeathMark(UUID player, GlobalPos pos) {
        deathMarks.put(player, pos);
        markDirty();
    }

    public Optional<GlobalPos> getDeathMark(UUID player) {
        return Optional.ofNullable(deathMarks.get(player));
    }

    public void clearDeathMark(UUID player) {
        if (deathMarks.remove(player) != null) markDirty();
    }

    public Set<UUID> envyStatues(long maze) {
        return java.util.Collections.unmodifiableSet(envyStatues.getOrDefault(maze, Set.of()));
    }

    public void addEnvyStatue(long maze, UUID statue) {
        if (envyStatues.computeIfAbsent(maze, k -> new HashSet<>()).add(statue)) markDirty();
    }

    public void removeEnvyStatue(long maze, UUID statue) {
        Set<UUID> statues = envyStatues.get(maze);
        if (statues != null && statues.remove(statue)) markDirty();
    }

    public Optional<BlockPos> shrine() {
        return Optional.ofNullable(shrine);
    }

    public void setShrine(BlockPos pos) {
        shrine = pos == null ? null : pos.toImmutable();
        markDirty();
    }

    /** Who placed each soul in the shrine so far, in sin order. */
    public Map<Sin, String> soulsPlaced() {
        return java.util.Collections.unmodifiableMap(soulsPlaced);
    }

    public void placeSoul(Sin sin, String player) {
        soulsPlaced.put(sin, player);
        markDirty();
    }

    public boolean allSoulsPlaced() {
        return soulsPlaced.size() == Sin.values().length;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
        markDirty();
    }

    /** Forgets the shrine's souls and the win (the blocks are put back separately). */
    public void clearSouls() {
        soulsPlaced.clear();
        finished = false;
        markDirty();
    }

    public void rememberPlacedOre(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, BlockPos pos) {
        if (placedOres.computeIfAbsent(dimension.getValue().toString(), d -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet()).add(pos.asLong())) markDirty();
    }

    /** Whether a player had placed the ore here; either way it's forgotten. */
    public boolean forgetPlacedOre(net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension, BlockPos pos) {
        it.unimi.dsi.fastutil.longs.LongSet set = placedOres.get(dimension.getValue().toString());
        if (set == null || !set.remove(pos.asLong())) return false;
        markDirty();
        return true;
    }

    public void clearDefeated(Sin sin) {
        if (defeated.remove(sin)) markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Sin sin : defeated) list.add(NbtString.of(sin.id()));
        nbt.put("defeated", list);

        NbtCompound lust = new NbtCompound();
        lustProgress.forEach((player, bred) -> {
            NbtList bredList = new NbtList();
            for (String id : bred) bredList.add(NbtString.of(id));
            lust.put(player.toString(), bredList);
        });
        nbt.put("lust", lust);

        NbtCompound returns = new NbtCompound();
        returnPositions.forEach((player, pos) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("dimension", pos.dimension().getValue().toString());
            entry.putLong("pos", pos.pos().asLong());
            returns.put(player.toString(), entry);
        });
        nbt.put("returns", returns);

        NbtCompound marks = new NbtCompound();
        deathMarks.forEach((player, pos) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("dimension", pos.dimension().getValue().toString());
            entry.putLong("pos", pos.pos().asLong());
            marks.put(player.toString(), entry);
        });
        nbt.put("death_marks", marks);

        NbtCompound mazes = new NbtCompound();
        envyStatues.forEach((maze, statues) -> {
            NbtList ids = new NbtList();
            for (UUID id : statues) ids.add(NbtString.of(id.toString()));
            mazes.put(Long.toString(maze), ids);
        });
        nbt.put("envy_statues", mazes);

        NbtCompound ending = new NbtCompound();
        if (shrine != null) ending.putLong("shrine", shrine.asLong());
        NbtCompound souls = new NbtCompound();
        soulsPlaced.forEach((sin, player) -> souls.putString(sin.id(), player));
        ending.put("souls", souls);
        ending.putBoolean("finished", finished);
        nbt.put("true_ending", ending);

        NbtCompound ores = new NbtCompound();
        placedOres.forEach((dimension, set) -> {
            if (!set.isEmpty()) ores.putLongArray(dimension, set.toLongArray());
        });
        nbt.put("placed_ores", ores);
        return nbt;
    }

    private static FmsWorldState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        FmsWorldState state = new FmsWorldState();
        NbtList list = nbt.getList("defeated", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            Sin sin = Sin.byId(list.getString(i));
            if (sin != null) state.defeated.add(sin);
        }

        NbtCompound lust = nbt.getCompound("lust");
        for (String key : lust.getKeys()) {
            NbtList bredList = lust.getList(key, NbtElement.STRING_TYPE);
            Set<String> bred = new HashSet<>();
            for (int i = 0; i < bredList.size(); i++) bred.add(bredList.getString(i));
            state.lustProgress.put(UUID.fromString(key), bred);
        }

        NbtCompound returns = nbt.getCompound("returns");
        for (String key : returns.getKeys()) {
            NbtCompound entry = returns.getCompound(key);
            RegistryKey<net.minecraft.world.World> dimension =
                    RegistryKey.of(RegistryKeys.WORLD, Identifier.of(entry.getString("dimension")));
            state.returnPositions.put(UUID.fromString(key), GlobalPos.create(dimension, BlockPos.fromLong(entry.getLong("pos"))));
        }

        NbtCompound marks = nbt.getCompound("death_marks");
        for (String key : marks.getKeys()) {
            NbtCompound entry = marks.getCompound(key);
            RegistryKey<net.minecraft.world.World> dimension =
                    RegistryKey.of(RegistryKeys.WORLD, Identifier.of(entry.getString("dimension")));
            state.deathMarks.put(UUID.fromString(key), GlobalPos.create(dimension, BlockPos.fromLong(entry.getLong("pos"))));
        }

        NbtCompound mazes = nbt.getCompound("envy_statues");
        for (String key : mazes.getKeys()) {
            NbtList ids = mazes.getList(key, NbtElement.STRING_TYPE);
            Set<UUID> statues = new HashSet<>();
            for (int i = 0; i < ids.size(); i++) statues.add(UUID.fromString(ids.getString(i)));
            state.envyStatues.put(Long.parseLong(key), statues);
        }

        NbtCompound ending = nbt.getCompound("true_ending");
        if (ending.contains("shrine")) state.shrine = BlockPos.fromLong(ending.getLong("shrine"));
        NbtCompound souls = ending.getCompound("souls");
        for (String key : souls.getKeys()) {
            Sin sin = Sin.byId(key);
            if (sin != null) state.soulsPlaced.put(sin, souls.getString(key));
        }
        state.finished = ending.getBoolean("finished");

        NbtCompound ores = nbt.getCompound("placed_ores");
        for (String dimension : ores.getKeys()) {
            state.placedOres.put(dimension, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(ores.getLongArray(dimension)));
        }
        return state;
    }
}
