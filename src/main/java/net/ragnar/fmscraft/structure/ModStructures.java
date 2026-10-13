package net.ragnar.fmscraft.structure;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.structure.envy.EnvyMazePiece;
import net.ragnar.fmscraft.structure.envy.EnvyMazeStructure;
import net.ragnar.fmscraft.structure.pride.PrideTowerPiece;
import net.ragnar.fmscraft.structure.pride.PrideTowerStructure;

public class ModStructures {
    public static final StructureType<EnvyMazeStructure> ENVY_MAZE = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "envy_maze"), () -> EnvyMazeStructure.CODEC);
    public static final StructurePieceType ENVY_MAZE_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "envy_maze"), (context, nbt) -> new EnvyMazePiece(nbt));

    public static final StructureType<PrideTowerStructure> PRIDE_TOWER = Registry.register(Registries.STRUCTURE_TYPE,
            Identifier.of(FmsCraft.MOD_ID, "tower_of_pride"), () -> PrideTowerStructure.CODEC);
    public static final StructurePieceType PRIDE_TOWER_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
            Identifier.of(FmsCraft.MOD_ID, "tower_of_pride"), (context, nbt) -> new PrideTowerPiece(nbt));

    public static void initialize() {
    }
}
