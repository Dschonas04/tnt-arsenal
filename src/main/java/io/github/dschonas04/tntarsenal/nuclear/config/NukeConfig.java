package io.github.dschonas04.tntarsenal.nuclear.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.block.Tier;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in {@code config/tnt_arsenal-nuclear.json}. Missing entries keep their
 * defaults, and the file is rewritten on load so new entries show up in it.
 */
public final class NukeConfig {
    public int miniRadius = 15;
    public int nukeRadius = 40;
    public int tsarRadius = 80;

    public int miniFuseTicks = 100;
    public int nukeFuseTicks = 200;
    public int tsarFuseTicks = 400;

    /** How many blocks the explosion may change per server tick. */
    public int blocksPerTick = 2000;

    /** Radiation: how long until a chunk's dose rate has halved (72000 ticks = three Minecraft days). */
    public long halfLifeTicks = 72000;

    /** Radiation: the dose rate at ground zero right after the blast, in mSv per second. */
    public float miniPeakRate = 2f;
    public float nukePeakRate = 8f;
    public float tsarPeakRate = 25f;

    public float peakRate(Tier tier) {
        return switch (tier) {
            case MINI -> miniPeakRate;
            case NUKE -> nukePeakRate;
            case TSAR -> tsarPeakRate;
        };
    }

    /** Fallout: ticks after the detonation before the ash starts to come down, and how long it takes. */
    public int falloutDelayTicks = 200;
    public int falloutTicks = 600;

    /** Client: how thick the green fog in contaminated land gets, 0 turns it off. */
    public float fogStrength = 1f;

    /** Client: how many particles the mushroom cloud may spawn per tick. */
    public int cloudParticlesPerTick = 160;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NukeConfig current = new NukeConfig();

    public static NukeConfig get() {
        return current;
    }

    public int radius(Tier tier) {
        return switch (tier) {
            case MINI -> miniRadius;
            case NUKE -> nukeRadius;
            case TSAR -> tsarRadius;
        };
    }

    public int fuse(Tier tier) {
        return switch (tier) {
            case MINI -> miniFuseTicks;
            case NUKE -> nukeFuseTicks;
            case TSAR -> tsarFuseTicks;
        };
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("tnt_arsenal-nuclear.json");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                NukeConfig read = GSON.fromJson(reader, NukeConfig.class);
                if (read != null) current = read;
            } catch (IOException | RuntimeException e) {
                NuclearTnt.LOG.error("Could not read {}, using defaults", file, e);
            }
        }
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(current, writer);
        } catch (IOException e) {
            NuclearTnt.LOG.warn("Could not write {}", file, e);
        }
    }
}
