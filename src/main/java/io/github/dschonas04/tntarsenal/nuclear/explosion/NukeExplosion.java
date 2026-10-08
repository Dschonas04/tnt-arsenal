package io.github.dschonas04.tntarsenal.nuclear.explosion;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The nuclear explosion, spread over many ticks instead of Minecraft's explosion,
 * which would freeze the server at this size. It works outwards shell by shell
 * from ground zero; the distance is measured with the depth stretched by 1.3, so
 * the crater comes out a little flatter than a ball.
 *
 * <ul>
 * <li><b>core</b> (under 0.55 R): everything goes except bedrock and other unbreakables;</li>
 * <li><b>crater</b> (up to 0.8 R): everything goes except obsidian-hard blocks;</li>
 * <li><b>rim</b> (up to R): a ragged edge — the further in, the more is torn out; what
 *     stays is charred: grass and dirt turn to coarse dirt, logs to charred logs,
 *     leaves and plants burn away;</li>
 * <li><b>outer zone</b> (up to 1.6 R): glass and ice shatter, leaves fall, fires start.</li>
 * </ul>
 *
 * The blast wave travels with the shells: whatever the front reaches is hurt and
 * thrown outwards, less if a wall stands between it and ground zero; inside the
 * core nothing survives. Each player hears the bang when the sound gets there.
 */
public final class NukeExplosion implements Jobs.Job {
    private static final double CORE = 0.55;
    private static final double CRATER = 0.8;
    private static final double OUTER = 1.6;
    private static final double DEPTH = 1.3;
    private static final long TICK_BUDGET_NANOS = 25_000_000L;
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "glass_panes"));

    private final ServerLevel level;
    private final BlockPos center;
    private final Vec3 origin;
    private final int radius;
    private final int outer;
    private final Entity source;
    private final LivingEntity owner;
    private final RandomSource random;
    private final List<long[]> forcedChunks = new ArrayList<>();

    private int shell;
    private int[] positions = new int[0];
    private int count;
    private int index;
    private double waveDone;

    public NukeExplosion(ServerLevel level, BlockPos center, int radius, Entity source, LivingEntity owner) {
        this.level = level;
        this.center = center;
        this.origin = Vec3.atCenterOf(center);
        this.radius = radius;
        this.outer = (int) Math.ceil(radius * OUTER);
        this.source = source;
        this.owner = owner;
        this.random = level.getRandom();
        // Keep the whole crater loaded until it is done, even if everyone runs.
        int reach = radius + 8;
        for (int cx = (center.getX() - reach) >> 4; cx <= (center.getX() + reach) >> 4; cx++) {
            for (int cz = (center.getZ() - reach) >> 4; cz <= (center.getZ() + reach) >> 4; cz++) {
                if (level.setChunkForced(cx, cz, true)) forcedChunks.add(new long[]{cx, cz});
            }
        }
        NuclearTnt.LOG.info("Nuclear detonation at {} with radius {} ({} chunks held)", center, radius, forcedChunks.size());
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public boolean tick() {
        long until = System.nanoTime() + TICK_BUDGET_NANOS;
        int budget = NukeConfig.get().blocksPerTick;
        int changed = 0;
        while (changed < budget && System.nanoTime() < until) {
            if (index >= count) {
                if (shell > outer) {
                    blastWave(outer * 2.0);
                    release();
                    return true;
                }
                buildShell(shell++);
                continue;
            }
            int packed = positions[index++];
            int dx = (packed >> 20) - 512;
            int dy = ((packed >> 10) & 1023) - 512;
            int dz = (packed & 1023) - 512;
            if (affect(center.offset(dx, dy, dz), dx, dy, dz)) changed++;
        }
        blastWave(Math.max(0, shell - 1) * 2.0);
        return false;
    }

    /** All positions whose stretched distance lies in [r, r + 1). */
    private void buildShell(int r) {
        count = 0;
        index = 0;
        double lo = (double) r * r;
        double hi = (r + 1.0) * (r + 1.0);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double flat = dx * dx + dz * dz;
                if (flat >= hi) continue;
                int up = (int) Math.floor(Math.sqrt(hi - flat - 1e-9));
                int down = (int) Math.floor(Math.sqrt((hi - flat - 1e-9) / (DEPTH * DEPTH)));
                for (int dy = -down; dy <= up; dy++) {
                    double stretch = dy < 0 ? dy * DEPTH : dy;
                    double d = flat + stretch * stretch;
                    if (d < lo || d >= hi) continue;
                    if (count == positions.length) positions = java.util.Arrays.copyOf(positions, Math.max(1024, count * 2));
                    positions[count++] = ((dx + 512) << 20) | ((dy + 512) << 10) | (dz + 512);
                }
            }
        }
    }

    /** Changes one block according to its zone. Returns true if anything changed. */
    private boolean affect(BlockPos pos, int dx, int dy, int dz) {
        if (!level.isLoaded(pos) || pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        double stretch = dy < 0 ? dy * DEPTH : dy;
        double e = Math.sqrt(dx * dx + dz * dz + stretch * stretch) / radius;
        boolean unbreakable = state.getDestroySpeed(level, pos) < 0;
        if (e < CORE) {
            return !unbreakable && vaporise(pos);
        }
        boolean hard = unbreakable || state.getBlock().getExplosionResistance() >= 600;
        if (e < CRATER) {
            return !hard && vaporise(pos);
        }
        if (e < 1.0) {
            // the further in, the more of the rim is torn out
            if (!hard && random.nextDouble() < (1.0 - e) / (1.0 - CRATER) * 0.85) return vaporise(pos);
            return !hard && burn(pos, state);
        }
        if (hard) return false;
        double fade = (OUTER - e) / (OUTER - 1.0);
        if (state.is(BlockTags.IMPERMEABLE) || state.is(GLASS_PANES) || state.is(Blocks.ICE)) {
            return vaporise(pos);
        }
        if (state.is(BlockTags.LEAVES) && random.nextDouble() < fade) return vaporise(pos);
        if (random.nextDouble() < 0.02 * fade && level.getBlockState(pos.above()).isAir()
                && state.isFaceSturdy(level, pos, Direction.UP)) {
            level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private boolean vaporise(BlockPos pos) {
        // No drops and no spilled container contents: vaporised is vaporised.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        return true;
    }

    /** Burns a block that survived at the crater's edge. */
    private boolean burn(BlockPos pos, BlockState state) {
        BlockState into = null;
        if (state.is(BlockTags.SUBSTRATE_OVERWORLD)) {
            into = Blocks.COARSE_DIRT.defaultBlockState();
        } else if (state.is(BlockTags.LOGS)) {
            into = NuclearTnt.CHARRED_LOG.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                into = into.setValue(RotatedPillarBlock.AXIS, state.getValue(BlockStateProperties.AXIS));
            }
        } else if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LiquidBlock
                || (state.canBeReplaced() && state.getFluidState().isEmpty())) {
            into = Blocks.AIR.defaultBlockState();
        }
        if (into == null || into == state) return false;
        level.setBlock(pos, into, Block.UPDATE_CLIENTS);
        if (into.isAir() && random.nextFloat() < 0.04f && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /** Hits every entity the front has reached since the last tick. */
    private void blastWave(double front) {
        double reach = Math.min(front, radius * 2.0);
        if (reach <= waveDone) return;
        double from = waveDone;
        waveDone = reach;
        AABB box = AABB.ofSize(origin, reach * 2, reach * 2, reach * 2);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            double dist = Math.sqrt(entity.distanceToSqr(origin));
            if (dist < from || dist >= reach) continue;
            if (dist < radius * CORE) {
                if (entity instanceof LivingEntity living) {
                    living.hurtServer(level, level.damageSources().explosion(source, owner), 10000f);
                } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                    entity.discard();
                }
                continue;
            }
            double strength = 1.0 - dist / (radius * 2.0);
            boolean covered = level.clip(new ClipContext(origin, entity.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.BLOCK;
            double factor = covered ? 0.3 : 1.0;
            Vec3 away = entity.position().subtract(origin);
            Vec3 dir = away.lengthSqr() < 0.01 ? new Vec3(0, 1, 0) : away.normalize();
            entity.setDeltaMovement(entity.getDeltaMovement().add(dir.scale(strength * 4.0 * factor)).add(0, strength * factor, 0));
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) {
                float damage = (float) (radius * 2.5 * strength * strength * factor);
                living.hurtServer(level, level.damageSources().explosion(source, owner), damage);
                if (!covered && strength > 0.4) living.igniteForSeconds(8);
            }
        }
        if (reach > 2) {
            level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 4, reach * 0.5, 2, reach * 0.5, 0);
        }
    }

    private void release() {
        for (long[] chunk : forcedChunks) level.setChunkForced((int) chunk[0], (int) chunk[1], false);
        forcedChunks.clear();
        NuclearTnt.LOG.info("Nuclear detonation at {} finished", center);
    }

    @Override
    public void abort() {
        release();
    }
}
