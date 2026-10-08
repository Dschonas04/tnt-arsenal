package io.github.dschonas04.tntarsenal.nuclear;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements the game cannot see on its own (dose, a detonation). They
 * carry an impossible criterion named "done" and are granted from here; the rest
 * in data/tnt_arsenal/advancement/nuclear/ trigger by themselves.
 */
public final class Achievements {
    private Achievements() {
    }

    public static void award(ServerPlayer player, String name) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(NuclearTnt.id("nuclear/" + name));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }
}
