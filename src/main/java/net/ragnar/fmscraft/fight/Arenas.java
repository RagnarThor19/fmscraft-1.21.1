package net.ragnar.fmscraft.fight;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Optional;

/**
 * Each sin has its own void dimension ({@code fmscraft:<sin>_arena}) with the arena at the origin.
 * The arena is wiped and rebuilt before every fight, so a boss always starts fresh.
 *
 * To give a sin its own arena, save it with a structure block as {@code fmscraft:arena/<sin>} (centered on
 * x/z 0, floor at y 63) and put the .nbt in data/fmscraft/structure/arena/. Without one, a plain platform is built.
 */
public final class Arenas {
    public static final int RADIUS = 18;
    public static final int FLOOR_Y = 63;
    private static final int CLEAR_HEIGHT = 40;

    public static final Vec3d PLAYER_SPAWN = new Vec3d(0.5, FLOOR_Y + 1, RADIUS - 4 + 0.5);
    public static final Vec3d BOSS_SPAWN = new Vec3d(0.5, FLOOR_Y + 1, 0.5);
    /** Bottom-left of the 2x3 exit portal that appears after a win, behind where players arrive. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(0, FLOOR_Y + 1, RADIUS - 1);

    private Arenas() {
    }

    public static RegistryKey<World> key(Sin sin) {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(FmsCraft.MOD_ID, sin.id() + "_arena"));
    }

    public static ServerWorld world(MinecraftServer server, Sin sin) {
        return server.getWorld(key(sin));
    }

    public static Sin sinOf(World world) {
        for (Sin sin : Sin.values()) {
            if (world.getRegistryKey().equals(key(sin))) return sin;
        }
        return null;
    }

    /** Removes everything left over from the last fight and builds the arena again. */
    public static void reset(ServerWorld world, Sin sin) {
        int r = RADIUS + 4;
        Box box = new Box(-r, FLOOR_Y - 4, -r, r + 1, FLOOR_Y + CLEAR_HEIGHT, r + 1);
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity))) {
            entity.discard();
        }

        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = FLOOR_Y - 4; y < FLOOR_Y + CLEAR_HEIGHT; y++) {
                    pos.set(x, y, z);
                    if (!world.getBlockState(pos).isAir()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
                }
            }
        }

        Optional<StructureTemplate> template = world.getStructureTemplateManager()
                .getTemplate(Identifier.of(FmsCraft.MOD_ID, "arena/" + sin.id()));
        if (template.isPresent()) {
            var size = template.get().getSize();
            BlockPos corner = new BlockPos(-size.getX() / 2, FLOOR_Y, -size.getZ() / 2);
            template.get().place(world, corner, corner, new StructurePlacementData(), world.getRandom(), Block.NOTIFY_LISTENERS);
        } else {
            buildDefault(world, flags);
        }
    }

    /** A round deepslate platform ringed with soul lanterns on blackstone pillars. */
    private static void buildDefault(ServerWorld world, int flags) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState edge = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState under = Blocks.COBBLED_DEEPSLATE.getDefaultState();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > RADIUS + 0.5) continue;
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), d > RADIUS - 1.5 ? edge : floor, flags);
                world.setBlockState(new BlockPos(x, FLOOR_Y - 1, z), under, flags);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * (RADIUS - 1));
            int z = (int) Math.round(Math.sin(a) * (RADIUS - 1));
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(x, FLOOR_Y + y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), flags);
            }
            world.setBlockState(new BlockPos(x, FLOOR_Y + 4, z),
                    Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, false), flags);
        }
    }
}
