package com.resourcecraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.item.Item;

@Mixin(ArrowItem.class)
public abstract class ArrowItemMixin {

    @Inject(method = "createArrow", at = @At("RETURN"), require = 0)
    private void onCreateArrow(Level world, ItemStack stack, LivingEntity shooter, ItemStack bowStack, CallbackInfoReturnable<AbstractArrow> cir) {
        AbstractArrow arrow = cir.getReturnValue();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag nbt = customData.copyTag();
            if (nbt.contains("ResourceCraftDurability")) {
                int val = nbt.getInt("ResourceCraftDurability").orElse(6);
                ((com.resourcecraft.ArrowExt)arrow).setResourceCraftDurability(val);
                return;
            }
        }
        ((com.resourcecraft.ArrowExt)arrow).setResourceCraftDurability(6); // Default 6
    }

    @Inject(method = "appendHoverText", at = @At("HEAD"), require = 0)
    private void onAppendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type, CallbackInfo ci) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        int val = 6;
        if (customData != null) {
            CompoundTag nbt = customData.copyTag();
            if (nbt.contains("ResourceCraftDurability")) {
                val = nbt.getInt("ResourceCraftDurability").orElse(6);
            }
        }
        tooltip.add(Component.literal("§7Durability: " + val + "/6"));
    }
}
