package io.github.dschonas04.tntarsenal.nuclear.item;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The Geiger counter. Held in either hand it reads the dose rate where the player
 * stands, after shielding, and the player's own dose, and shows both above the
 * hotbar. It ticks the way a real one does: the higher the rate, the more often.
 * Everything runs on the server, so the counter needs nothing on the client.
 */
public final class GeigerCounter {
    private GeigerCounter() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            boolean reading = server.getTickCount() % 10 == 0;
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    if (!holds(player)) continue;
                    float rate = Radiation.rateAt(level, player.blockPosition()) * Radiation.shielding(level, player);
                    tick(level, player, rate);
                    if (reading) show(player, rate);
                }
            }
        });
    }

    private static boolean holds(ServerPlayer player) {
        return player.getMainHandItem().is(NuclearTnt.GEIGER_COUNTER) || player.getOffhandItem().is(NuclearTnt.GEIGER_COUNTER);
    }

    /** Background radiation clicks now and then; a hot zone turns it into a rattle. */
    private static void tick(ServerLevel level, ServerPlayer player, float rate) {
        float chance = Math.min(0.9f, 0.01f + rate * 0.15f);
        if (player.getRandom().nextFloat() >= chance) return;
        float pitch = 1.6f + player.getRandom().nextFloat() * 0.4f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(),
                SoundSource.PLAYERS, 0.35f, pitch);
    }

    private static void show(ServerPlayer player, float rate) {
        float dose = Radiation.dose(player);
        ChatFormatting colour = rate >= 5f ? ChatFormatting.DARK_RED : rate >= 1f ? ChatFormatting.RED
                : rate >= 0.1f ? ChatFormatting.GOLD : rate > 0.001f ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        player.sendOverlayMessage(Component.translatable("item.tnt_arsenal.geiger_counter.reading",
                String.format("%.3f", rate), String.format("%.1f", dose)).withStyle(colour));
    }
}
