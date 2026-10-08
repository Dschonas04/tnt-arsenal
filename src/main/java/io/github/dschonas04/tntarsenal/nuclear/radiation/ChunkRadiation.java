package io.github.dschonas04.tntarsenal.nuclear.radiation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The radiation of one chunk: the dose rate in mSv per second as it was at game
 * time {@code since}. It decays with the configured half-life; the current value
 * is worked out when someone asks, not every tick.
 */
public record ChunkRadiation(float value, long since) {
    public static final ChunkRadiation NONE = new ChunkRadiation(0f, 0L);

    public static final Codec<ChunkRadiation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("value").forGetter(ChunkRadiation::value),
            Codec.LONG.fieldOf("since").forGetter(ChunkRadiation::since)
    ).apply(i, ChunkRadiation::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkRadiation> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ChunkRadiation::value,
            ByteBufCodecs.VAR_LONG, ChunkRadiation::since,
            ChunkRadiation::new);

    /** The dose rate at {@code now}, after decay. */
    public float at(long now, long halfLife) {
        if (value <= 0) return 0f;
        long elapsed = Math.max(0, now - since);
        return (float) (value * Math.pow(0.5, (double) elapsed / Math.max(1, halfLife)));
    }

    /** This chunk with more radiation added on top of what is left now. */
    public ChunkRadiation plus(float more, long now, long halfLife) {
        return new ChunkRadiation(at(now, halfLife) + more, now);
    }
}
