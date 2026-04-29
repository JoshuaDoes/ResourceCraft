package com.resourcecraft.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow private int age;
    @Shadow private int pickupDelay;

    /**
     * Disable the despawn timeout for item entities.
     * We intercept the discard call in tick() and prevent it if it's triggered by age.
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private void onDiscard(ItemEntity instance) {
        // In vanilla ItemEntity#tick, discard() is called when age >= 6000.
        // We only allow discard() if it's NOT a timeout (though in tick() it almost always is).
        // By not calling instance.discard() here, we prevent the timeout.
        // Allow discard if it's a "fake item" created by /give (which sets pickup delay to 32767)
        if (this.age < 6000 || this.pickupDelay == 32767) {
            instance.discard();
        }
    }
}
