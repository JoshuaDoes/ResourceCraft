package com.resourcecraft.mixin;

import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {
    
    /**
     * Completely disables vanilla tick-based spawning.
     */
    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void onSpawnForChunk(ServerLevel world, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> categories, CallbackInfo ci) {
        // Seizing control of the population engine
        ci.cancel();
    }

    /**
     * Completely disables vanilla generation-time spawning (Pack spawning).
     * This ensures no animals are pre-placed at world spawn or in newly generated chunks.
     */
    @Inject(method = "spawnMobsForChunkGeneration", at = @At("HEAD"), cancellable = true)
    private static void onSpawnMobsForChunkGeneration(ServerLevelAccessor world, Holder<Biome> biome, ChunkPos pos, RandomSource random, CallbackInfo ci) {
        ci.cancel();
    }
}
