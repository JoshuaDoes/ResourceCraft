package com.resourcecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Global state for ResourceCraft to track unique one-time events.
 */
public class GlobalResourceState extends SavedData {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ResourceCraft.MOD_ID, "global_state");

    public static final Codec<GlobalResourceState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.listOf().fieldOf("spawnedAncientCities").forGetter(s -> List.copyOf(s.spawnedAncientCities))
            ).apply(instance, GlobalResourceState::new)
    );

    private final Set<Long> spawnedAncientCities = new HashSet<>();

    public GlobalResourceState() {
    }

    public GlobalResourceState(List<Long> cities) {
        this.spawnedAncientCities.addAll(cities);
    }

    public boolean hasWardenSpawnedIn(long cityId) {
        return spawnedAncientCities.contains(cityId);
    }

    public void markWardenSpawned(long cityId) {
        if (spawnedAncientCities.add(cityId)) {
            setDirty();
        }
    }

    public static GlobalResourceState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(
                new SavedDataType<>(
                        ID,
                        GlobalResourceState::new,
                        CODEC,
                        DataFixTypes.LEVEL
                )
        );
    }
}
