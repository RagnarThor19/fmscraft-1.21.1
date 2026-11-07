package net.ragnar.fmscraft.boss.envy;

import java.util.UUID;

/** What GummssModel needs to pose GUMMSS, and his Mirror Images, which look exactly like him. */
public interface GummssLook {
    int POSE_IDLE = 0, POSE_CAST = 1, POSE_PUSH = 2, POSE_PULL = 3, POSE_LIFT = 4, POSE_SLAM = 5, POSE_THROW = 6,
            POSE_SLASH_A = 7, POSE_SLASH_B = 8, POSE_SKY = 9, POSE_FALL = 10, POSE_LAND = 11, POSE_POINT = 12,
            POSE_STARE = 13, POSE_CATCH = 14, POSE_STUNNED = 15;
    /** His Disguise: not on; both of them shut in the cups (neither is drawn); his face and theirs traded. */
    int GUISE_OFF = 0, GUISE_HIDDEN = 1, GUISE_WORN = 2;

    int poseState();

    /** How far over he's rolled, in degrees (180: upside down, after the Tome of Upside Down). */
    default float flipRoll(float tickDelta) {
        return 0f;
    }

    /** Whose face he's wearing (or about to), null if nobody's. */
    default UUID guisePlayer() {
        return null;
    }

    default int guiseStage() {
        return GUISE_OFF;
    }
}
