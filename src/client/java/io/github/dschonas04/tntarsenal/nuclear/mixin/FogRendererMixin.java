package io.github.dschonas04.tntarsenal.nuclear.mixin;

import io.github.dschonas04.tntarsenal.nuclear.client.GreenFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Turns the fog green and draws it in when the camera is in contaminated land. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void tntArsenal$greenFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkness,
            ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        GreenFog.apply(cir.getReturnValue(), camera, level);
    }
}
