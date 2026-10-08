package io.github.dschonas04.tntarsenal.nuclear.client;

import io.github.dschonas04.tntarsenal.nuclear.config.NukeConfig;
import io.github.dschonas04.tntarsenal.nuclear.radiation.ChunkRadiation;
import io.github.dschonas04.tntarsenal.nuclear.radiation.Radiation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;

/**
 * The green haze over contaminated land. Its strength follows the dose rate
 * of the chunk the camera is in, synced from the server, and eases in and out
 * so that crossing a chunk border does not make the fog jump.
 */
public final class GreenFog {
    private GreenFog() {
    }

    private static final float RED = 0.42f;
    private static final float GREEN = 0.62f;
    private static final float BLUE = 0.22f;

    private static float strength;
    private static long lastFrame;

    public static void apply(FogData fog, Camera camera, ClientLevel level) {
        float target = 0f;
        if (camera.getFluidInCamera() == FogType.NONE || camera.getFluidInCamera() == FogType.ATMOSPHERIC) {
            target = Mth.clamp((float) Math.sqrt(rate(level, camera.blockPosition()) / 3.0), 0f, 1f) * NukeConfig.get().fogStrength;
        }
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        strength += (target - strength) * Math.min(1f, seconds * 0.8f);
        if (strength < 0.005f) return;
        float s = Mth.clamp(strength, 0f, 1f);
        fog.environmentalStart = Mth.lerp(s, fog.environmentalStart, 0f);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, Mth.lerp(s, fog.environmentalEnd, 28f));
        fog.renderDistanceStart = Mth.lerp(s, fog.renderDistanceStart, 0f);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, Mth.lerp(s, fog.renderDistanceEnd, 40f));
        fog.skyEnd = Math.min(fog.skyEnd, Mth.lerp(s, fog.skyEnd, 48f));
        fog.cloudEnd = Math.min(fog.cloudEnd, Mth.lerp(s, fog.cloudEnd, 48f));
        float mix = s * 0.75f;
        fog.color.set(Mth.lerp(mix, fog.color.x, RED), Mth.lerp(mix, fog.color.y, GREEN), Mth.lerp(mix, fog.color.z, BLUE), fog.color.w);
    }

    private static float rate(ClientLevel level, BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return 0f;
        ChunkRadiation radiation = ((AttachmentTarget) level.getChunkAt(pos)).getAttached(Radiation.CHUNK);
        return radiation == null ? 0f : radiation.at(level.getGameTime(), NukeConfig.get().halfLifeTicks);
    }
}
