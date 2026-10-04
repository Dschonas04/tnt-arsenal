package io.github.dschonas04.tntarsenal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;

/**
 * Every TNT kind: its registry name, fuse in ticks and what happens when it goes off.
 * Vanilla TNT has a power of 4 and an 80-tick fuse; the numbers below are relative to that.
 */
public enum Kind {
    MEGA("mega_tnt", 80, (l, t) -> Detonations.blast(l, t, 8, false)),
    GIGA("giga_tnt", 100, (l, t) -> Detonations.blast(l, t, 16, false)),
    NUKE("nuke_tnt", 160, Detonations::nuke),
    FIRE("fire_tnt", 80, Detonations::fire),
    LIGHTNING("lightning_tnt", 80, Detonations::lightning),
    CLUSTER("cluster_tnt", 60, Detonations::cluster),
    DRILL("drill_tnt", 60, Detonations::drill),
    TUNNEL("tunnel_tnt", 60, Detonations::tunnel),
    LEVELER("leveler_tnt", 60, Detonations::leveler),
    MINER("miner_tnt", 60, Detonations::miner),
    WATER("water_tnt", 60, Detonations::water),
    LAVA("lava_tnt", 60, Detonations::lava),
    FROST("frost_tnt", 60, Detonations::frost),
    POISON("poison_tnt", 60, Detonations::poison),
    GRAVITY("gravity_tnt", 80, Detonations::gravity),
    ENDER("ender_tnt", 60, Detonations::ender),
    BOUNCE("bounce_tnt", 40, Detonations::bounce),
    FIREWORK("firework_tnt", 40, Detonations::firework),
    HEALING("healing_tnt", 40, Detonations::healing);

    @FunctionalInterface
    public interface Detonation {
        void detonate(ServerLevel level, PrimedTnt tnt);
    }

    private final String id;
    private final int fuse;
    private final Detonation detonation;

    Kind(String id, int fuse, Detonation detonation) {
        this.id = id;
        this.fuse = fuse;
        this.detonation = detonation;
    }

    public String id() {
        return id;
    }

    public int fuse() {
        return fuse;
    }

    public void detonate(ServerLevel level, PrimedTnt tnt) {
        detonation.detonate(level, tnt);
    }
}
