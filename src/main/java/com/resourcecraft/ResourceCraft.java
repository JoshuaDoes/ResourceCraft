package com.resourcecraft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.midnightdust.lib.config.MidnightConfig;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.server.level.ServerLevel;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class ResourceCraft implements ModInitializer {
    public static final String MOD_ID = "resourcecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Track initial world spawns per seed to detect the "game default"
    public static final Map<Long, BlockPos> INITIAL_SPAWNS = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("ResourceCraft | Initializing Spawning Engine...");
        MidnightConfig.init("resourcecraft", ResourceCraftConfig.class);
        
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            SpawnCalculator.onChunkUnloaded(chunk.getPos());
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SpawnCalculator.reset(server);
            INITIAL_SPAWNS.clear();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SpawnCalculator.reset(server);
            INITIAL_SPAWNS.clear();
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (ServerLevel world : server.getAllLevels()) {
                SpawnCalculator.tick(world);
            }
        });
    }
}
