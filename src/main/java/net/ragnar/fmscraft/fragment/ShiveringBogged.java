package net.ragnar.fmscraft.fragment;

/** Mixed into every Bogged ({@code mixin/BoggedEntityMixin}): whether it's the rare shivering one, see {@link EnvyTrigger}. */
public interface ShiveringBogged {
    boolean fmscraft$isShivering();

    void fmscraft$setShivering(boolean shivering);

    /** Synced to clients: shivering and not after a player right now. */
    boolean fmscraft$isCalm();

    void fmscraft$setCalm(boolean calm);
}
