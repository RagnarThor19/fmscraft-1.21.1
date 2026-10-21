package net.ragnar.fmscraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallPiece;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHalls;
import net.ragnar.fmscraft.structure.greed.GreedVaultLayout;
import net.ragnar.fmscraft.structure.greed.GreedVaultPiece;
import net.ragnar.fmscraft.structure.greed.GreedVaults;
import net.ragnar.fmscraft.structure.lust.LustGardenPiece;
import net.ragnar.fmscraft.structure.lust.LustGardens;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape;
import net.ragnar.fmscraft.structure.sloth.SlothSpherePiece;
import net.ragnar.fmscraft.structure.sloth.SlothSpheres;

import java.util.List;
import java.util.function.Predicate;

/**
 * The rooms a challenge shuts off until it's done: behind the Greed Vault's door, in the Lust crypt under the
 * fountain, inside the core of the Sphere of Sloth and behind the Hall of Gluttony's gate. Nothing teleports into one
 * while it's shut (LivingEntityMixin): chorus fruit is food, so the barriers allow it, and it jumps up to 8 blocks
 * through walls, so without this anyone could eat their way past a challenge, and the room would then open for
 * everyone (each structure opens up for whoever is in there, so nobody back from a fight is ever shut in). A chorus
 * fruit that would have landed in one just lands somewhere else, as it does when its first spot isn't safe.
 */
public final class SealedRooms {
    private SealedRooms() {
    }

    /** Whether {@code pos} is in one of those rooms while it's still shut. */
    public static boolean sealed(ServerWorld world, BlockPos pos) {
        GreedVaultPiece vault = GreedVaults.pieceAt(world, pos);
        if (vault != null && GreedVaultLayout.inner(vault.toLocal(pos)) && any(world, vault.door(), s -> s.isOf(Blocks.IRON_BLOCK))) return true;
        LustGardenPiece garden = LustGardens.pieceAt(world, pos);
        if (garden != null && garden.inCrypt(pos) && any(world, garden.capBlocks(), s -> !s.isAir())) return true;
        SlothSpherePiece sphere = SlothSpheres.pieceAt(world, pos);
        if (sphere != null && sphere.distanceFromCenter(Vec3d.ofCenter(pos)) < SlothSphereShape.CORE && !SlothSpheres.isOpen(world, sphere)) return true;
        GluttonyHallPiece hall = GluttonyHalls.pieceAt(world, pos);
        return hall != null && hall.inSanctum(pos) && any(world, hall.gate(), s -> s.isOf(Blocks.GILDED_BLACKSTONE));
    }

    /** Whether any of the door's blocks is still there: it's shut until the last of them has gone. */
    private static boolean any(ServerWorld world, List<BlockPos> door, Predicate<BlockState> shut) {
        for (BlockPos pos : door) if (shut.test(world.getBlockState(pos))) return true;
        return false;
    }
}
