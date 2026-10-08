package io.github.dschonas04.tntarsenal.nuclear.radiation;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.world.level.block.Block;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /nuclear radiation <pos>} shows the dose rate of a chunk,
 * {@code /nuclear dose <player> [set <mSv>]} shows or sets a player's dose.
 * {@code /nuclear clear <radius>} removes all radiation around the caller.
 * {@code /nuclear count <block> <radius>} counts a block around the caller, 40 blocks up and down.
 * {@code /nuclear config [key value]} lists or changes a setting and saves it.
 * For operators.
 */
public final class RadiationCommand {
    private RadiationCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("nuclear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("count").then(Commands.argument("block", BlockStateArgument.block(registry))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(c -> {
                                    ServerLevel level = c.getSource().getLevel();
                                    Block block = BlockStateArgument.getBlock(c, "block").getState().getBlock();
                                    int radius = IntegerArgumentType.getInteger(c, "radius");
                                    BlockPos center = BlockPos.containing(c.getSource().getPosition());
                                    int count = 0;
                                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                                    for (int x = -radius; x <= radius; x++) {
                                        for (int z = -radius; z <= radius; z++) {
                                            if (!level.hasChunk((center.getX() + x) >> 4, (center.getZ() + z) >> 4)) continue;
                                            for (int y = -40; y <= 40; y++) {
                                                if (level.getBlockState(pos.set(center.getX() + x, center.getY() + y, center.getZ() + z)).is(block)) count++;
                                            }
                                        }
                                    }
                                    int found = count;
                                    c.getSource().sendSuccess(() -> Component.literal(found + " x " + block.getName().getString()), false);
                                    return found;
                                }))))
                        .then(Commands.literal("config").executes(c -> {
                            NukeConfig.values().forEach((k, v) -> c.getSource().sendSuccess(() -> Component.literal(k + " = " + v), false));
                            return 1;
                        }).then(Commands.argument("key", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(NukeConfig.values().keySet(), b))
                                .then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                                    String key = StringArgumentType.getString(c, "key");
                                    String value = StringArgumentType.getString(c, "value");
                                    String error = NukeConfig.set(key, value);
                                    if (error != null) {
                                        c.getSource().sendFailure(Component.literal(key + ": " + error));
                                        return 0;
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal(key + " = " + NukeConfig.values().get(key)), true);
                                    return 1;
                                }))))
                        .then(Commands.literal("clear").then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            int radius = IntegerArgumentType.getInteger(c, "radius");
                            int chunks = Radiation.clear(level, BlockPos.containing(c.getSource().getPosition()), radius);
                            c.getSource().sendSuccess(() -> Component.literal("Radiation cleared in " + chunks + " chunks"), true);
                            return chunks;
                        })))
                        .then(Commands.literal("radiation").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            float rate = Radiation.rateAt(level, pos);
                            c.getSource().sendSuccess(() -> Component.literal(String.format("%.3f mSv/s at %d %d %d", rate, pos.getX(), pos.getY(), pos.getZ())), false);
                            return (int) (rate * 1000);
                        })))
                        .then(Commands.literal("dose").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer player = EntityArgument.getPlayer(c, "player");
                            float dose = Radiation.dose(player);
                            c.getSource().sendSuccess(() -> Component.literal(String.format("%s: %.1f mSv", player.getScoreboardName(), dose)), false);
                            return (int) dose;
                        }).then(Commands.literal("set").then(Commands.argument("mSv", FloatArgumentType.floatArg(0)).executes(c -> {
                            ServerPlayer player = EntityArgument.getPlayer(c, "player");
                            float dose = FloatArgumentType.getFloat(c, "mSv");
                            Radiation.setDose(player, dose);
                            c.getSource().sendSuccess(() -> Component.literal(String.format("%s: %.1f mSv", player.getScoreboardName(), dose)), true);
                            return (int) dose;
                        })))))));
    }
}
