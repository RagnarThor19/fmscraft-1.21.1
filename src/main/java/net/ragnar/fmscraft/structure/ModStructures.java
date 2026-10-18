package net.ragnar.fmscraft.structure;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.structure.envy.EnvyMazePiece;
import net.ragnar.fmscraft.structure.envy.EnvyMazeStructure;
import net.ragnar.fmscraft.structure.greed.GreedVaultPiece;
import net.ragnar.fmscraft.structure.greed.GreedVaultStructure;
import net.ragnar.fmscraft.structure.pride.PrideTowerPiece;
import net.ragnar.fmscraft.structure.pride.PrideTowerStructure;
import net.ragnar.fmscraft.structure.sloth.SlothSpherePiece;
import net.ragnar.fmscraft.structure.sloth.SlothSphereStructure;

public class ModStructures {
    public static final StructureType<EnvyMazeStructure> ENVY_MAZE = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "envy_maze"), () -> EnvyMazeStructure.CODEC);
    public static final StructurePieceType ENVY_MAZE_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "envy_maze"), (context, nbt) -> new EnvyMazePiece(nbt));

    public static final StructureType<PrideTowerStructure> PRIDE_TOWER = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "tower_of_pride"), () -> PrideTowerStructure.CODEC);
    public static final StructurePieceType PRIDE_TOWER_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "tower_of_pride"), (context, nbt) -> new PrideTowerPiece(nbt));

    public static final StructureType<SlothSphereStructure> SLOTH_SPHERE = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "sphere_of_sloth"), () -> SlothSphereStructure.CODEC);
    public static final StructurePieceType SLOTH_SPHERE_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "sphere_of_sloth"), (context, nbt) -> new SlothSpherePiece(nbt));

    public static final StructureType<GreedVaultStructure> GREED_VAULT = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "greed_vault"), () -> GreedVaultStructure.CODEC);
    public static final StructurePieceType GREED_VAULT_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "greed_vault"), (context, nbt) -> new GreedVaultPiece(context.structureTemplateManager(), nbt));

    public static void initialize() {
    }
}
