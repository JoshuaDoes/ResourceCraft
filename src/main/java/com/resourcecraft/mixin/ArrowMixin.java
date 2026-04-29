package com.resourcecraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.nbt.CompoundTag;

@Mixin(AbstractArrow.class)
public abstract class ArrowMixin implements com.resourcecraft.ArrowExt {

    @Shadow public AbstractArrow.Pickup pickup;

    @Unique
    private int resourceCraft$durability = 6;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"), require = 0)
    private void onInit(CallbackInfo ci) {
        this.pickup = AbstractArrow.Pickup.ALLOWED;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(CallbackInfo ci) {
        if (this.pickup != AbstractArrow.Pickup.ALLOWED) {
            this.pickup = AbstractArrow.Pickup.ALLOWED;
        }
        if (((AbstractArrow)(Object)this).tickCount % 20 == 0) {
            // Log every second to avoid spam
            // com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Arrow ticking. Durability: {}", this.resourceCraft$durability);
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), require = 0)
    private void onPlayerTouch(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (com.resourcecraft.ResourceCraftConfig.debugLogs) {
            com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Arrow playerTouch! Durability: {}", this.resourceCraft$durability);
        }
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = {"getPickupItem", "getItemStack", "getStack", "getPickupStack"}, at = @At("RETURN"), require = 0)
    private net.minecraft.world.item.ItemStack onGetPickupItem(net.minecraft.world.item.ItemStack stack) {
        if (com.resourcecraft.ResourceCraftConfig.debugLogs) {
            com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Attempting arrow pickup persistence (via AbstractArrow)...");
        }
        if (stack != null && !stack.isEmpty()) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            net.minecraft.nbt.CompoundTag nbt = customData != null ? customData.copyTag() : new net.minecraft.nbt.CompoundTag();
            nbt.putInt("ResourceCraftDurability", this.resourceCraft$durability);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt));
            if (com.resourcecraft.ResourceCraftConfig.debugLogs) {
                com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Persisted durability {} to pickup stack", this.resourceCraft$durability);
            }
        }
        return stack;
    }

    // Hit detection is now handled by ProjectileMixin to ensure compatibility with 26.1.2

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddSaveData(net.minecraft.world.level.storage.ValueOutput nbt, CallbackInfo ci) {
        nbt.putInt("ResourceCraftDurability", this.resourceCraft$durability);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadSaveData(net.minecraft.world.level.storage.ValueInput nbt, CallbackInfo ci) {
        this.resourceCraft$durability = nbt.getIntOr("ResourceCraftDurability", 6);
    }

    @Unique
    @Override
    public void setResourceCraftDurability(int durability) {
        this.resourceCraft$durability = durability;
    }

    @Unique
    @Override
    public int getResourceCraftDurability() {
        return this.resourceCraft$durability;
    }
}
