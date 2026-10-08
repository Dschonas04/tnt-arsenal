package io.github.dschonas04.tntarsenal.nuclear.client;

import io.github.dschonas04.tntarsenal.nuclear.NuclearTnt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import io.github.dschonas04.tntarsenal.nuclear.world.Contamination;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

/** Client side: the lit bomb's renderer (flashing like TNT), the effects of a detonation and contaminated water. */
public final class NuclearTntClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NuclearTnt.PRIMED_NUKE, TntRenderer::new);
        NukeEffects.register();
        // contaminated water: the water textures, tinted a sickly green
        FluidRenderingRegistry.register(Contamination.WATER, Contamination.FLOWING_WATER, new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                state -> 0xFF6FB83A));
    }
}
