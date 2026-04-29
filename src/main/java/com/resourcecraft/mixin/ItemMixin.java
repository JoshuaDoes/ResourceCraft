package com.resourcecraft.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void onAppendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> consumer, TooltipFlag type, CallbackInfo ci) {
        if (!(stack.getItem() instanceof ArrowItem)) return;
        
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        int durability = 6;
        if (customData != null) {
            CompoundTag nbt = customData.copyTag();
            if (nbt.contains("ResourceCraftDurability")) {
                durability = nbt.getInt("ResourceCraftDurability").orElse(6);
            }
        }
        
        if (durability < 6) {
            consumer.accept(Component.literal("§eDurability: " + durability + " / 6").withStyle(net.minecraft.ChatFormatting.YELLOW));
        } else {
            consumer.accept(Component.literal("§7Durability: 6 / 6").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }
}
