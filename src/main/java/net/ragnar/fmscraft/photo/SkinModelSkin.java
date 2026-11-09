package net.ragnar.fmscraft.photo;

import org.jetbrains.annotations.Nullable;

/**
 * Temporary, for the title screen panoramas: the seven friends' skins (textures/entity/skin_model/<id>.png, /ragnar_skin),
 * and each one's boss in his second phase (/raggimel2): the boss's own skin, his eyes burning (the boss's own glow
 * layer), an aura round him (the charged creeper's shimmer, in his colour) and his size.
 * Only ever append: a model stores its skin by position.
 */
public enum SkinModelSkin {
    GUMMSS("gummss", false),
    LARUS("larus", true),
    LEONARD("leonard", false),
    MUGEN("mugen", false),
    ORNAMIX("ornamix", false),
    RAGNAR("ragnar", false),
    RICKY("ricky", false),
    /** Giant, as for the Giant Slam: his phase 2 size times four. */
    RAGGIMEL2("raggimel2", "raggimel/raggimel", false, "raggimel/raggimel_eyes", 0x4FA8FF, 1.5f * 4f, false),
    GUMMSS2("gummss2", "gummss/gummss", false, "gummss/gummss_eyes", 0x50FF60, 1f, false),
    /** Three blocks tall. */
    LALLI2("lalli2", "lalli/lalli", true, "lalli/lalli_eyes", 0x9FE6FF, 3f / 1.8f, false),
    /** CONEMAN: five blocks tall, the traffic cone over his head (so his shades don't show). */
    LEONARD2("leonard2", "leonard/leonard", false, "leonard/leonard_eyes", 0xFFE640, 5f / 1.8f, true),
    MUGEN2("mugen2", "mugen/mugen", false, "mugen/mugen_eyes_red", 0xE01828, 1f, false),
    /** Ragemode. */
    ORNAMIX2("ornamix2", "ornamix/ornamix", false, "ornamix/ornamix_eyes", 0xFF6A18, 1f, false),
    /** Super Saiyan. */
    RICKY2("ricky2", "ricky/ricky_saiyan", false, "ricky/ricky_saiyan_glow", 0xFFC83C, 1f, false);

    private final String id;
    private final String texture;
    private final boolean slim;
    @Nullable
    private final String eyes;
    private final int aura;
    private final float scale;
    private final boolean cone;

    SkinModelSkin(String id, boolean slim) {
        this(id, "skin_model/" + id, slim, null, -1, 1f, false);
    }

    SkinModelSkin(String id, String texture, boolean slim, @Nullable String eyes, int aura, float scale, boolean cone) {
        this.id = id;
        this.texture = texture;
        this.slim = slim;
        this.eyes = eyes;
        this.aura = aura;
        this.scale = scale;
        this.cone = cone;
    }

    public String id() {
        return id;
    }

    /** /ragnar_skin for a friend, /raggimel2 for a boss. */
    public String command() {
        return boss() ? id : id + "_skin";
    }

    public boolean boss() {
        return eyes != null;
    }

    /** Under textures/entity/, without .png. */
    public String texture() {
        return texture;
    }

    /** 3-pixel arms (Alex) instead of 4 (Steve). */
    public boolean slim() {
        return slim;
    }

    /** The glowing layer, under textures/entity/ without .png; null for a friend. */
    @Nullable
    public String eyes() {
        return eyes;
    }

    /** The aura's colour (0xRRGGBB); -1 for none. */
    public int aura() {
        return aura;
    }

    /** How big he is (the scale attribute). */
    public float scale() {
        return scale;
    }

    /** CONEMAN's traffic cone over his head. */
    public boolean cone() {
        return cone;
    }
}
