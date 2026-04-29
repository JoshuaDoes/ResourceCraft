package com.resourcecraft.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin({Arrow.class, SpectralArrow.class})
public abstract class ArrowPickupMixin {

    @ModifyReturnValue(method = {"getPickupItem", "getItemStack", "getStack", "getPickupStack"}, at = @At("RETURN"), require = 0)
    private ItemStack onGetPickupItem(ItemStack stack) {
        com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Attempting arrow pickup persistence...");
        if (stack != null && !stack.isEmpty()) {
            int durability = ((com.resourcecraft.ArrowExt)this).getResourceCraftDurability();
            com.resourcecraft.ResourceCraft.LOGGER.info("ResourceCraft | Arrow durability to persist: {}", durability);
            
            CompoundTag nbt = new CompoundTag();
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                nbt = customData.copyTag();
            }
            nbt.putInt("ResourceCraftDurability", durability);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
            return stack;
        }
        return stack;
    }
}
