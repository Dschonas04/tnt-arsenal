package io.github.dschonas04.tntarsenal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;

/**
 * Every TNT kind: its registry name, fuse in ticks and what happens when it goes off.
 * Vanilla TNT has a power of 4 and an 80-tick fuse; the numbers below are relative to that.
 */
public enum Kind {
    MEGA("mega_tnt", 80, (l, t) -> Detonations.blast(l, t, 8, false)),
    GIGA("giga_tnt", 100, Detonations::giga),
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
    HEALING("healing_tnt", 40, Detonations::healing),
    BLACK_HOLE("black_hole_tnt", 100, Detonations::blackHole),
    ANTIGRAVITY("antigravity_tnt", 60, Detonations::antigravity),
    BRIDGE("bridge_tnt", 40, Detonations::bridge),
    WALL("wall_tnt", 40, Detonations::wall),
    HORDE("horde_tnt", 60, Detonations::horde),
    SONIC("sonic_tnt", 60, Detonations::sonic),
    SMOKE("smoke_tnt", 40, Detonations::smoke),
    XRAY("xray_tnt", 40, Detonations::xray),
    NATURE("nature_tnt", 40, Detonations::nature),
    ANTIMATTER("antimatter_tnt", 120, Detonations::antimatter),
    NETHER("nether_tnt", 60, Detonations::nether),
    METEOR("meteor_tnt", 60, Detonations::meteor),
    ARROW_RAIN("arrow_rain_tnt", 40, Detonations::arrowRain),
    COBWEB("cobweb_tnt", 40, Detonations::cobweb),
    STORM("storm_tnt", 60, Detonations::storm),
    MAGNET("magnet_tnt", 40, Detonations::magnet),
    SPONGE("sponge_tnt", 40, Detonations::sponge),
    TORCH("torch_tnt", 40, Detonations::torch),
    SHOCKWAVE("shockwave_tnt", 60, Detonations::shockwave),
    DOME("dome_tnt", 40, Detonations::dome),
    CHAIN("chain_tnt", 40, Detonations::chain),
    LUMBER("lumber_tnt", 40, Detonations::lumber),
    HARVEST("harvest_tnt", 40, Detonations::harvest),
    SCULK("sculk_tnt", 40, Detonations::sculk),
    DESERT("desert_tnt", 40, Detonations::desert),
    END("end_tnt", 40, Detonations::end),
    RAINBOW("rainbow_tnt", 40, Detonations::rainbow),
    EARTHQUAKE("earthquake_tnt", 80, Detonations::earthquake),
    VOLCANO("volcano_tnt", 60, Detonations::volcano),
    STAIRCASE("staircase_tnt", 40, Detonations::staircase),
    BUNKER("bunker_tnt", 40, Detonations::bunker),
    PLATFORM("platform_tnt", 30, Detonations::platform),
    TOWER("tower_tnt", 30, Detonations::tower),
    CURSE("curse_tnt", 40, Detonations::curse),
    BLESSING("blessing_tnt", 30, Detonations::blessing),
    STASIS("stasis_tnt", 40, Detonations::stasis),
    BEE("bee_tnt", 40, Detonations::bees),
    WOLF("wolf_tnt", 30, Detonations::wolves),
    GOLEM("golem_tnt", 30, Detonations::golems),
    FLAK("flak_tnt", 20, Detonations::flak),
    HALLOWEEN("halloween_tnt", 40, Detonations::halloween);

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
