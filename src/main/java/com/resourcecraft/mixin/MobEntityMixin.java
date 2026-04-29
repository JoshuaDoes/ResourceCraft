package com.resourcecraft.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobEntityMixin {

    /**
     * Ensure all mobs are persistent and stay active.
     */
    @Inject(method = "checkDespawn", at = @At("HEAD"))
    private void onCheckDespawn(CallbackInfo ci) {
        // Resource Persistence Law: All mobs are permanent once spawned.
        // We set persistence instead of cancelling to allow noActionTime to reset,
        // which keeps idle AI goals (like wandering) active.
        ((Mob)(Object)this).setPersistenceRequired();
    }

    /**
     * Prevents mobs from catching fire in the sun.
     * This ensures that naturally spawned mobs remain in the world until killed by the player.
     */
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void onIsSunBurnTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
