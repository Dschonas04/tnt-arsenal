package io.github.dschonas04.tntarsenal.nuclear.explosion;

import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import io.github.dschonas04.tntarsenal.nuclear.world.Contamination;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The fallout after a detonation. After a delay the wind carries the ash
 * downwind and it settles over half a minute: a glowing layer, thickest along
 * the plume. Near ground zero the land itself changes: grass dies to
 * irradiated earth, sand melts to trinitite, water is contaminated.
 * Columns in unloaded chunks are left alone.
 */
public final class Fallout implements Jobs.Job {
    private static final long BUDGET_NANOS = 4_000_000L;

    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final double windX;
    private final double windZ;
    private final double drift;
    private final LongArrayList columns = new LongArrayList();
    private final RandomSource random;
    private final int perTick;
    private int delay;
    private int next;

    public Fallout(ServerLevel level, BlockPos center, int radius) {
        this.level = level;
        this.center = center;
        this.radius = radius;
        this.random = RandomSource.create(level.getRandom().nextLong());
        double angle = random.nextDouble() * Math.PI * 2;
        this.windX = Math.cos(angle);
        this.windZ = Math.sin(angle);
        this.drift = radius * 0.8;
        this.delay = NukeConfig.get().falloutDelayTicks;
        int reach = (int) Math.ceil(2.4 * radius + drift);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (plume(x, z) > 0 || x * x + z * z < 4 * radius * radius) {
                    columns.add(BlockPos.asLong(center.getX() + x, 0, center.getZ() + z));
                }
            }
        }
        // a random order, so the ash comes down everywhere at once and not row by row
        for (int i = columns.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            long t = columns.getLong(i);
            columns.set(i, columns.getLong(j));
            columns.set(j, t);
        }
        this.perTick = Math.max(1, columns.size() / Math.max(1, NukeConfig.get().falloutTicks));
    }

    /** How thick the fallout lies at an offset from ground zero: 1 in the middle of the plume, 0 outside it. */
    private double plume(double x, double z) {
        double px = x - windX * drift;
        double pz = z - windZ * drift;
        double along = px * windX + pz * windZ;
        double across = -px * windZ + pz * windX;
        double e = Math.sqrt(Math.pow(along / (2.4 * radius), 2) + Math.pow(across / (1.3 * radius), 2));
        return e >= 1 ? 0 : Math.pow(1 - e, 1.2);
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public boolean heavy() {
        return true;
    }

    @Override
    public boolean tick() {
        if (delay-- > 0) return false;
        long start = System.nanoTime();
        int end = Math.min(columns.size(), next + perTick);
        while (next < end && System.nanoTime() - start < BUDGET_NANOS) {
            long packed = columns.getLong(next++);
            column(BlockPos.getX(packed), BlockPos.getZ(packed));
        }
        return next >= columns.size();
    }

    private void column(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int dx = x - center.getX();
        int dz = z - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        BlockState state = level.getBlockState(ground);
        if (distance < 2 * radius && random.nextDouble() < 1.6 * (1 - distance / (2 * radius))) {
            if (state.is(Blocks.WATER)) {
                for (int down = 0; down < 3; down++) {
                    BlockPos pos = ground.below(down);
                    BlockState water = level.getBlockState(pos);
                    if (!water.is(Blocks.WATER) || !water.getFluidState().isSource()) break;
                    level.setBlock(pos, Contamination.WATER_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                return;
            }
            if (state.is(BlockTags.SAND) && distance < 1.3 * radius) {
                level.setBlock(ground, Contamination.TRINITITE.defaultBlockState(), Block.UPDATE_CLIENTS);
                state = Contamination.TRINITITE.defaultBlockState();
            } else if (state.is(BlockTags.SUBSTRATE_OVERWORLD) && !state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                level.setBlock(ground, Contamination.IRRADIATED_EARTH.defaultBlockState(), Block.UPDATE_CLIENTS);
                state = Contamination.IRRADIATED_EARTH.defaultBlockState();
                BlockPos plant = ground.above();
                BlockState above = level.getBlockState(plant);
                if (!above.isAir() && !above.canSurvive(level, plant)) level.destroyBlock(plant, false);
            }
        }
        if (random.nextDouble() >= plume(dx, dz) * 0.9) return;
        BlockPos pos = ground.above();
        BlockState above = level.getBlockState(pos);
        if (above.is(Contamination.FALLOUT)) {
            int layers = above.getValue(SnowLayerBlock.LAYERS);
            if (layers < 3) level.setBlock(pos, above.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
            return;
        }
        if (!above.isAir() && !(above.canBeReplaced() && above.getFluidState().isEmpty())) return;
        if (!state.isFaceSturdy(level, ground, Direction.UP)) return;
        level.setBlock(pos, Contamination.FALLOUT.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
