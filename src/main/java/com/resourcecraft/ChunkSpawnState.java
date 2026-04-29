package com.resourcecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists which chunks and categories have already spawned their deterministic mobs.
 * Tracks (ChunkLong << 4 | CategoryOrdinal) per day/night cycle.
 */
public class ChunkSpawnState extends SavedData {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ResourceCraft.MOD_ID, "spawn_state");

    public static final Codec<ChunkSpawnState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.listOf().fieldOf("spawned").forGetter(s -> new ArrayList<>(s.spawned))
            ).apply(instance, ChunkSpawnState::new)
    );

    private final Set<Long> spawned;
    private final Object lock = new Object();

    public ChunkSpawnState() {
        this.spawned = java.util.concurrent.ConcurrentHashMap.newKeySet();
    }

    public ChunkSpawnState(List<Long> spawnedList) {
        this.spawned = java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.spawned.addAll(spawnedList);
    }

    /**
     * Bitflag Key Structure:
     * [63-32: ChunkX] [31-12: ChunkZ] [11-8: Category] [7: IsNight] [6-0: EnvironmentType]
     */
    public boolean hasSpawned(ChunkPos pos, int categoryOrdinal, boolean isNight, int envType) {
        return hasKey(getBitKey(pos, categoryOrdinal, isNight, envType));
    }

    public void markSpawned(ChunkPos pos, int categoryOrdinal, boolean isNight, int envType) {
        markKey(getBitKey(pos, categoryOrdinal, isNight, envType));
    }

    public boolean hasKey(long key) {
        return spawned.contains(key);
    }

    public void markKey(long key) {
        spawned.add(key);
        setDirty();
    }

    public void clear() {
        spawned.clear();
        setDirty();
    }

    public long getBitKey(ChunkPos pos, int categoryOrdinal, boolean isNight, int envType) {
        long chunkLong = ((long)pos.x() << 32) | (pos.z() & 0xFFFFFFFFL);
        
        // [FIX] Time-insensitive bits for specific environments and categories
        boolean bitNight = isNight;
        // 0: SurfaceLand, 1: SurfaceWater, 2: DeepLand, 3: DeepWater, 4: Nether, 5: End
        if (envType >= 2) {
            bitNight = false; // Deep caves and other dimensions don't care about the day/night bit
        } else if (categoryOrdinal != 0 && categoryOrdinal != 1) { 
            // 0: Monster, 1: Creature (Ordinal from MobCategory)
            bitNight = false; // Aquatics/Ambient/Misc don't care about the day/night bit
        }

        return (chunkLong << 12) | 
               ((long)(categoryOrdinal & 0xF) << 8) | 
               ((long)(bitNight ? 1 : 0) << 7) | 
               ((long)(envType & 0x7F));
    }

    public static ChunkSpawnState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(
                new SavedDataType<>(
                        ID,
                        ChunkSpawnState::new,
                        CODEC,
                        DataFixTypes.LEVEL
                )
        );
    }
}
