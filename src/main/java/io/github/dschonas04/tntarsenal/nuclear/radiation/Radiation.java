package io.github.dschonas04.tntarsenal.nuclear.radiation;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.chunk.LevelChunk;
import com.mojang.serialization.Codec;

/**
 * Radiation: a dose rate per chunk, a dose per player, and what the dose does.
 *
 * <p>Chunks carry their rate as an attachment that is saved with them and sent to
 * clients. A detonation raises the rate of every loaded chunk within three blast
 * radii and is remembered by the level; a chunk that loads later catches up on
 * the blasts it missed. Rates halve every half-life.
 *
 * <p>Players carry their dose in mSv, saved and sent to their own client. Every
 * second they take in the rate of the chunk they stand in, less what the blocks
 * over their head and water around them shield. Out of the radiation the dose
 * slowly goes down. The stages: from 100 mSv nausea, from 250 weakness and
 * hunger, from 500 radiation damage, from 1000 far more of it.
 */
public final class Radiation {
    private Radiation() {
    }

    public static final AttachmentType<ChunkRadiation> CHUNK = AttachmentRegistry.create(NuclearTnt.id("chunk_radiation"),
            b -> b.persistent(ChunkRadiation.CODEC).syncWith(ChunkRadiation.STREAM_CODEC, AttachmentSyncPredicate.all()));
    public static final AttachmentType<Float> DOSE = AttachmentRegistry.create(NuclearTnt.id("dose"),
            b -> b.persistent(Codec.FLOAT).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
    public static final AttachmentType<List<Blast>> BLASTS = AttachmentRegistry.create(NuclearTnt.id("blasts"),
            b -> b.persistent(Blast.CODEC.listOf()));

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, NuclearTnt.id("radiation"));

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> catchUp(level, chunk));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) expose(level, player);
            }
        });
    }

    /** Fabric injects AttachmentTarget into these classes at runtime; without Loom the compiler does not know. */
    private static AttachmentTarget at(Object target) {
        return (AttachmentTarget) target;
    }

    private static long halfLife() {
        return NukeConfig.get().halfLifeTicks;
    }

    /** The current dose rate in the chunk at a position, in mSv per second. */
    public static float rateAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 0f;
        ChunkRadiation radiation = at(level.getChunkAt(pos)).getAttached(CHUNK);
        return radiation == null ? 0f : radiation.at(level.getGameTime(), halfLife());
    }

    public static float dose(ServerPlayer player) {
        Float dose = at(player).getAttached(DOSE);
        return dose == null ? 0f : dose;
    }

    public static void setDose(ServerPlayer player, float dose) {
        if (dose <= 0) at(player).removeAttached(DOSE);
        else at(player).setAttached(DOSE, dose);
    }

    /** Contaminates the land around a detonation and remembers it for chunks loaded later. */
    public static void contaminate(ServerLevel level, BlockPos center, int radius, float peak) {
        long now = level.getGameTime();
        Blast blast = new Blast(center, radius, peak, now);
        List<Blast> blasts = new ArrayList<>(at(level).getAttachedOrElse(BLASTS, List.of()));
        // blasts that have decayed to nothing are forgotten
        blasts.removeIf(old -> old.peak() * Math.pow(0.5, (double) (now - old.time()) / halfLife()) < 0.01);
        blasts.add(blast);
        at(level).setAttached(BLASTS, List.copyOf(blasts));
        int reach = blast.reach();
        for (int cx = (center.getX() - reach) >> 4; cx <= (center.getX() + reach) >> 4; cx++) {
            for (int cz = (center.getZ() - reach) >> 4; cz <= (center.getZ() + reach) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) add(chunk, blast.rateAt(cx, cz), now);
            }
        }
    }

    private static void add(LevelChunk chunk, float rate, long now) {
        if (rate <= 0.001f) return;
        ChunkRadiation old = at(chunk).getAttachedOrElse(CHUNK, ChunkRadiation.NONE);
        at(chunk).setAttached(CHUNK, old.plus(rate, now, halfLife()));
        chunk.markUnsaved();
    }

    /** A chunk that was unloaded during a blast gets that blast's radiation now, decayed by the time since. */
    private static void catchUp(ServerLevel level, LevelChunk chunk) {
        List<Blast> blasts = at(level).getAttached(BLASTS);
        if (blasts == null || blasts.isEmpty()) return;
        ChunkRadiation current = at(chunk).getAttachedOrElse(CHUNK, ChunkRadiation.NONE);
        long now = level.getGameTime();
        float missed = 0f;
        for (Blast blast : blasts) {
            if (blast.time() <= current.since()) continue;
            float rate = blast.rateAt(chunk.getPos().x(), chunk.getPos().z());
            missed += (float) (rate * Math.pow(0.5, (double) (now - blast.time()) / halfLife()));
        }
        if (missed > 0.001f) add(chunk, missed, now);
    }

    /** How much of the outside radiation reaches a player: roofs and water shield. */
    public static float shielding(ServerLevel level, ServerPlayer player) {
        BlockPos head = BlockPos.containing(player.getEyePosition());
        float factor = 1f;
        for (int dy = 1; dy <= 12; dy++) {
            BlockPos pos = head.above(dy);
            if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) factor *= 0.7f;
        }
        if (player.isUnderWater()) factor *= 0.5f;
        return factor;
    }

    private static void expose(ServerLevel level, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        float rate = rateAt(level, player.blockPosition()) * shielding(level, player);
        float dose = dose(player);
        if (rate > 0.001f) dose += rate;
        else dose = Math.max(0f, dose - 0.2f);
        setDose(player, dose);
        if (dose >= 100 && player.getRandom().nextFloat() < 0.15f) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
        }
        if (dose >= 250) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, dose >= 1000 ? 1 : 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1));
        }
        if (dose >= 1000) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
            player.hurtServer(level, damage(level), 2f);
        } else if (dose >= 500 && level.getGameTime() % 40 < 20) {
            player.hurtServer(level, damage(level), 1f);
        }
    }

    public static DamageSource damage(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
    }
}
