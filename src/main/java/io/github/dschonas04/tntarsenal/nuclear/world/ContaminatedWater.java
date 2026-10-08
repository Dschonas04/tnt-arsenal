package io.github.dschonas04.tntarsenal.nuclear.world;

import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;

/**
 * Water that fallout has come down on. It behaves like water (it is in the
 * water tag) but does not form new sources, and swimming in it irradiates.
 */
public abstract class ContaminatedWater extends WaterFluid {
    /** mSv a player takes in for every tick spent in it. */
    private static final float DOSE_PER_TICK = 0.25f;

    @Override
    public Fluid getFlowing() {
        return Contamination.FLOWING_WATER;
    }

    @Override
    public Fluid getSource() {
        return Contamination.WATER;
    }

    @Override
    public Item getBucket() {
        return Contamination.WATER_BUCKET;
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == Contamination.WATER || fluid == Contamination.FLOWING_WATER;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return Contamination.WATER_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        super.entityInside(level, pos, entity, effects);
        if (entity instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
            Radiation.setDose(player, Radiation.dose(player) + DOSE_PER_TICK);
        }
    }

    public static class Source extends ContaminatedWater {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends ContaminatedWater {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
