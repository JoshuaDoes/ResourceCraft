package com.resourcecraft.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "onHit", at = @At("HEAD"), require = 0)
    private void onHit(HitResult hitResult, CallbackInfo ci) {
        if ((Object)this instanceof AbstractArrow arrow) {
            com.resourcecraft.ArrowExt arrowExt = (com.resourcecraft.ArrowExt) arrow;
            int currentDurability = arrowExt.getResourceCraftDurability();
            
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                currentDurability -= 1;
                if (com.resourcecraft.ResourceCraftConfig.debugLogs) {
                    com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Arrow hit block (via Projectile)! Durability: {}", currentDurability);
                }
            } else if (hitResult.getType() == HitResult.Type.ENTITY) {
                currentDurability -= 2;
                if (com.resourcecraft.ResourceCraftConfig.debugLogs) {
                    com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Arrow hit entity (via Projectile)! Durability: {}", currentDurability);
                }
                
                EntityHitResult entityHit = (EntityHitResult) hitResult;
                if (entityHit.getEntity() instanceof com.resourcecraft.LivingEntityExt living) {
                    if (currentDurability > 0) {
                        living.resourceCraft$addStuckArrow(currentDurability);
                    }
                }
            }
            
            arrowExt.setResourceCraftDurability(currentDurability);
            
            if (currentDurability <= 0) {
                arrow.discard();
            }
        }
    }
}
