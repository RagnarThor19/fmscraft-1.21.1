package net.ragnar.fmscraft.world;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

/**
 * The {@code minecartSpeed} gamerule: minecarts on rails go this many times as fast as vanilla's 8 blocks a second
 * (1 is vanilla, the most is 10). {@code /gamerule minecartSpeed 4}.
 * <p>
 * Their top speed isn't raised: a cart moving more than a block a tick flies off curves, jumps slopes and skips
 * detector rails. Instead each tick it runs its ordinary rail movement this many times over (AbstractMinecartEntityMixin),
 * so it takes every curve, slope, powered, detector and activator rail exactly as vanilla does, just sooner. Off the
 * rails it's vanilla again, and it never moves into a chunk that isn't loaded and ticking, so it can't make the server
 * load or generate chunks on its own.
 */
public final class FastMinecarts {
    public static final int MAX = 10;
    public static final GameRules.Key<GameRules.IntRule> SPEED =
            GameRuleRegistry.register("minecartSpeed", GameRules.Category.MISC, GameRuleFactory.createIntRule(1, 1, MAX));

    private FastMinecarts() {
    }

    /** Loads the class, so the gamerule is registered while the game starts. */
    public static void register() {
    }
}
