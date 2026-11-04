package net.ragnar.fmscraft.photo;

/** Temporary, for the title screen panoramas: the seven friends' skins (textures/entity/skin_model/<id>.png). */
public enum SkinModelSkin {
    GUMMSS("gummss", false),
    LARUS("larus", true),
    LEONARD("leonard", false),
    MUGEN("mugen", false),
    ORNAMIX("ornamix", false),
    RAGNAR("ragnar", false),
    RICKY("ricky", false);

    private final String id;
    private final boolean slim;

    SkinModelSkin(String id, boolean slim) {
        this.id = id;
        this.slim = slim;
    }

    public String id() {
        return id;
    }

    /** 3-pixel arms (Alex) instead of 4 (Steve). */
    public boolean slim() {
        return slim;
    }
}
