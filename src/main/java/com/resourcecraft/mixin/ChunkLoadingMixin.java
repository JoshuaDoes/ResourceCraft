package com.resourcecraft.mixin;

import com.resourcecraft.SpawnCalculator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ChunkLoadingMixin {

    @Unique
    private boolean resourceCraft$lastIsNight = false;
    @Unique
    private boolean resourceCraft$flipThisTick = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(java.util.function.BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel world = (ServerLevel)(Object)this;
        long timeOfDay = world.getOverworldClockTime() % 24000;
        boolean isNight = timeOfDay >= 13000 && timeOfDay < 23000;
        
        if (isNight != resourceCraft$lastIsNight) {
            resourceCraft$flipThisTick = true;
            resourceCraft$lastIsNight = isNight;
        } else {
            resourceCraft$flipThisTick = false;
        }
    }

    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void onTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerLevel world = (ServerLevel) (Object) this;
        ChunkPos chunkPos = chunk.getPos();
        
        long timeOfDay = world.getOverworldClockTime() % 24000;
        boolean isNight = timeOfDay >= 13000 && timeOfDay < 23000;

        // Trigger population on either:
        // 1. Initial chunk load (one-time mandate)
        // 2. Day/Night transition (time flip pass)
        if (!SpawnCalculator.isChunkInitialized(chunkPos) || resourceCraft$flipThisTick) {
            SpawnCalculator.checkPendingConditions(world, chunkPos, isNight);
        }

        // Process deferred spawns from other chunks that tried to spawn here while it was unloaded
        java.util.List<Runnable> pendingSpawns = SpawnCalculator.DEFERRED_SPAWNS.remove(chunkPos);
        if (pendingSpawns != null && !pendingSpawns.isEmpty()) {
            SpawnCalculator.SPAWN_EXECUTOR.submit(() -> {
                for (Runnable task : pendingSpawns) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        com.resourcecraft.ResourceCraft.LOGGER.error("Error executing deferred spawn in chunk {}", chunkPos, e);
                    }
                }
            });
        }
    }
}
