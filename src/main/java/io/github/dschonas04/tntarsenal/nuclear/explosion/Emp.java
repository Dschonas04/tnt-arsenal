package io.github.dschonas04.tntarsenal.nuclear.explosion;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The electromagnetic pulse of a detonation, reaching twice the blast radius:
 * detonators, timer fuses and Geiger counters in a player's inventory go dead for
 * a minute, eyes are dazzled for a moment, and lit redstone lamps and copper bulbs
 * near the ground go out. The lamps are switched off over many ticks, a few
 * thousand blocks at a time.
 */
public final class Emp {
    private Emp() {
    }

    private static final int DEAD_TICKS = 1200;
    private static final int PER_TICK = 6000;
    private static final int HEIGHT = 12;

    public static void pulse(ServerLevel level, BlockPos center, int radius) {
        int reach = radius * 2;
        Component dead = Component.translatable("tnt_arsenal.emp").withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(center)) > (double) reach * reach) continue;
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(NuclearTnt.DETONATOR) || stack.is(NuclearTnt.GEIGER_COUNTER) || stack.is(NuclearTnt.TIMER)) {
                    player.getCooldowns().addCooldown(stack, DEAD_TICKS);
                }
            }
            player.sendOverlayMessage(dead);
        }
        int lampReach = Math.min(reach, 96);
        Jobs.add(new Jobs.Job() {
            private int index;
            private final int side = lampReach * 2 + 1;
            private final int total = side * side * (HEIGHT * 2 + 1);
            private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            @Override
            public ServerLevel level() {
                return level;
            }

            @Override
            public boolean tick() {
                int end = Math.min(total, index + PER_TICK);
                for (; index < end; index++) {
                    int x = index % side - lampReach;
                    int z = (index / side) % side - lampReach;
                    int y = index / (side * side) - HEIGHT;
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.isLoaded(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(Blocks.REDSTONE_LAMP) || state.getBlock() instanceof CopperBulbBlock)
                            && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 2);
                    }
                }
                return index >= total;
            }
        });
    }

    /** Whether an item is dead from a pulse right now. */
    public static boolean dead(ServerPlayer player, ItemStack stack) {
        return player.getCooldowns().isOnCooldown(stack);
    }
}
