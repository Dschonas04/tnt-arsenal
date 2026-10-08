package io.github.dschonas04.tntarsenal.nuclear.item;

import io.github.dschonas04.tntarsenal.nuclear.block.NukeBlock;
import io.github.dschonas04.tntarsenal.nuclear.explosion.Jobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;

/**
 * The timer fuse. Sneak and right-click to set the time (10 s, 30 s, 1, 2 or 5
 * minutes), then right-click a bomb to fix it there: the countdown runs above
 * the hotbar of everyone within 64 blocks, beeping faster at the end, and lights
 * the bomb at zero. The fuse is used up.
 */
public class TimerItem extends Item {
    private static final int[] STEPS = {10, 30, 60, 120, 300};

    public TimerItem(Properties properties) {
        super(properties);
    }

    private static int seconds(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getIntOr("seconds", STEPS[1]);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel) {
            int now = seconds(stack), next = STEPS[0];
            for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == now) next = STEPS[(i + 1) % STEPS.length];
            CompoundTag tag = new CompoundTag();
            tag.putInt("seconds", next);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
            player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.timer.set", next));
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof NukeBlock)) return super.useOn(context);
        if (level instanceof ServerLevel server) {
            ItemStack stack = context.getItemInHand();
            Player player = context.getPlayer();
            countdown(server, pos, seconds(stack) * 20, player);
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.timer.started", seconds(stack)));
                if (!player.isCreative()) stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void countdown(ServerLevel level, BlockPos pos, int ticks, Player igniter) {
        Jobs.add(new Jobs.Job() {
            private int left = ticks;

            @Override
            public ServerLevel level() {
                return level;
            }

            @Override
            public boolean tick() {
                if (!(level.getBlockState(pos).getBlock() instanceof NukeBlock bomb)) {
                    if (igniter instanceof ServerPlayer player) player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.timer.gone"));
                    return true;
                }
                if (left <= 0) {
                    bomb.ignite(level, pos, igniter, 1);
                    return true;
                }
                int every = left > 200 ? 20 : left > 60 ? 10 : 4;
                if (left % every == 0) {
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.5f, left > 200 ? 1.2f : 1.9f);
                    level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.1, 0.05, 0.1, 0);
                }
                if (left % 20 == 0) {
                    Component text = Component.translatable("item.tnt_arsenal.timer.countdown", (left + 19) / 20).withStyle(ChatFormatting.RED);
                    for (ServerPlayer player : level.players()) {
                        if (player.distanceToSqr(Vec3.atCenterOf(pos)) < 64 * 64) player.sendOverlayMessage(text);
                    }
                }
                left--;
                return false;
            }
        });
    }
}
