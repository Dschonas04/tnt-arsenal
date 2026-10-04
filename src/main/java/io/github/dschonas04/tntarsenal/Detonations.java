package io.github.dschonas04.tntarsenal;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What each kind does when its fuse runs out. Every method gets the level and
 * the primed entity, which is already discarded but still knows its position,
 * its owner and the block state it carried.
 */
final class Detonations {
    private Detonations() {
    }

    private static final int FLAGS = Block.UPDATE_ALL;

    // --- helpers -------------------------------------------------------

    private static double y(PrimedTnt t) {
        return t.getY(0.0625);
    }

    static void blast(ServerLevel level, PrimedTnt t, float power, boolean fire) {
        level.explode(t, t.getX(), y(t), t.getZ(), power, fire, Level.ExplosionInteraction.TNT);
    }

    /** Bang and smoke without breaking anything. */
    private static void pop(ServerLevel level, PrimedTnt t, float power) {
        level.explode(t, t.getX(), y(t), t.getZ(), power, false, Level.ExplosionInteraction.NONE);
    }

    private static <T extends Entity> List<T> around(ServerLevel level, PrimedTnt t, Class<T> type, double radius) {
        AABB box = new AABB(t.getX() - radius, t.getY() - radius, t.getZ() - radius,
                t.getX() + radius, t.getY() + radius, t.getZ() + radius);
        return level.getEntitiesOfClass(type, box, e -> e.isAlive() && e.distanceToSqr(t) <= radius * radius);
    }

    private static boolean breakable(BlockState state) {
        return !state.isAir() && state.getBlock().defaultDestroyTime() >= 0 && !state.is(Blocks.BEDROCK);
    }

    private static Iterable<BlockPos> sphere(BlockPos center, int radius) {
        return BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))
                .filter(p -> p.distSqr(center) <= (double) radius * radius)
                .map(BlockPos::immutable)
                .toList();
    }

    // --- kinds ---------------------------------------------------------

    /** A very large explosion and a mushroom of smoke. */
    static void nuke(ServerLevel level, PrimedTnt t) {
        blast(level, t, 24, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.getX(), y(t) + 8, t.getZ(), 12, 6, 10, 6, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, t.getX(), y(t) + 20, t.getZ(), 300, 8, 6, 8, 0.02);
    }

    /** A normal explosion that also sets the surroundings alight. */
    static void fire(ServerLevel level, PrimedTnt t) {
        blast(level, t, 4, true);
        RandomSource random = level.getRandom();
        BlockPos center = t.blockPosition();
        for (BlockPos pos : sphere(center, 8)) {
            if (random.nextFloat() < 0.35f && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), FLAGS);
            }
        }
    }

    /** A ring of lightning around a small blast. */
    static void lightning(ServerLevel level, PrimedTnt t) {
        blast(level, t, 2, false);
        for (int i = 0; i <= 8; i++) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt == null) continue;
            double angle = Math.PI * 2 * i / 8;
            double r = i == 8 ? 0 : 5;
            bolt.setPos(t.getX() + Math.cos(angle) * r, t.getY(), t.getZ() + Math.sin(angle) * r);
            level.addFreshEntity(bolt);
        }
    }

    /** Bursts and flings eight ordinary TNT in all directions. */
    static void cluster(ServerLevel level, PrimedTnt t) {
        blast(level, t, 2, false);
        RandomSource random = level.getRandom();
        LivingEntity owner = t.getOwner();
        for (int i = 0; i < 8; i++) {
            PrimedTnt child = new PrimedTnt(level, t.getX(), t.getY() + 0.5, t.getZ(), owner);
            double angle = Math.PI * 2 * i / 8 + random.nextDouble() * 0.4;
            child.setDeltaMovement(Math.cos(angle) * 0.6, 0.55 + random.nextDouble() * 0.3, Math.sin(angle) * 0.6);
            child.setFuse(25 + random.nextInt(25));
            level.addFreshEntity(child);
        }
    }

    /** Blows a shaft straight down, 36 blocks deep. */
    static void drill(ServerLevel level, PrimedTnt t) {
        for (int i = 0; i < 12; i++) {
            double y = y(t) - i * 3;
            if (y <= level.getMinY() + 2) break;
            level.explode(t, t.getX(), y, t.getZ(), 3, false, Level.ExplosionInteraction.TNT);
        }
    }

    /** Blows a tunnel in the direction the block faced when it was placed. */
    static void tunnel(ServerLevel level, PrimedTnt t) {
        BlockState state = t.getBlockState();
        Direction facing = state.hasProperty(ArsenalTntBlock.FACING) ? state.getValue(ArsenalTntBlock.FACING) : Direction.NORTH;
        for (int i = 0; i < 14; i++) {
            double x = t.getX() + facing.getStepX() * i * 3;
            double z = t.getZ() + facing.getStepZ() * i * 3;
            level.explode(t, x, y(t) + 1, z, 3, false, Level.ExplosionInteraction.TNT);
        }
    }

    /** Clears a round building plot: everything from the TNT's height up, radius 8, 12 high. */
    static void leveler(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        BlockPos center = t.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, 0, -8), center.offset(8, 12, 8))) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > 64) continue;
            BlockState state = level.getBlockState(pos);
            if (breakable(state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Removes stone, dirt and gravel around it and leaves the ores standing. */
    static void miner(ServerLevel level, PrimedTnt t) {
        pop(level, t, 2);
        for (BlockPos pos : sphere(t.blockPosition(), 6)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL)
                    || state.is(Blocks.SAND) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Explodes and floods the crater. */
    static void water(ServerLevel level, PrimedTnt t) {
        blast(level, t, 3, false);
        fill(level, t, Blocks.WATER.defaultBlockState());
    }

    /** Explodes and fills the crater with lava. */
    static void lava(ServerLevel level, PrimedTnt t) {
        blast(level, t, 3, false);
        fill(level, t, Blocks.LAVA.defaultBlockState());
    }

    private static void fill(ServerLevel level, PrimedTnt t, BlockState liquid) {
        BlockPos center = t.blockPosition();
        for (BlockPos pos : sphere(center, 3)) {
            if (pos.getY() <= center.getY() && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, liquid, FLAGS);
            }
        }
    }

    /** Freezes water and lava, drops snow and chills every creature around. */
    static void frost(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        BlockPos center = t.blockPosition();
        for (BlockPos pos : sphere(center, 8)) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(Fluids.WATER) && state.is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), FLAGS);
            } else if (state.is(Blocks.LAVA)) {
                level.setBlock(pos, state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
            } else if (state.isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState(), FLAGS);
            }
        }
        for (LivingEntity entity : around(level, t, LivingEntity.class, 9)) {
            entity.setTicksFrozen(entity.getTicksRequiredToFreeze() + 200);
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 3));
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), y(t) + 1, t.getZ(), 400, 5, 2, 5, 0.05);
    }

    /** A small bang that leaves a lingering cloud of poison. */
    static void poison(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        cloud(level, t, 6, 600, new MobEffectInstance(MobEffects.POISON, 200, 1));
    }

    /** A cloud that heals instead of hurting — nothing breaks. */
    static void healing(ServerLevel level, PrimedTnt t) {
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0f, 1.0f);
        cloud(level, t, 6, 400, new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        for (LivingEntity entity : around(level, t, LivingEntity.class, 6)) entity.heal(8);
        level.sendParticles(ParticleTypes.HEART, t.getX(), y(t) + 1, t.getZ(), 40, 3, 1, 3, 0.1);
    }

    private static void cloud(ServerLevel level, PrimedTnt t, float radius, int duration, MobEffectInstance effect) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, t.getX(), t.getY(), t.getZ());
        cloud.setRadius(radius);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(-radius / duration);
        cloud.setWaitTime(0);
        cloud.addEffect(effect);
        LivingEntity owner = t.getOwner();
        if (owner != null) cloud.setOwner(owner);
        level.addFreshEntity(cloud);
    }

    /** Pulls everything nearby into the middle, then explodes. */
    static void gravity(ServerLevel level, PrimedTnt t) {
        Vec3 center = t.position();
        for (Entity entity : around(level, t, Entity.class, 16)) {
            Vec3 pull = center.subtract(entity.position()).normalize().scale(1.6).add(0, 0.3, 0);
            entity.setDeltaMovement(pull);
            entity.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), y(t) + 1, t.getZ(), 300, 6, 3, 6, 0.4);
        blast(level, t, 5, false);
    }

    /** Scatters every creature around it like a chorus fruit, just stronger. */
    static void ender(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        RandomSource random = level.getRandom();
        for (LivingEntity entity : around(level, t, LivingEntity.class, 10)) {
            for (int attempt = 0; attempt < 16; attempt++) {
                double x = entity.getX() + (random.nextDouble() - 0.5) * 48;
                double y = entity.getY() + random.nextInt(16) - 8;
                double z = entity.getZ() + (random.nextDouble() - 0.5) * 48;
                if (entity.randomTeleport(x, y, z, true)) break;
            }
        }
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 2.0f, 0.8f);
        level.sendParticles(ParticleTypes.PORTAL, t.getX(), y(t) + 1, t.getZ(), 400, 4, 2, 4, 0.5);
    }

    /** Launches everything around it into the air without breaking a block. */
    static void bounce(ServerLevel level, PrimedTnt t) {
        pop(level, t, 1);
        for (Entity entity : around(level, t, Entity.class, 8)) {
            Vec3 away = entity.position().subtract(t.position()).multiply(1, 0, 1);
            Vec3 push = away.lengthSqr() < 0.01 ? Vec3.ZERO : away.normalize().scale(0.8);
            entity.setDeltaMovement(push.x, 2.4, push.z);
            entity.hurtMarked = true;
        }
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.BLOCKS, 2.0f, 0.6f);
    }

    /** All show, no damage. */
    static void firework(ServerLevel level, PrimedTnt t) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 6; i++) {
            double x = t.getX() + (random.nextDouble() - 0.5) * 6;
            double z = t.getZ() + (random.nextDouble() - 0.5) * 6;
            double height = 6 + random.nextDouble() * 8;
            level.sendParticles(ParticleTypes.FIREWORK, x, t.getY() + height, z, 120, 1.2, 1.2, 1.2, 0.25);
            level.sendParticles(ParticleTypes.END_ROD, x, t.getY() + height, z, 40, 0.8, 0.8, 0.8, 0.15);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 10, t.getZ(), 4, 2, 2, 2, 0);
        level.playSound(null, t.getX(), t.getY() + 10, t.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 4.0f, 1.0f);
        level.playSound(null, t.getX(), t.getY() + 10, t.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, 4.0f, 1.0f);
    }
}
