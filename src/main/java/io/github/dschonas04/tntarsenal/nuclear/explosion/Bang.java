package io.github.dschonas04.tntarsenal.nuclear.explosion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The bang, heard late: sound travels 343 blocks a second, so every player gets
 * it when it reaches them, quieter the further away they are. Played just next
 * to the player, so it is heard at any distance.
 */
public final class Bang implements Jobs.Job {
    private static final double SPEED = 343.0 / 20.0; // blocks per tick
    private static final double AUDIBLE = 1200;

    private final ServerLevel level;
    private final Vec3 origin;
    private final List<ServerPlayer> waiting;
    private final List<Integer> due = new ArrayList<>();
    private int age;

    public Bang(ServerLevel level, Vec3 origin) {
        this.level = level;
        this.origin = origin;
        this.waiting = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            double dist = Math.sqrt(player.distanceToSqr(origin));
            if (dist > AUDIBLE) continue;
            waiting.add(player);
            due.add((int) (dist / SPEED));
        }
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public boolean tick() {
        for (int i = waiting.size() - 1; i >= 0; i--) {
            if (due.get(i) > age) continue;
            ServerPlayer player = waiting.remove(i);
            due.remove(i);
            double dist = Math.sqrt(player.distanceToSqr(origin));
            float volume = (float) Math.max(0.15, 1.0 - dist / AUDIBLE) * 4.0f;
            Vec3 toward = origin.subtract(player.position());
            Vec3 at = player.getEyePosition().add(toward.lengthSqr() < 1 ? Vec3.ZERO : toward.normalize().scale(4));
            long seed = level.getRandom().nextLong();
            player.connection.send(new ClientboundSoundPacket(SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                    at.x, at.y, at.z, volume, 0.35f, seed));
            player.connection.send(new ClientboundSoundPacket(SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                    at.x, at.y, at.z, volume, 0.5f, seed + 1));
        }
        age++;
        return waiting.isEmpty();
    }
}
