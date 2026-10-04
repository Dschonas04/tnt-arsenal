package io.github.dschonas04.tntarsenal.mixin;

import io.github.dschonas04.tntarsenal.ArsenalTntBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the fuse of a primed TNT runs out, check whose block it carries. Ours
 * detonate their own way; everything else explodes as before.
 */
@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void tntArsenal$detonate(CallbackInfo ci) {
        PrimedTnt self = (PrimedTnt) (Object) this;
        if (self.getBlockState().getBlock() instanceof ArsenalTntBlock block
                && self.level() instanceof ServerLevel level) {
            block.kind().detonate(level, self);
            ci.cancel();
        }
    }
}
