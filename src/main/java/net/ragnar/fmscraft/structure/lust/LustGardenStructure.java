package net.ragnar.fmscraft.structure.lust;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.structure.ModStructures;

import java.util.Optional;

/**
 * Lust's Garden. Placement and rarity live in data/fmscraft/worldgen/structure_set/lusts_garden.json, the biomes it
 * may grow in in data/fmscraft/tags/worldgen/biome/has_structure/lusts_garden.json (the twilight's plains).
 *
 * It's about 200 blocks across, so it only takes ground that is fairly level and in its biomes all the way out to
 * the hedge, and it keeps clear of the twilight mod's temples (whose portal it would otherwise bury): it reads that
 * structure set from the registry by id, so nothing breaks if the twilight mod isn't there.
 */
public class LustGardenStructure extends Structure {
    public static final MapCodec<LustGardenStructure> CODEC = createCodec(LustGardenStructure::new);
    /** The tallest the ground under the garden may vary. */
    public static final int MAX_UNEVENNESS = 12;
    /** No temple may start within this many chunks of the garden's middle. */
    public static final int TEMPLE_CLEARANCE = 10;
    private static final RegistryKey<net.minecraft.structure.StructureSet> TEMPLES =
            RegistryKey.of(RegistryKeys.STRUCTURE_SET, Identifier.of("ragnarstwilightdimension", "twilight_temple"));

    public LustGardenStructure(Config config) {
        super(config);
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getCenterX(), cz = chunk.getCenterZ();
        if (nearTemple(context, chunk)) return Optional.empty();

        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, samples = 0;
        for (int ring : new int[]{0, 30, 60, 90}) {
            int count = ring == 0 ? 1 : ring / 5;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count;
                int x = cx + (int) Math.round(Math.cos(angle) * ring), z = cz + (int) Math.round(Math.sin(angle) * ring);
                int surface = context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                if (surface <= context.world().getBottomY() + 1) return Optional.empty();
                var biome = context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(surface), BiomeCoords.fromBlock(z),
                        context.noiseConfig().getMultiNoiseSampler());
                if (!context.biomePredicate().test(biome)) return Optional.empty();
                min = Math.min(min, surface);
                max = Math.max(max, surface);
                sum += surface;
                samples++;
            }
        }
        if (max - min > MAX_UNEVENNESS) return Optional.empty();
        int floor = Math.round((float) sum / samples) + 1;
        if (floor - LustGardenPiece.FOUNDATION <= context.world().getBottomY() || floor + LustGardenPiece.CLEAR >= context.world().getTopY()) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(cx, floor, cz);
        long seed = context.random().nextLong();
        return Optional.of(new StructurePosition(origin, collector -> collector.addPiece(new LustGardenPiece(seed, origin))));
    }

    private static boolean nearTemple(Context context, ChunkPos chunk) {
        var sets = context.dynamicRegistryManager().getOptional(RegistryKeys.STRUCTURE_SET);
        if (sets.isEmpty()) return false;
        var temples = sets.get().get(TEMPLES);
        if (temples == null || !(temples.placement() instanceof RandomSpreadStructurePlacement spread)) return false;
        int spacing = spread.getSpacing(), reach = TEMPLE_CLEARANCE;
        // Every temple start chunk in the regions round us
        for (int rx = Math.floorDiv(chunk.x - reach, spacing); rx <= Math.floorDiv(chunk.x + reach, spacing); rx++) {
            for (int rz = Math.floorDiv(chunk.z - reach, spacing); rz <= Math.floorDiv(chunk.z + reach, spacing); rz++) {
                ChunkPos start = spread.getStartChunk(context.seed(), rx * spacing, rz * spacing);
                if (Math.abs(start.x - chunk.x) <= reach && Math.abs(start.z - chunk.z) <= reach) return true;
            }
        }
        return false;
    }

    @Override
    public StructureType<?> getType() {
        return ModStructures.LUST_GARDEN;
    }
}
