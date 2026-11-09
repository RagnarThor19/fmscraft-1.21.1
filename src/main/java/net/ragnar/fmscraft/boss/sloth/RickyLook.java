package net.ragnar.fmscraft.boss.sloth;

/** What RickyModel and RickyRenderer need to draw RICKY. */
public interface RickyLook {
    int POSE_IDLE = 0, POSE_SLEEP = 1, POSE_SIT = 2, POSE_STRETCH = 3, POSE_CAST = 4, POSE_RAISE = 5, POSE_GROUND = 6,
            POSE_CROUCH = 7, POSE_PUNCH = 8, POSE_SMASH = 9, POSE_CUP = 10, POSE_BEAM = 11, POSE_FLY = 12, POSE_POWER = 13,
            POSE_THROW = 14, POSE_BARRAGE_A = 15, POSE_BARRAGE_B = 16, POSE_JAB_A = 17, POSE_JAB_B = 18, POSE_POINT = 19,
            POSE_DIVE = 20, POSE_SPREAD = 21,
    /** Dying: falling out of the sky on his back, slowly flailing. */
            POSE_FALLING = 22;

    /** No aura; phase 1's faint dark red; the Super Saiyan's gold. */
    int AURA_NONE = 0, AURA_DARK = 1, AURA_SAIYAN = 2;

    int poseState();

    /** Client side: how long he's been falling (his death), for the renderer to tip him over smoothly; 0 if he isn't. */
    default float fallTime(float tickDelta) {
        return 0f;
    }

    /** He's wearing the Super Saiyan skin (flickering on and off while he powers up, for good after). */
    boolean saiyan();

    /** Which aura is drawn round him. */
    int aura();

    /** True while he moves too fast to see: afterimages trail behind him. */
    boolean afterimages();
}
