package io.github.dschonas04.tntarsenal.nuclear.block;

import com.mojang.serialization.MapCodec;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import io.github.dschonas04.tntarsenal.nuclear.entity.PrimedNuke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bomb block. It lights like TNT — redstone, flint and steel, fire charges,
 * burning arrows, explosions — and the detonator item lights it from afar. The
 * lit bomb is a {@link PrimedNuke}.
 */
public class NukeBlock extends TntBlock {
    private final Tier tier;
    private final MapCodec<TntBlock> codec;

    @SuppressWarnings("unchecked")
    public NukeBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = (MapCodec<TntBlock>) (MapCodec<?>) simpleCodec(p -> new NukeBlock(tier, p));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public MapCodec<TntBlock> codec() {
        return codec;
    }

    /** Replaces the block with a lit bomb. Returns false on the client. */
    public boolean ignite(Level level, BlockPos pos, LivingEntity igniter, int fuse) {
        if (!(level instanceof ServerLevel)) return false;
        PrimedNuke nuke = PrimedNuke.create(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this, igniter, fuse);
        level.addFreshEntity(nuke);
        level.playSound(null, nuke.getX(), nuke.getY(), nuke.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 0.6f);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        level.removeBlock(pos, false);
        return true;
    }

    private int fuse() {
        return NukeConfig.get().fuse(tier);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(pos)) ignite(level, pos, null, fuse());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) ignite(level, pos, null, fuse());
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        // Already gone from the world; light a fresh one with a short fuse, as TNT does.
        Entity source = explosion.getIndirectSourceEntity();
        PrimedNuke nuke = PrimedNuke.create(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this,
                source instanceof LivingEntity living ? living : null, PrimedTnt.getRandomShortFuse(fuse(), level.getRandom()));
        level.addFreshEntity(nuke);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        ignite(level, pos, player, fuse());
        if (!player.isCreative()) {
            if (stack.is(Items.FLINT_AND_STEEL)) stack.hurtAndBreak(1, player, hand);
            else stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel && projectile.isOnFire()) {
            Entity owner = projectile.getOwner();
            ignite(level, hit.getBlockPos(), owner instanceof LivingEntity living ? living : null, fuse());
        }
    }

}
