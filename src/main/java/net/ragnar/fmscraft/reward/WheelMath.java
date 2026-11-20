package net.ragnar.fmscraft.reward;

/**
 * Geometry of the reward wheel, shared by server and client so the animation always stops on the slice the
 * server already picked. Angles are degrees clockwise from the pointer at the top.
 *
 * 12 slices of 30 degrees: 5 beginner, 3 advanced, 2 master, 2 passive (matching {@link WheelTier#chance}),
 * spread out so equal tiers never touch; the two master and the two passive slices sit opposite each other.
 */
public final class WheelMath {
    public static final WheelTier[] SLICES = {
            WheelTier.BEGINNER, WheelTier.ADVANCED, WheelTier.BEGINNER, WheelTier.MASTER, WheelTier.BEGINNER,
            WheelTier.PASSIVE, WheelTier.BEGINNER, WheelTier.ADVANCED, WheelTier.BEGINNER, WheelTier.MASTER,
            WheelTier.ADVANCED, WheelTier.PASSIVE};
    public static final float SLICE_DEGREES = 360f / SLICES.length;
    public static final int FULL_SPINS = 4;

    private WheelMath() {
    }

    /** Total rotation that leaves the pointer {@code offset} (0..1) of the way into {@code slice}. */
    public static float finalRotation(int slice, float offset) {
        float landing = (slice + offset) * SLICE_DEGREES;
        return 360f * FULL_SPINS + (360f - landing);
    }

    /** Which slice sits under the pointer when the wheel has turned {@code rotation} degrees. */
    public static int sliceAt(float rotation) {
        float local = ((-rotation % 360f) + 360f) % 360f;
        return Math.min(SLICES.length - 1, (int) (local / SLICE_DEGREES));
    }

    /** Quintic ease-out: fast start, then a long glide where it creeps toward the last few slices. {@code t} in 0..1. */
    public static float ease(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inv = 1f - clamped;
        return 1f - inv * inv * inv * inv * inv;
    }
}
