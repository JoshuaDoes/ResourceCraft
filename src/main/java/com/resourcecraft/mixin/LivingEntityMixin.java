package com.resourcecraft.mixin;

import com.resourcecraft.ResourceCraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.Entity;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements com.resourcecraft.LivingEntityExt {
    @Unique
    private final java.util.List<Integer> resourceCraft$stuckArrowDurabilities = new java.util.ArrayList<>();

    @Unique
    @Override
    public void resourceCraft$addStuckArrow(int durability) {
        this.resourceCraft$stuckArrowDurabilities.add(durability);
    }

    @Unique
    @Override
    public java.util.List<Integer> resourceCraft$getStuckArrows() {
        return this.resourceCraft$stuckArrowDurabilities;
    }

    @Unique
    @Override
    public void resourceCraft$clearStuckArrows() {
        this.resourceCraft$stuckArrowDurabilities.clear();
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDie(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        // --- Spawning Logic Logging ---
        java.util.Set<String> tags = ((EntityAccessor) entity).getTags();
        String habitat = "Unknown";
        String category = "Unknown";
        String isNight = "Unknown";
        boolean found = false;

        for (String tag : tags) {
            if (tag.startsWith("ResourceCraft_Habitat:")) {
                habitat = tag.substring(22);
                found = true;
            } else if (tag.startsWith("ResourceCraft_Category:")) {
                category = tag.substring(23);
                found = true;
            } else if (tag.startsWith("ResourceCraft_IsNight:")) {
                isNight = tag.substring(22);
                found = true;
            }
        }

        if (found) {
            ResourceCraft.LOGGER.info("ResourceCraft | [DEATH] {} at {} | Habitat: {} | Category: {} | Night: {} | Cause: {}", 
                entity.getType().getDescription().getString(), entity.blockPosition(), habitat, category, isNight, damageSource.getMsgId());
        }

        // --- Arrow Physics Logic ---
        // In 26.1.2, LivingEntity keeps track of stuck arrows via an internal counter, but doesn't store the entities.
        // However, we can simulate the "falling" by spawning new arrows if the counter is > 0.
        // Wait, vanilla stuck arrows are just a visual effect (counter).
        // If we want real reclaimable arrows, we'd need to have tracked them.
        // But the user said "mobs shot with an arrow should drop those same arrows with their expected remaining durabilities".
        // This implies we need to track them.
        
        // Spawn actual arrows that were stuck with their tracked durabilities
        if (!entity.level().isClientSide() && !resourceCraft$stuckArrowDurabilities.isEmpty()) {
            for (int durability : resourceCraft$stuckArrowDurabilities) {
                // Spawn with random offset and upward velocity
                double rx = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                double ry = entity.getY() + entity.getBbHeight() * 0.5 + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                double rz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                
                net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow = net.minecraft.world.entity.EntityType.ARROW.create((net.minecraft.server.level.ServerLevel)entity.level(), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
                if (arrow == null) continue;
                arrow.setPos(rx, ry, rz);
                arrow.setDeltaMovement((entity.getRandom().nextDouble() - 0.5) * 0.2, 0.3, (entity.getRandom().nextDouble() - 0.5) * 0.2);
                arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED;
                ((com.resourcecraft.ArrowExt)arrow).setResourceCraftDurability(durability);
                entity.level().addFreshEntity(arrow);
            }
            resourceCraft$clearStuckArrows();
            entity.setArrowCount(0); // Clear visual stuck arrows
        } else if (entity.getArrowCount() > 0 && !entity.level().isClientSide()) {
            // Fallback for non-ResourceCraft arrows if any
            int stuckArrows = entity.getArrowCount();
            for (int i = 0; i < stuckArrows; i++) {
                double rx = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                double ry = entity.getY() + entity.getBbHeight() * 0.5 + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                double rz = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * 0.5;
                
                net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow = net.minecraft.world.entity.EntityType.ARROW.create((net.minecraft.server.level.ServerLevel)entity.level(), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
                if (arrow == null) continue;
                arrow.setPos(rx, ry, rz);
                arrow.setDeltaMovement((entity.getRandom().nextDouble() - 0.5) * 0.2, 0.3, (entity.getRandom().nextDouble() - 0.5) * 0.2);
                arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED;
                ((com.resourcecraft.ArrowExt)arrow).setResourceCraftDurability(6); // Full durability fallback
                entity.level().addFreshEntity(arrow);
            }
            entity.setArrowCount(0);
        }
    }
}
