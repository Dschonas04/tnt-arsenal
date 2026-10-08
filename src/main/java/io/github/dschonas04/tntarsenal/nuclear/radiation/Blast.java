package io.github.dschonas04.tntarsenal.nuclear.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * A detonation as the world remembers it, so that chunks which were not loaded
 * at the time still get their share of the radiation when they load.
 */
public record Blast(BlockPos center, int radius, float peak, long time) {
    public static final Codec<Blast> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(Blast::center),
            Codec.INT.fieldOf("radius").forGetter(Blast::radius),
            Codec.FLOAT.fieldOf("peak").forGetter(Blast::peak),
            Codec.LONG.fieldOf("time").forGetter(Blast::time)
    ).apply(i, Blast::new));

    /** Contamination reaches three times the blast radius. */
    public int reach() {
        return radius * 3;
    }

    /** The dose rate this blast left at a chunk's centre, at the time of the blast. */
    public float rateAt(int chunkX, int chunkZ) {
        double dx = (chunkX << 4) + 8 - center.getX();
        double dz = (chunkZ << 4) + 8 - center.getZ();
        double fall = 1.0 - Math.sqrt(dx * dx + dz * dz) / reach();
        return fall <= 0 ? 0f : (float) (peak * fall * fall);
    }
}
