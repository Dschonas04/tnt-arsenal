package io.github.dschonas04.tntarsenal.nuclear.block;

/**
 * The three sizes of bomb. Radius and fuse come from the config; the tier only
 * names them and says which config entry applies.
 */
public enum Tier {
    MINI("mini_nuke"),
    NUKE("nuke"),
    TSAR("tsar_bomba");

    private final String id;

    Tier(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
