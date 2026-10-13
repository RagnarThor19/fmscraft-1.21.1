package net.ragnar.fmscraft.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

public class ModBlocks {
    public static final Block SIN_PORTAL = register("sin_portal", new SinPortalBlock(AbstractBlock.Settings.create()
            .noCollision()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .luminance(state -> 11)
            .sounds(BlockSoundGroup.GLASS)
            .pistonBehavior(PistonBehavior.BLOCK)));

    /** The lock in the Envy maze. Unbreakable so it can't be carried off. */
    public static final Block ENVIOUS_EYE = register("envious_eye", new EnviousEyeBlock(AbstractBlock.Settings.create()
            .strength(-1.0f, 3600000.0f)
            .dropsNothing()
            .luminance(state -> state.get(EnviousEyeBlock.LIT) ? 12 : 3)
            .sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
            .pistonBehavior(PistonBehavior.BLOCK)));

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of(FmsCraft.MOD_ID, name), block);
    }

    public static void initialize() {
    }
}
