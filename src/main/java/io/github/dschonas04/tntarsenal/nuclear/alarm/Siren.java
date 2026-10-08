package io.github.dschonas04.tntarsenal.nuclear.alarm;

import io.github.dschonas04.tntarsenal.nuclear.explosion.Jobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The air-raid siren: a tone that rises and falls over four seconds, made of
 * vanilla flute notes so it needs no sound file. Siren blocks wail while powered;
 * every lit bomb sounds it over the land for as long as its fuse burns.
 */
public final class Siren {
    private Siren() {
    }

    /** One step of the wail, to be called every few ticks. Volume 16 carries about 256 blocks. */
    public static void wail(ServerLevel level, Vec3 at, float volume) {
        double phase = (level.getGameTime() % 80) / 80.0;
        double up = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
        float pitch = (float) (0.55 + 1.35 * up);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.BLOCKS, volume, pitch);
    }

    /** Wails over a lit bomb until its fuse has burnt down. */
    public static void warn(ServerLevel level, Vec3 at, int ticks) {
        Jobs.add(new Jobs.Job() {
            private int age;

            @Override
            public ServerLevel level() {
                return level;
            }

            @Override
            public boolean tick() {
                if (age % 4 == 0) wail(level, at, 16f);
                return ++age >= ticks;
            }
        });
    }
}
