package io.github.dschonas04.tntarsenal.nuclear.entity;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.block.NukeBlock;
import io.github.dschonas04.tntarsenal.nuclear.block.Tier;
import io.github.dschonas04.tntarsenal.nuclear.explosion.Detonation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;

/**
 * A lit bomb. It is a primed TNT in every respect the client cares about — it
 * falls, flashes and renders with the bomb's block — but it has its own entity
 * type, beeps faster and faster as the fuse runs down, and does not explode the
 * vanilla way: a mixin hands the moment of detonation to {@link Detonation}.
 */
public class PrimedNuke extends PrimedTnt {
    private LivingEntity igniter;

    public PrimedNuke(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
    }

    public static PrimedNuke create(Level level, double x, double y, double z, NukeBlock block, LivingEntity igniter, int fuse) {
        PrimedNuke nuke = new PrimedNuke(NuclearTnt.PRIMED_NUKE, level);
        nuke.setPos(x, y, z);
        nuke.setBlockState(block.defaultBlockState());
        nuke.setFuse(fuse);
        nuke.igniter = igniter;
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        nuke.setDeltaMovement(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02);
        nuke.xo = x;
        nuke.yo = y;
        nuke.zo = z;
        return nuke;
    }

    public Tier tier() {
        return getBlockState().getBlock() instanceof NukeBlock block ? block.tier() : Tier.NUKE;
    }

    public LivingEntity igniter() {
        return igniter;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        int fuse = getFuse();
        // A slow beep at first, then faster and higher towards the end.
        int every = fuse > 200 ? 20 : fuse > 100 ? 10 : fuse > 40 ? 5 : 2;
        if (fuse % every == 0) {
            float pitch = fuse > 100 ? 1.2f : fuse > 40 ? 1.6f : 2.0f;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 2.0f, pitch);
        }
    }

    /** Called by the mixin instead of the vanilla explosion. */
    public void detonate(ServerLevel level) {
        Detonation.start(level, this);
    }
}
