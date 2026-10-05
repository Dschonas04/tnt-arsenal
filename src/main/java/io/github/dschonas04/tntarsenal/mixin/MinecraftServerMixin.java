package io.github.dschonas04.tntarsenal.mixin;

import io.github.dschonas04.tntarsenal.TntArsenal;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the detonations that take more than one tick. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void tntArsenal$tick(BooleanSupplier haveTime, CallbackInfo ci) {
        TntArsenal.tickTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void tntArsenal$stop(CallbackInfo ci) {
        TntArsenal.clearTasks();
    }
}
