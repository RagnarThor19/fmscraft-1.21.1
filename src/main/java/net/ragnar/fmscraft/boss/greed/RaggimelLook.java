package net.ragnar.fmscraft.boss.greed;

/** What RAGGIMEL's model needs to know, from him or from one of his mirages (they look exactly alike). */
public interface RaggimelLook {
    int poseState();

    /** Client side: how long he's been raising his arms, for the model. */
    float raiseTime(float tickDelta);

    boolean hasGlowingEyes();

    default boolean isBubbled() {
        return false;
    }
}
