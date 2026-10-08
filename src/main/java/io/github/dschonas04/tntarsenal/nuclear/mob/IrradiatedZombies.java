package io.github.dschonas04.tntarsenal.nuclear.mob;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Zombies that wander into contaminated land turn: tougher, faster, harder
 * hitting, trailing green motes, and every hit they land adds 25 mSv to the
 * victim's dose. They stay that way when they wander out again.
 */
public final class IrradiatedZombies {
    private IrradiatedZombies() {
    }

    public static final String TAG = "tnt_arsenal.irradiated";
    /** The dose rate in mSv/s from which a zombie turns. */
    private static final float THRESHOLD = 0.2f;
    private static final float DOSE_PER_HIT = 25f;
    private static final DustParticleOptions GLOW = new DustParticleOptions(0x7CFF3A, 1.2f);

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 40 != 0) return;
            for (Zombie zombie : level.getEntities(EntityTypes.ZOMBIE, z -> z.isAlive())) {
                if (zombie.entityTags().contains(TAG)) {
                    level.sendParticles(GLOW, zombie.getX(), zombie.getY() + 1.2, zombie.getZ(), 4, 0.3, 0.5, 0.3, 0);
                } else if (Radiation.rateAt(level, zombie.blockPosition()) >= THRESHOLD) {
                    irradiate(zombie);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (blocked || !(victim instanceof ServerPlayer player)) return;
            if (source.getEntity() instanceof Zombie zombie && zombie.entityTags().contains(TAG)) {
                Radiation.setDose(player, Radiation.dose(player) + DOSE_PER_HIT);
            }
        });
    }

    private static void irradiate(Zombie zombie) {
        zombie.addTag(TAG);
        if (!zombie.hasCustomName()) zombie.setCustomName(Component.translatable("entity.tnt_arsenal.irradiated_zombie"));
        modify(zombie.getAttribute(Attributes.MAX_HEALTH), "irradiated_health", 20, AttributeModifier.Operation.ADD_VALUE);
        modify(zombie.getAttribute(Attributes.MOVEMENT_SPEED), "irradiated_speed", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(zombie.getAttribute(Attributes.ATTACK_DAMAGE), "irradiated_attack", 3, AttributeModifier.Operation.ADD_VALUE);
        zombie.setHealth(zombie.getMaxHealth());
    }

    private static void modify(AttributeInstance attribute, String name, double amount, AttributeModifier.Operation operation) {
        if (attribute != null && !attribute.hasModifier(NuclearTnt.id(name))) {
            attribute.addPermanentModifier(new AttributeModifier(NuclearTnt.id(name), amount, operation));
        }
    }
}
