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
        return state;
    }
}
