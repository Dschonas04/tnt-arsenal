package io.github.dschonas04.tntarsenal.nuclear.world;

import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** What grass becomes near a detonation: grey, dead earth. When the radiation is gone it turns back into dirt, and grass can grow again. */
public class IrradiatedEarthBlock extends Block {
    public IrradiatedEarthBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(16) == 0 && Radiation.rateAt(level, pos) < 0.05f) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), UPDATE_ALL);
        }
    }
}
