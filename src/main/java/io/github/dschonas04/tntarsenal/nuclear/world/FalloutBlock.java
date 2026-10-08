package io.github.dschonas04.tntarsenal.nuclear.world;

import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fallout: a faintly glowing layer of radioactive ash. It does not melt like
 * snow; once the land around it has cooled down it slowly blows away.
 */
public class FalloutBlock extends SnowLayerBlock {
    public FalloutBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0 || Radiation.rateAt(level, pos) > 0.05f) return;
        int layers = state.getValue(LAYERS);
        level.setBlock(pos, layers > 1 ? state.setValue(LAYERS, layers - 1) : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(12) != 0) return;
        level.addParticle(new DustParticleOptions(0x8CFF5A, 0.6f), pos.getX() + random.nextDouble(),
                pos.getY() + state.getValue(LAYERS) / 8.0 + 0.05, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
    }
}
