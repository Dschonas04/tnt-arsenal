package io.github.dschonas04.tntarsenal.nuclear.client;

import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The mushroom cloud, drawn with particles that stay visible at any distance.
 * Its size follows the bomb's radius; it lives for sixty seconds:
 *
 * <ol>
 * <li>fireball — a glowing ball that swells for half a second;</li>
 * <li>stem — a column that climbs for six seconds, particles streaming upwards;</li>
 * <li>cap — a torus on top of the stem that grows and rolls outwards: over the top,
 *     down the outside, in underneath and up through the middle;</li>
 * <li>condensation ring — a white ring halfway up that spreads out;</li>
 * <li>fade — after thirty seconds fewer and fewer particles, gone at sixty.</li>
 * </ol>
 */
final class MushroomCloud {
    private static final int LIFE = 1200;
    private static final DustParticleOptions GLOW = new DustParticleOptions(0xFFB040, 4.0f);
    private static final DustParticleOptions EMBER = new DustParticleOptions(0xFF5A1A, 4.0f);
    private static final DustParticleOptions ASH = new DustParticleOptions(0x8A8078, 4.0f);
    private static final DustParticleOptions DARK = new DustParticleOptions(0x5A524C, 4.0f);

    private final Vec3 ground;
    private final double height;
    private final double cap;
    private final RandomSource random = RandomSource.create();
    private int age;

    MushroomCloud(Vec3 ground, int radius) {
        this.ground = ground;
        this.height = radius * 2.5;
        this.cap = radius * 0.9;
    }

    /** One client tick. Returns false once the cloud is gone. */
    boolean tick(ClientLevel level) {
        age++;
        int budget = NukeConfig.get().cloudParticlesPerTick;
        double fade = age < 600 ? 1.0 : Math.max(0, 1.0 - (age - 600) / 600.0);
        int count = (int) (budget * fade);
        double stemTop = height * Math.min(1.0, age / 120.0);

        if (age < 30) fireball(level, budget / 2);
        int stem = count / 4;
        for (int i = 0; i < stem; i++) {
            double h = random.nextDouble() * stemTop;
            double r = cap * 0.18 * (0.6 + 0.4 * h / height) * Math.sqrt(random.nextDouble());
            double a = random.nextDouble() * Math.PI * 2;
            ParticleOptions dust = age < 80 && h < stemTop * 0.5 ? EMBER : (random.nextBoolean() ? ASH : DARK);
            add(level, dust, ground.x + Math.cos(a) * r, ground.y + h, ground.z + Math.sin(a) * r, 0, 0.25, 0);
        }
        if (age > 30) capTorus(level, count - stem, stemTop);
        if (age > 40 && age < 260) ring(level, count / 6);
        return age < LIFE;
    }

    private void fireball(ClientLevel level, int count) {
        double r = cap * 0.6 * Math.min(1.0, age / 10.0);
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            double d = r * Math.cbrt(random.nextDouble());
            double x = ground.x + dir.x * d;
            double y = ground.y + r * 0.6 + dir.y * d;
            double z = ground.z + dir.z * d;
            add(level, random.nextInt(3) == 0 ? ParticleTypes.FLAME : random.nextBoolean() ? GLOW : EMBER, x, y, z, 0, 0.05, 0);
        }
    }

    /** Particles on a torus around the top of the stem, moving the way the cap rolls. */
    private void capTorus(ClientLevel level, int count, double stemTop) {
        double grow = Math.min(1.0, (age - 30) / 200.0);
        double major = cap * (0.4 + 0.6 * grow);
        double minor = cap * 0.35 * (0.5 + 0.5 * grow);
        double cy = ground.y + stemTop;
        for (int i = 0; i < count; i++) {
            double around = random.nextDouble() * Math.PI * 2;   // around the stem
            double roll = random.nextDouble() * Math.PI * 2;     // around the tube
            double r = major + Math.cos(roll) * minor;
            double x = ground.x + Math.cos(around) * r;
            double y = cy + Math.sin(roll) * minor;
            double z = ground.z + Math.sin(around) * r;
            // rolling outwards: tangent to the tube, top moving out, outside moving down
            double speed = 0.08 + 0.12 * (1 - grow);
            double radial = -Math.sin(roll) * speed;
            double up = Math.cos(roll) * speed;
            double vx = Math.cos(around) * -radial;
            double vz = Math.sin(around) * -radial;
            boolean hot = age < 200 && Math.sin(roll) < 0.3 && random.nextInt(3) == 0;
            add(level, hot ? GLOW : (random.nextInt(3) == 0 ? DARK : ASH), x, y, z, vx, up + 0.02, vz);
        }
    }

    private void ring(ClientLevel level, int count) {
        double spread = cap * (0.5 + 1.3 * (age - 40) / 220.0);
        double y = ground.y + height * 0.5;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = spread * (0.92 + random.nextDouble() * 0.08);
            add(level, ParticleTypes.CLOUD, ground.x + Math.cos(a) * r, y + random.nextGaussian() * 0.6, ground.z + Math.sin(a) * r,
                    Math.cos(a) * 0.15, 0, Math.sin(a) * 0.15);
        }
    }

    private static void add(ClientLevel level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(type, true, x, y, z, vx, vy, vz);
    }
}
