package io.github.dschonas04.tntarsenal.nuclear.mixin;

import io.github.dschonas04.tntarsenal.nuclear.entity.PrimedNuke;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When a bomb's fuse runs out, its own detonation runs instead of the vanilla explosion. */
@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void tntArsenalNuclear$detonate(CallbackInfo ci) {
        if ((Object) this instanceof PrimedNuke nuke && nuke.level() instanceof ServerLevel level) {
            nuke.detonate(level);
            ci.cancel();
        }
    }
}
