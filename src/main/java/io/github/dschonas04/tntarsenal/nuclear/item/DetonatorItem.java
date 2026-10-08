package io.github.dschonas04.tntarsenal.nuclear.item;

import io.github.dschonas04.tntarsenal.nuclear.block.NukeBlock;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The remote detonator. Right-click a bomb to bind it; right-click anywhere else
 * to light it, wherever you are — as long as its chunk is loaded and you are in
 * the same dimension.
 */
public class DetonatorItem extends Item {
    public DetonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof NukeBlock)) return super.useOn(context);
        Player player = context.getPlayer();
        if (level instanceof ServerLevel && player != null) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos.asLong());
            tag.putString("dimension", level.dimension().identifier().toString());
            CustomData.set(DataComponents.CUSTOM_DATA, context.getItemInHand(), tag);
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.bound", pos.getX(), pos.getY(), pos.getZ()));
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!(level instanceof ServerLevel)) return InteractionResult.SUCCESS;
        if (data == null || data.isEmpty() || !data.copyTag().contains("pos")) {
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.unbound"));
            return InteractionResult.FAIL;
        }
        CompoundTag tag = data.copyTag();
        BlockPos pos = BlockPos.of(tag.getLongOr("pos", 0L));
        if (!tag.getStringOr("dimension", "").equals(level.dimension().identifier().toString())) {
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.other_dimension"));
            return InteractionResult.FAIL;
        }
        if (!level.isLoaded(pos)) {
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.not_loaded"));
            return InteractionResult.FAIL;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof NukeBlock bomb)) {
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.gone"));
            stack.remove(DataComponents.CUSTOM_DATA);
            return InteractionResult.FAIL;
        }
        bomb.ignite(level, pos, player, NukeConfig.get().fuse(bomb.tier()));
        stack.remove(DataComponents.CUSTOM_DATA);
        player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.detonator.fired"));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
