package io.github.dschonas04.tntarsenal.nuclear.client;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import io.github.dschonas04.tntarsenal.nuclear.network.NukeDetonatedPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a player sees of a detonation: the flash (blinding if you look at
 * it, a glare if you do not), the mushroom cloud and the shaking ground, which
 * arrives with the shock wave — later, the further away you are.
 */
final class NukeEffects {
    private NukeEffects() {
    }

    private static final List<MushroomCloud> CLOUDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static float flash;
    private static int shakeIn = -1;
    private static float shake;
    private static double shakeX;
    private static double shakeY;

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NukeDetonatedPayload.TYPE, (payload, context) -> detonated(payload));
        ClientTickEvents.END_CLIENT_TICK.register(NukeEffects::tick);
        HudElementRegistry.addLast(NuclearTnt.id("flash"), NukeEffects::drawFlash);
    }

    private static void detonated(NukeDetonatedPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Vec3 blast = Vec3.atCenterOf(payload.pos());
        CLOUDS.add(new MushroomCloud(blast, payload.radius()));
        Vec3 to = blast.subtract(player.getEyePosition());
        double dist = to.length();
        double facing = dist < 1 ? 1 : player.getViewVector(1.0f).dot(to.normalize());
        double near = Mth.clamp(1 - dist / (payload.radius() * 20.0), 0, 1);
        // looking at it: blinding; looking away: a glare from behind
        flash = Math.max(flash, (float) (near * (facing > 0 ? 0.35 + 0.65 * facing : 0.25)));
        shakeIn = (int) (dist / (343.0 / 20.0));
        shake = (float) Mth.clamp(1.2 - dist / (payload.radius() * 12.0), 0, 1.2);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            CLOUDS.clear();
            flash = 0;
            shake = 0;
            return;
        }
        if (!mc.isPaused()) CLOUDS.removeIf(cloud -> !cloud.tick(mc.level));
        flash *= 0.94f;
        if (flash < 0.01f) flash = 0;
        LocalPlayer player = mc.player;
        if (shakeIn > 0) {
            shakeIn--;
        } else if (shake > 0.01f && player != null) {
            // jitter and undo the last jitter, so the view shakes but does not drift
            double x = (RANDOM.nextDouble() - 0.5) * shake * 30;
            double y = (RANDOM.nextDouble() - 0.5) * shake * 30;
            player.turn(x - shakeX, y - shakeY);
            shakeX = x;
            shakeY = y;
            shake *= 0.96f;
        } else if (shakeX != 0 || shakeY != 0) {
            if (player != null) player.turn(-shakeX, -shakeY);
            shakeX = 0;
            shakeY = 0;
            shake = 0;
        }
    }

    private static void drawFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (flash <= 0) return;
        int alpha = (int) (Mth.clamp(flash, 0, 1) * 255);
        int color = (alpha << 24) | 0xFFF8F0;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
    }
}
