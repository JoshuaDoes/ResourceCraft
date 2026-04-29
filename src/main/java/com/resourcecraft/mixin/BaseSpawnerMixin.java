package com.resourcecraft.mixin;

import com.resourcecraft.SpawnerExt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin implements SpawnerExt {

    @Shadow private int spawnDelay;

    @Unique
    private boolean resourceCraft$spawnedOnce = false;
    
    @Unique
    private boolean resourceCraft$forceSpawn = false;

    @Override
    @Unique
    public void setForceSpawn(boolean force) {
        this.resourceCraft$forceSpawn = force;
        if (force) {
            // Instantly trigger spawn when forced
            this.spawnDelay = 0;
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(Level world, BlockPos pos, ValueInput nbt, CallbackInfo ci) {
        this.resourceCraft$spawnedOnce = nbt.getBooleanOr("ResourceCraftSpawnedOnce", false);
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void onSave(ValueOutput nbt, CallbackInfo ci) {
        nbt.putBoolean("ResourceCraftSpawnedOnce", this.resourceCraft$spawnedOnce);
    }

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void onServerTick(ServerLevel world, BlockPos pos, CallbackInfo ci) {
        if (this.resourceCraft$spawnedOnce && !this.resourceCraft$forceSpawn) {
            this.spawnDelay = 9999; 
            ci.cancel();
        }
    }

    @Inject(method = "delay", at = @At("HEAD"))
    private void onDelay(Level world, BlockPos pos, CallbackInfo ci) {
        if (!this.resourceCraft$forceSpawn) {
            this.resourceCraft$spawnedOnce = true;
            if (!world.isClientSide()) {
                world.sendBlockUpdated(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    }

    @Inject(method = "setEntityId", at = @At("TAIL"))
    private void onSetEntityId(EntityType<?> type, Level world, RandomSource random, BlockPos pos, CallbackInfo ci) {
        this.resourceCraft$spawnedOnce = false;
        this.spawnDelay = 20; 
    }

    @Inject(method = "isNearPlayer", at = @At("HEAD"), cancellable = true)
    private void onIsNearPlayer(Level world, BlockPos pos, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (this.resourceCraft$spawnedOnce && !this.resourceCraft$forceSpawn) {
            cir.setReturnValue(false);
        }
    }
}
