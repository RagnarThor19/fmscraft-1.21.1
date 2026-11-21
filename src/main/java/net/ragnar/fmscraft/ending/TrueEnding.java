package net.ragnar.fmscraft.ending;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SoulPedestalBlock;
import net.ragnar.fmscraft.network.CreditsDonePayload;
import net.ragnar.fmscraft.network.CreditsPayload;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The True Ending: the shrine with the seven Soul Pedestals (structure/fms_beams.nbt, built by
 * tools/structures/fms_beams.py), and the end of the game once all seven souls are in it.
 *
 * <p>There is exactly one shrine per world. It isn't left to world generation (which would never reach the chunks
 * round spawn on a world that's already being played, and can't promise one near spawn): the first time the server
 * starts, {@link #findSite} reads the world generator's own terrain and biomes round the spawn and picks the best
 * spot {@value #MIN_DISTANCE} to {@value #MAX_DISTANCE} blocks out (open, flat, dry land in a pleasant biome, its
 * real ground untouched by players), and {@link #place} sets it into the ground there, facing back toward spawn.
 *
 * <p>When the seventh soul goes in: fireworks in every sin's color over the shrine, "True Ending" on everyone's
 * screen and the advancement for everyone (also for anyone who logs in later), then the credits roll.
 */
public final class TrueEnding {
    public static final Identifier TEMPLATE = Identifier.of(FmsCraft.MOD_ID, "fms_beams");
    public static final Identifier ADVANCEMENT = Identifier.of(FmsCraft.MOD_ID, "true_ending");
    public static final int MIN_DISTANCE = 80;
    public static final int MAX_DISTANCE = 300;
    /** Ideal distance from spawn: far enough to be a little trip, near enough to find. */
    private static final int IDEAL_DISTANCE = 170;
    private static final int STEP = 16;
    /** Half the side of the square checked for each site (the shrine is 10x24, plus some room round it). */
    private static final int HALF = 15;
    /** How much the ground may rise and fall under a site. */
    private static final int MAX_BUMP = 4;
    private static final int CLEAR_HEIGHT = 20;
    /** Ticks from the seventh soul to the credits, and how long everyone is kept safe while they roll. */
    public static final int CREDITS_DELAY = 140;
    private static final int CREDITS_PROTECTION = 20 * 60 * 12; // the credits, then the memories video

    private record Scheduled(long at, Runnable action) {
    }

    private static final List<Scheduled> SCHEDULED = new ArrayList<>();

    private TrueEnding() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Game tests build their own shrines where they need them
            if (System.getProperty("fabric-api.gametest") != null) return;
            ensureShrine(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SCHEDULED.clear());
        ServerTickEvents.END_SERVER_TICK.register(TrueEnding::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (FmsWorldState.get(server).isFinished()) grantAdvancement(handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(CreditsDonePayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            player.removeStatusEffect(StatusEffects.RESISTANCE);
            player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
            player.removeStatusEffect(StatusEffects.WATER_BREATHING);
        }));
    }

    // ---- Where the shrine goes ----

    /** Builds the shrine if this world doesn't have one yet. Returns where it is. */
    public static BlockPos ensureShrine(MinecraftServer server) {
        FmsWorldState state = FmsWorldState.get(server);
        Optional<BlockPos> existing = state.shrine();
        if (existing.isPresent()) return existing.get();

        ServerWorld world = server.getOverworld();
        BlockPos spawn = world.getSpawnPos();
        long start = System.currentTimeMillis();
        BlockPos site = findSite(world, spawn);
        BlockPos center = place(world, site, facingToward(site, spawn));
        state.setShrine(center);
        FmsCraft.LOGGER.info("Built the True Ending shrine at {} ({} blocks from spawn, took {} ms)", center.toShortString(),
                Math.round(Math.sqrt(center.getSquaredDistance(spawn.getX(), center.getY(), spawn.getZ()))), System.currentTimeMillis() - start);
        return center;
    }

    private record Candidate(int x, int z, double score) {
    }

    /**
     * The nicest spot round {@code spawn}: scored on the world generator's terrain and biomes (cheap, nothing is
     * generated), then the best few are checked against the real world, so a site players have built on or dug up is
     * passed over. Returns a ground-level position (the shrine's center).
     */
    public static BlockPos findSite(ServerWorld world, BlockPos spawn) {
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        NoiseConfig noise = world.getChunkManager().getNoiseConfig();
        int sea = world.getSeaLevel();

        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -MAX_DISTANCE; dx <= MAX_DISTANCE; dx += STEP) {
            for (int dz = -MAX_DISTANCE; dz <= MAX_DISTANCE; dz += STEP) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < MIN_DISTANCE || distance > MAX_DISTANCE - HALF) continue;
                int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                Double score = score(world, generator, noise, sea, x, z, distance);
                if (score != null) candidates.add(new Candidate(x, z, score));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score));

        // The real ground of the best ones (this loads or generates their chunks)
        int checked = 0;
        for (Candidate candidate : candidates) {
            if (checked++ >= 24) break;
            Integer ground = untouchedGround(world, generator, noise, candidate.x(), candidate.z());
            if (ground != null) return new BlockPos(candidate.x(), ground, candidate.z());
        }
        // Nothing passed (a world of ocean, or built over): take the best by the generator alone, or else spawn itself
        if (!candidates.isEmpty()) {
            Candidate best = candidates.get(0);
            return new BlockPos(best.x(), surface(world, best.x(), best.z()), best.z());
        }
        BlockPos fallback = spawn.add(MIN_DISTANCE, 0, 0);
        return new BlockPos(fallback.getX(), surface(world, fallback.getX(), fallback.getZ()), fallback.getZ());
    }

    /** Lower is better; null for a site that won't do at all. */
    @Nullable
    private static Double score(World world, ChunkGenerator generator, NoiseConfig noise, int sea, int x, int z, double distance) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int sx = x + i * HALF, sz = z + j * HALF;
                int surface = generator.getHeight(sx, sz, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
                int floor = generator.getHeight(sx, sz, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
                if (surface != floor) return null; // water
                min = Math.min(min, floor);
                max = Math.max(max, floor);
            }
        }
        int bump = max - min;
        if (bump > MAX_BUMP || min <= sea || max > sea + 70) return null;

        RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(max),
                BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler());
        Double biomeScore = biomeScore(biome);
        if (biomeScore == null) return null;

        return bump * 2.0 + biomeScore + Math.abs(distance - IDEAL_DISTANCE) / 40.0
                // A little higher than the sea is a nicer view than a bog by the water
                + (min < sea + 4 ? 2.0 : 0.0);
    }

    /** Open, pleasant land is best; woods and the cold are fine; water, swamps, jungle and the like are out. */
    @Nullable
    private static Double biomeScore(RegistryEntry<Biome> biome) {
        if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER) || biome.isIn(BiomeTags.IS_BEACH)
                || biome.isIn(BiomeTags.IS_JUNGLE) || biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP)
                || biome.matchesKey(BiomeKeys.MUSHROOM_FIELDS) || biome.matchesKey(BiomeKeys.DEEP_DARK)) {
            return null;
        }
        if (biome.matchesKey(BiomeKeys.PLAINS) || biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS) || biome.matchesKey(BiomeKeys.MEADOW)
                || biome.matchesKey(BiomeKeys.CHERRY_GROVE) || biome.matchesKey(BiomeKeys.FLOWER_FOREST)) {
            return 0.0;
        }
        if (biome.matchesKey(BiomeKeys.SAVANNA) || biome.matchesKey(BiomeKeys.BIRCH_FOREST) || biome.matchesKey(BiomeKeys.FOREST)
                || biome.matchesKey(BiomeKeys.SNOWY_PLAINS)) {
            return 3.0;
        }
        return 6.0;
    }

    /**
     * The ground height at the site if the real world still matches what the generator made there (natural ground at
     * about the same height, nothing with a block entity in it), else null.
     */
    @Nullable
    private static Integer untouchedGround(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int x, int z) {
        for (int cx = (x - HALF) >> 4; cx <= (x + HALF) >> 4; cx++) {
            for (int cz = (z - HALF) >> 4; cz <= (z + HALF) >> 4; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    if (Math.abs(pos.getX() - x) <= HALF + 2 && Math.abs(pos.getZ() - z) <= HALF + 2) return null;
                }
            }
        }
        List<Integer> heights = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                int sx = x + i * HALF / 2, sz = z + j * HALF / 2;
                int real = surface(world, sx, sz);
                int generated = generator.getHeight(sx, sz, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
                if (Math.abs(real - generated) > 2) return null;
                if (!natural(world.getBlockState(new BlockPos(sx, real - 1, sz)))) return null;
                heights.add(real);
            }
        }
        heights.sort(null);
        return heights.get(heights.size() / 2);
    }

    private static boolean natural(BlockState ground) {
        return ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SAND) || ground.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || ground.isOf(Blocks.GRAVEL) || ground.isOf(Blocks.SNOW_BLOCK) || ground.isOf(Blocks.POWDER_SNOW)
                || ground.isOf(Blocks.CLAY) || ground.isIn(BlockTags.TERRACOTTA);
    }

    /** First free block above the ground, ignoring leaves and plants. */
    private static int surface(World world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, y - 1, z);
        // Logs aren't ground: walk down a trunk
        while (pos.getY() > world.getBottomY() && world.getBlockState(pos).isIn(BlockTags.LOGS)) pos.move(Direction.DOWN);
        return pos.getY() + 1;
    }

    /** The side of the shrine that looks back toward spawn. */
    public static Direction facingToward(BlockPos site, BlockPos spawn) {
        int dx = spawn.getX() - site.getX(), dz = spawn.getZ() - site.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ---- Building it ----

    /**
     * Sets the shrine into the ground centered on {@code site} (a ground-level position), its pedestals facing
     * {@code facing}: the spot is cleared of plants and trees, any dips under it are filled, the shrine's floor
     * replaces the top layer of soil. Returns its center.
     */
    public static BlockPos place(ServerWorld world, BlockPos site, Direction facing) {
        StructureTemplate template = world.getStructureTemplateManager().getTemplate(TEMPLATE)
                .orElseThrow(() -> new IllegalStateException("Missing structure " + TEMPLATE));
        // The pedestals face east in the template
        StructurePlacementData data = new StructurePlacementData().setRotation(switch (facing) {
            case SOUTH -> BlockRotation.CLOCKWISE_90;
            case WEST -> BlockRotation.CLOCKWISE_180;
            case NORTH -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        });

        BlockBox atOrigin = template.calculateBoundingBox(data, BlockPos.ORIGIN);
        BlockPos center = atOrigin.getCenter();
        int floorY = site.getY() - 1;
        BlockPos corner = new BlockPos(site.getX() - center.getX(), floorY - atOrigin.getMinY(), site.getZ() - center.getZ());
        BlockBox box = template.calculateBoundingBox(data, corner);

        // Clear the spot, one block wider than the shrine, and give it even ground
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = box.getMinX() - 1; x <= box.getMaxX() + 1; x++) {
            for (int z = box.getMinZ() - 1; z <= box.getMaxZ() + 1; z++) {
                for (int y = floorY + 1; y <= floorY + CLEAR_HEIGHT; y++) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                pos.set(x, floorY, z);
                BlockState top = world.getBlockState(pos);
                if (top.isAir() || top.isReplaceable() || top.isIn(BlockTags.DIRT)) {
                    world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Fill any hollow under it with dirt, down to the ground
                for (int y = floorY - 1; y > floorY - 12; y--) {
                    pos.set(x, y, z);
                    BlockState below = world.getBlockState(pos);
                    if (!below.isAir() && !below.isReplaceable()) break;
                    world.setBlockState(pos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        template.place(world, corner, corner, data, world.getRandom(), Block.NOTIFY_LISTENERS);
        return box.getCenter().withY(site.getY());
    }

    /** The shrine's pedestals, found by looking round where it was built. */
    public static Map<Sin, BlockPos> pedestals(ServerWorld world, BlockPos center) {
        Map<Sin, BlockPos> found = new java.util.EnumMap<>(Sin.class);
        for (BlockPos pos : BlockPos.iterate(center.add(-16, -6, -16), center.add(16, 8, 16))) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(ModBlocks.SOUL_PEDESTAL)) found.put(state.get(SoulPedestalBlock.SIN), pos.toImmutable());
        }
        return found;
    }

    /** Takes every soul back out of the shrine's pedestals and forgets the win (for testing, by command). */
    public static void reset(MinecraftServer server) {
        FmsWorldState state = FmsWorldState.get(server);
        state.shrine().ifPresent(center -> {
            ServerWorld world = server.getOverworld();
            for (BlockPos pos : pedestals(world, center).values()) {
                world.setBlockState(pos, world.getBlockState(pos).with(SoulPedestalBlock.SOUL, false));
            }
        });
        state.clearSouls();
    }

    // ---- The souls going in ----

    /** A soul went into its pedestal: tell everyone, and if it was the last, end the game. */
    public static void onSoulPlaced(ServerWorld world, BlockPos pos, Sin sin, ServerPlayerEntity player) {
        MinecraftServer server = world.getServer();
        FmsWorldState state = FmsWorldState.get(server);
        state.placeSoul(sin, player.getGameProfile().getName());
        int placed = state.soulsPlaced().size();
        server.getPlayerManager().broadcast(Text.translatable("message.fmscraft.soul_placed", player.getDisplayName(),
                sin.displayName(), placed, Sin.values().length).withColor(sin.color()), false);
        if (state.allSoulsPlaced() && !state.isFinished()) finale(world, pos);
    }

    private static void finale(ServerWorld world, BlockPos lastPedestal) {
        MinecraftServer server = world.getServer();
        FmsWorldState state = FmsWorldState.get(server);
        state.setFinished(true);
        BlockPos center = state.shrine().orElse(lastPedestal);

        server.getPlayerManager().broadcast(Text.translatable("message.fmscraft.true_ending").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            grantAdvancement(player);
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(20, 100, 30));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.fmscraft.true_ending.subtitle").formatted(Formatting.GRAY)));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.fmscraft.true_ending").formatted(Formatting.GOLD)));
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        world.playSound(null, center, SoundEvents.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.BLOCKS, 0.6f, 1.2f);

        // A rocket in each sin's color from its pedestal, one after another, then all of them at once
        Map<Sin, BlockPos> pedestals = pedestals(world, center);
        long now = server.getTicks();
        int i = 0;
        for (Sin sin : Sin.values()) {
            BlockPos from = pedestals.getOrDefault(sin, center);
            schedule(now + 20 + i * 8L, () -> firework(world, from, List.of(sin), 2));
            i++;
        }
        schedule(now + 100, () -> firework(world, center, List.of(Sin.values()), 3));
        schedule(now + CREDITS_DELAY, () -> rollCredits(server));
    }

    private static void firework(ServerWorld world, BlockPos from, List<Sin> sins, int flight) {
        IntList colors = IntList.of(sins.stream().mapToInt(Sin::color).toArray());
        FireworkExplosionComponent explosion = new FireworkExplosionComponent(
                sins.size() > 1 ? FireworkExplosionComponent.Type.LARGE_BALL : FireworkExplosionComponent.Type.BURST,
                colors, IntList.of(0xFFFFFF), true, sins.size() > 1);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(flight, List.of(explosion)));
        world.spawnEntity(new FireworkRocketEntity(world, from.getX() + 0.5, from.getY() + 1.0, from.getZ() + 0.5, rocket));
    }

    /** The credits as they'd roll now: who laid each soul to rest, and the fun facts. */
    public static CreditsPayload credits(MinecraftServer server) {
        Map<Sin, String> placed = FmsWorldState.get(server).soulsPlaced();
        List<String> names = new ArrayList<>();
        for (Sin sin : Sin.values()) names.add(placed.getOrDefault(sin, ""));
        return new CreditsPayload(names, CreditsFacts.collect(server), false);
    }

    /** Everyone online watches the credits, kept safe from harm while they roll (the effects go when they close them). */
    public static void rollCredits(MinecraftServer server) {
        CreditsPayload credits = credits(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, CreditsPayload.ID)) continue;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, CREDITS_PROTECTION, 4, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, CREDITS_PROTECTION, 0, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, CREDITS_PROTECTION, 0, false, false, false));
            ServerPlayNetworking.send(player, credits);
        }
    }

    private static void grantAdvancement(ServerPlayerEntity player) {
        AdvancementEntry advancement = player.getServer().getAdvancementLoader().get(ADVANCEMENT);
        if (advancement != null) player.getAdvancementTracker().grantCriterion(advancement, "placed_all_souls");
    }

    private static void schedule(long at, Runnable action) {
        SCHEDULED.add(new Scheduled(at, action));
    }

    private static void tick(MinecraftServer server) {
        if (SCHEDULED.isEmpty()) return;
        long now = server.getTicks();
        List<Scheduled> due = new ArrayList<>();
        SCHEDULED.removeIf(s -> {
            if (s.at() > now) return false;
            due.add(s);
            return true;
        });
        due.forEach(s -> s.action().run());
    }
}
