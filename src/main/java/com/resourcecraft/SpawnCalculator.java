package com.resourcecraft;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.lang.reflect.Field;
import java.util.ArrayList;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.phys.AABB;

import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Individualistic Sparse Spawning Overhaul with Triple-Region controls.
 * Separates Surface, Cave, and Water sparsity and enforces absolute solo spawning.
 */
public class SpawnCalculator {

    public static final int SPAWNING_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    public static final ExecutorService SPAWN_EXECUTOR = Executors.newFixedThreadPool(ResourceCraftConfig.threadCount);
    public static final Map<ChunkPos, List<Runnable>> DEFERRED_SPAWNS = new ConcurrentHashMap<>();
    public static final Map<Long, CompletableFuture<Void>> CHUNK_FUTURES = new ConcurrentHashMap<>();
    public static final Map<ChunkPos, java.util.Set<Integer>> PENDING_CONDITIONS = new ConcurrentHashMap<>();

    public static void reset() {
        DEFERRED_SPAWNS.clear();
        CHUNK_FUTURES.forEach((k, v) -> v.cancel(true));
        CHUNK_FUTURES.clear();
        PENDING_CONDITIONS.clear();
        INITIALIZED_CHUNKS.clear();
        LAST_SPAWN_TIMES.clear();
        PENDING_SPAWN_QUEUE.clear();
        currentSeed = -1;
        noise = null;
    }

    private static float getStructurePopulationGain(String structureId) {
        if ("minecraft:village".equals(structureId)) return ResourceCraftConfig.village;
        if ("minecraft:pillager_outpost".equals(structureId)) return ResourceCraftConfig.outpost;
        if ("minecraft:fortress".equals(structureId)) return ResourceCraftConfig.fortress;
        if ("minecraft:bastion_remnant".equals(structureId)) return ResourceCraftConfig.bastion;
        if ("minecraft:ancient_city".equals(structureId)) return ResourceCraftConfig.ancientCity;
        if ("minecraft:swamp_hut".equals(structureId)) return ResourceCraftConfig.swampHut;
        if ("minecraft:trial_chambers".equals(structureId)) return ResourceCraftConfig.trialChambers;
        if ("resourcecraft:warped_forest".equals(structureId)) return ResourceCraftConfig.warpedForest;
        if ("resourcecraft:end_boss_island".equals(structureId)) return ResourceCraftConfig.endIsland;
        return 1.0f;
    }
    
    private static float getStructureSpreadGain(String structureId) {
        if ("minecraft:village".equals(structureId)) return ResourceCraftConfig.villageSpread;
        if ("minecraft:pillager_outpost".equals(structureId)) return ResourceCraftConfig.outpostSpread;
        if ("minecraft:fortress".equals(structureId)) return ResourceCraftConfig.fortressSpread;
        if ("minecraft:bastion_remnant".equals(structureId)) return ResourceCraftConfig.bastionSpread;
        if ("minecraft:ancient_city".equals(structureId)) return ResourceCraftConfig.ancientCitySpread;
        if ("minecraft:swamp_hut".equals(structureId)) return 0.5f; // Tighter spread for witch huts
        if ("minecraft:trial_chambers".equals(structureId)) return 0.7f; // Tighter spread for trial chambers
        if ("resourcecraft:end_boss_island".equals(structureId)) return ResourceCraftConfig.endIslandSpread;
        return 1.0f;
    }

    public static final Map<EntityType<?>, Float> MOB_GAINS = new HashMap<>();
    static {
        MOB_GAINS.put(EntityType.ZOMBIE, ResourceCraftConfig.zombieHorde);
    }

    public static final int ENV_SURFACE_LAND = 0;
    public static final int ENV_SURFACE_WATER = 1;
    public static final int ENV_DEEP_LAND = 2;
    public static final int ENV_DEEP_WATER = 3;
    public static final int ENV_NETHER = 4;
    public static final int ENV_END = 5;

    private static final java.util.Set<Long> INITIALIZED_CHUNKS = new java.util.HashSet<>();
    private static ResourceCraftNoise noise;
    private static long currentSeed = -1;
    
    private static final Map<EntityType<?>, Float> BASE_WEIGHTS = new HashMap<>();
    private static final java.util.Queue<PendingSpawn> PENDING_SPAWN_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    
    private static record PendingSpawn(ServerLevel world, BlockPos pos, MobSpawnSettings.SpawnerData entry, boolean isCustomMisc, long seed, int envType, MobCategory category, boolean isNight, float yaw) {}
    
    public static void tick(ServerLevel world) {
        int spawnsThisTick = 0;
        int maxSpawns = 5; // Limit spawns per tick to prevent lag
        while (spawnsThisTick < maxSpawns && !PENDING_SPAWN_QUEUE.isEmpty()) {
            PendingSpawn pending = PENDING_SPAWN_QUEUE.poll();
            if (pending != null) {
                executeSpawn(pending);
                spawnsThisTick++;
            }
        }
    }

    /**
     * Centralized Noise Controller for deterministic population and spread.
     */
    private static class ResourceCraftNoise {
        private final PerlinNoise densityNoise; // 1 to 4 per spawn
        private final PerlinNoise spreadNoise;  // 1 to 6 chunks apart
        private final PerlinNoise regionNoise;  // 1 to 8 chunks apart

        public ResourceCraftNoise(long seed) {
            RandomSource rnd = RandomSource.create(seed);
            this.densityNoise = PerlinNoise.create(rnd, List.of(0));
            this.spreadNoise = PerlinNoise.create(rnd, List.of(0));
            this.regionNoise = PerlinNoise.create(rnd, List.of(0));
        }

        public int getRegionSize(int x, int y, int z, int surfaceY) {
            double raw = (regionNoise.getValue(x * 0.05, y * 0.05, z * 0.05) + 1.0) / 2.0; // 0 to 1
            double dist = Math.abs(y - surfaceY);
            double proximity = 1.0 / (1.0 + dist / ResourceCraftConfig.regionDecay); // 1.0 at surface, decays away
            
            // Scaled to favor target chunks at surface, and variance away.
            double target = ResourceCraftConfig.regionTarget;
            double variance = 1.0 + (raw * ResourceCraftConfig.regionVariance);
            return (int)Math.round(target * proximity + variance * (1.0 - proximity));
        }

        public int getSpawnCount(int x, int y, int z, int surfaceY, int minY, int maxY) {
            double raw = (densityNoise.getValue(x * 0.1, y * 0.1, z * 0.1) + 1.0) / 2.0;
            
            // Base density from noise (Deterministic density seed)
            double baseDensity = 1.0 + (raw * ResourceCraftConfig.baseDensityFactor);
            
            // Height-based Gain Scaling
            // Ground (surfaceY) = 1.0x, MinY = 2.0x, MaxY = 2.0x
            double heightGain;
            if (y < surfaceY) {
                double den = (double)(surfaceY - minY);
                heightGain = 1.0 + (den > 0 ? (double)(surfaceY - y) / den : 0);
            } else {
                double den = (double)(maxY - surfaceY);
                heightGain = 1.0 + (den > 0 ? (double)(y - surfaceY) / den : 0);
            }
            
            return (int)Math.max(1, Math.round(baseDensity * heightGain));
        }

        public int getSpreadRange(int x, int y, int z, boolean isNether, boolean isEnd) {
            double raw = (spreadNoise.getValue(x * 0.02, y * 0.02, z * 0.02) + 1.0) / 2.0;
            
            // Gain Math: Base spread is 1 to 6. Nether gets a 1.5x gain (up to 9), End gets a 2.0x gain (up to 11)
            double gain = isNether ? ResourceCraftConfig.netherGlobal : (isEnd ? ResourceCraftConfig.endGlobal : 1.0);
            
            return 1 + (int)(raw * ResourceCraftConfig.rangeFactor * gain); 
        }
    }

    private static float getBaseWeight(EntityType<?> type) {
        if (type == EntityType.ZOMBIE) return ResourceCraftConfig.zombie;
        if (type == EntityType.SKELETON) return ResourceCraftConfig.skeleton;
        if (type == EntityType.CREEPER) return ResourceCraftConfig.creeper;
        if (type == EntityType.SPIDER) return ResourceCraftConfig.spider;
        if (type == EntityType.ENDERMAN) return ResourceCraftConfig.enderman;
        if (type == EntityType.WITCH) return ResourceCraftConfig.witch;
        if (type == EntityType.SLIME) return ResourceCraftConfig.slime;
        if (type == EntityType.BOGGED) return ResourceCraftConfig.bogged;
        if (type == EntityType.HUSK) return ResourceCraftConfig.husk;
        if (type == EntityType.STRAY) return ResourceCraftConfig.stray;
        if (type == EntityType.DROWNED) return ResourceCraftConfig.drowned;
        if (type == EntityType.PHANTOM) return ResourceCraftConfig.phantom;
        if (type == EntityType.GUARDIAN) return ResourceCraftConfig.guardian;
        if (type == EntityType.SILVERFISH) return ResourceCraftConfig.silverfish;
        if (type == EntityType.CAVE_SPIDER) return ResourceCraftConfig.caveSpider;
        
        if (type == EntityType.ZOMBIFIED_PIGLIN) return ResourceCraftConfig.zombifiedPiglin;
        if (type == EntityType.PIGLIN) return ResourceCraftConfig.piglin;
        if (type == EntityType.HOGLIN) return ResourceCraftConfig.hoglin;
        if (type == EntityType.GHAST) return ResourceCraftConfig.ghast;
        if (type == EntityType.MAGMA_CUBE) return ResourceCraftConfig.magmaCube;
        if (type == EntityType.BLAZE) return ResourceCraftConfig.blaze;
        if (type == EntityType.WITHER_SKELETON) return ResourceCraftConfig.witherSkeleton;
        if (type == EntityType.STRIDER) return ResourceCraftConfig.strider;
        
        if (type == EntityType.PIG) return ResourceCraftConfig.pig;
        if (type == EntityType.COW) return ResourceCraftConfig.cow;
        if (type == EntityType.SHEEP) return ResourceCraftConfig.sheep;
        if (type == EntityType.CHICKEN) return ResourceCraftConfig.chicken;
        if (type == EntityType.HORSE) return ResourceCraftConfig.horse;
        if (type == EntityType.RABBIT) return ResourceCraftConfig.rabbit;
        if (type == EntityType.WOLF) return ResourceCraftConfig.wolf;
        if (type == EntityType.FOX) return ResourceCraftConfig.fox;
        if (type == EntityType.BAT) return ResourceCraftConfig.bat;
        if (type == EntityType.SQUID) return ResourceCraftConfig.squid;
        if (type == EntityType.GLOW_SQUID) return ResourceCraftConfig.glowSquid;
        
        return BASE_WEIGHTS.getOrDefault(type, 100.0f);
    }

    static {
        // MONSTERS (Standard fallbacks if not in config-mapped list)
        BASE_WEIGHTS.put(EntityType.ZOMBIE, 100.0f);
        
        // NETHER (Standard fallbacks)
        BASE_WEIGHTS.put(EntityType.ZOMBIFIED_PIGLIN, 30.0f);
        
        // CREATURES (Static for now, can be expanded if requested)
        BASE_WEIGHTS.put(EntityType.SHEEP, 12.0f);
        BASE_WEIGHTS.put(EntityType.PIG, 10.0f);
        BASE_WEIGHTS.put(EntityType.CHICKEN, 10.0f);
        BASE_WEIGHTS.put(EntityType.COW, 8.0f);
        BASE_WEIGHTS.put(EntityType.HORSE, 5.0f);
        BASE_WEIGHTS.put(EntityType.DONKEY, 1.0f);
        BASE_WEIGHTS.put(EntityType.MULE, 1.0f);
        BASE_WEIGHTS.put(EntityType.GOAT, 10.0f);
        BASE_WEIGHTS.put(EntityType.POLAR_BEAR, 10.0f);
        BASE_WEIGHTS.put(EntityType.FOX, 8.0f);
        BASE_WEIGHTS.put(EntityType.WOLF, 8.0f);
        BASE_WEIGHTS.put(EntityType.OCELOT, 2.0f);
        BASE_WEIGHTS.put(EntityType.PARROT, 40.0f);
        BASE_WEIGHTS.put(EntityType.PANDA, 5.0f);
        BASE_WEIGHTS.put(EntityType.RABBIT, 20.0f);
        BASE_WEIGHTS.put(EntityType.LLAMA, 10.0f);
        BASE_WEIGHTS.put(EntityType.CAMEL, 5.0f);
        BASE_WEIGHTS.put(EntityType.SNIFFER, 2.0f);
        BASE_WEIGHTS.put(EntityType.ARMADILLO, 10.0f);
        BASE_WEIGHTS.put(EntityType.FROG, 10.0f);
        BASE_WEIGHTS.put(EntityType.MOOSHROOM, 8.0f);
        
        // AQUATIC / AMBIENT
        BASE_WEIGHTS.put(EntityType.BAT, 5.0f);
        BASE_WEIGHTS.put(EntityType.SQUID, 10.0f);
        BASE_WEIGHTS.put(EntityType.COD, 100.0f);
        BASE_WEIGHTS.put(EntityType.SALMON, 100.0f);
        BASE_WEIGHTS.put(EntityType.TROPICAL_FISH, 100.0f);
        BASE_WEIGHTS.put(EntityType.PUFFERFISH, 15.0f);
        BASE_WEIGHTS.put(EntityType.DOLPHIN, 2.0f);
        BASE_WEIGHTS.put(EntityType.GLOW_SQUID, 10.0f);
        BASE_WEIGHTS.put(EntityType.AXOLOTL, 10.0f);
        BASE_WEIGHTS.put(EntityType.TADPOLE, 10.0f);
        
        // ILLAGERS (Raid/Patrol context)
        BASE_WEIGHTS.put(EntityType.PILLAGER, 20.0f);
        BASE_WEIGHTS.put(EntityType.VINDICATOR, 10.0f);
        BASE_WEIGHTS.put(EntityType.EVOKER, 5.0f);
        BASE_WEIGHTS.put(EntityType.RAVAGER, 5.0f);
        
        // VILLAGERS (Structure-specific)
        BASE_WEIGHTS.put(EntityType.VILLAGER, 100.0f);
        BASE_WEIGHTS.put(EntityType.WANDERING_TRADER, 1.0f);
    }

    private static final Map<Long, Long> LAST_SPAWN_TIMES = new HashMap<>();

    private static synchronized void ensureNoiseInitialized(long seed) {
        if (currentSeed != seed) {
            noise = new ResourceCraftNoise(seed);
            currentSeed = seed;
        }
    }

    public static void onBlockChanged(ServerLevel world, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        long cpLong = ((long)chunkPos.x() << 32) | (chunkPos.z() & 0xFFFFFFFFL);
        long currentTime = world.getGameTime();
        
        long lastSpawn = LAST_SPAWN_TIMES.getOrDefault(cpLong, 0L);
        if (currentTime - lastSpawn >= ResourceCraftConfig.gracePeriod) { // config grace period
            LAST_SPAWN_TIMES.put(cpLong, currentTime);
            // Use getOverworldClockTime() as seen in previous logic
            long timeOfDay = world.getOverworldClockTime() % 24000;
            boolean isNight = timeOfDay >= 13000 && timeOfDay < 23000;
            checkPendingConditions(world, chunkPos, isNight);
        }
    }

    public static boolean isChunkInitialized(ChunkPos chunkPos) {
        long cpLong = ((long)chunkPos.x() << 32) | (chunkPos.z() & 0xFFFFFFFFL);
        return INITIALIZED_CHUNKS.contains(cpLong);
    }

    public static void onChunkUnloaded(ChunkPos chunkPos) {
        long cpLong = ((long)chunkPos.x() << 32) | (chunkPos.z() & 0xFFFFFFFFL);
        INITIALIZED_CHUNKS.remove(cpLong);
        DEFERRED_SPAWNS.remove(chunkPos);
        PENDING_CONDITIONS.remove(chunkPos);
        CompletableFuture<Void> future = CHUNK_FUTURES.remove(cpLong);
        if (future != null) {
            future.cancel(true);
        }
    }

    public static void checkPendingConditions(ServerLevel world, ChunkPos chunkPos, boolean isNight) {
        java.util.Set<Integer> pending = PENDING_CONDITIONS.get(chunkPos);
        if (pending == null) {
            // Initial load or transition
            refreshPendingConditions(world, chunkPos, isNight);
            pending = PENDING_CONDITIONS.get(chunkPos);
        }
        
        if (pending != null && !pending.isEmpty()) {
            // Check if current (isNight) matches any pending condition
            // Note: envType is dynamic, but we can check categories
            spawnMobsForChunk(world, chunkPos, isNight);
        }
    }

    private static void refreshPendingConditions(ServerLevel world, ChunkPos chunkPos, boolean isNight) {
        ChunkSpawnState state = ChunkSpawnState.get(world);
        java.util.Set<Integer> pending = new java.util.HashSet<>();
        
        // Ordinals for categories (0-7)
        for (int cat = 0; cat <= 7; cat++) {
            // Check day and night for both surface environments (0 and 1)
            for (int night = 0; night <= 1; night++) {
                for (int env = 0; env <= 5; env++) {
                    if (!state.hasKey(state.getBitKey(chunkPos, cat, night == 1, env))) {
                        pending.add(cat); // Keep it simple: if category is missing ANY condition, it's pending
                        break;
                    }
                }
            }
        }
        
        if (!pending.isEmpty()) {
            PENDING_CONDITIONS.put(chunkPos, pending);
        } else {
            PENDING_CONDITIONS.remove(chunkPos);
        }
    }

    public static boolean isPreloadingComplete(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        int radius = Math.max(world.getServer().getPlayerList().getViewDistance(), world.getServer().getPlayerList().getSimulationDistance());
        
        ChunkPos center = player.chunkPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!world.hasChunk(center.x() + x, center.z() + z)) {
                    return false;
                }
            }
        }
        
        if (SPAWN_EXECUTOR instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            if (tpe.getActiveCount() > 0 || !tpe.getQueue().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    public static void spawnMobsForChunk(ServerLevel world, ChunkPos chunkPos, boolean isNight) {
        if (!world.getServer().isReady()) return;
        long cpLong = ((long)chunkPos.x() << 32) | (chunkPos.z() & 0xFFFFFFFFL);
        boolean firstRun = !INITIALIZED_CHUNKS.contains(cpLong);
        
        if (firstRun) INITIALIZED_CHUNKS.add(cpLong);
        
        final ChunkSpawnState state = ChunkSpawnState.get(world);
        final long seed = world.getSeed();
        final int minBuildHeight = world.getMinY();
        final int maxBuildHeight = world.getMaxY();
        final int dimType = world.dimension() == Level.NETHER ? ENV_NETHER : (world.dimension() == Level.END ? ENV_END : -1);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                ensureNoiseInitialized(seed);

                MobCategory[] categories = {
                    MobCategory.MONSTER, MobCategory.CREATURE, MobCategory.AMBIENT,
                    MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT,
                    MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS,
                    MobCategory.MISC
                };

                // 1. Precise Surface Scan (Top-down)
                int actualSurfaceY = minBuildHeight;
                int actualGroundY = minBuildHeight;
                BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos(chunkPos.getMiddleBlockX(), maxBuildHeight, chunkPos.getMiddleBlockZ());
                
                if (world.dimension() == Level.NETHER) {
                    // NETHER: Surface is the first non-bedrock floor from top down
                    while (scanPos.getY() > minBuildHeight) {
                        BlockState bs = world.getBlockState(scanPos);
                        if (!bs.is(Blocks.BEDROCK) && (bs.isSolid() || bs.is(Blocks.LAVA))) {
                            actualSurfaceY = scanPos.getY();
                            actualGroundY = scanPos.getY();
                            break;
                        }
                        scanPos.move(0, -1, 0);
                    }
                } else {
                    while (scanPos.getY() > minBuildHeight) {
                        BlockState bs = world.getBlockState(scanPos);
                        if (actualSurfaceY == minBuildHeight && (bs.isSolid() || bs.is(Blocks.WATER))) {
                            actualSurfaceY = scanPos.getY();
                        }
                        if (bs.isSolid()) {
                            actualGroundY = scanPos.getY();
                            break;
                        }
                        scanPos.move(0, -1, 0);
                    }
                }
                
                if (actualSurfaceY <= minBuildHeight) {
                    actualSurfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
                }
                if (actualGroundY <= world.getMinY()) {
                    actualGroundY = world.getHeight(Heightmap.Types.OCEAN_FLOOR, chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
                }

                for (MobCategory category : categories) {
                    // Optimization: If all possible environments for this dim are already spawned, skip entirely
                    if (dimType != -1) {
                        if (state.hasSpawned(chunkPos, category.ordinal(), isNight, dimType)) continue;
                    } else {
                        boolean allSpawned = true;
                        for (int e = 0; e <= 3; e++) {
                            if (!state.hasSpawned(chunkPos, category.ordinal(), isNight, e)) {
                                allSpawned = false;
                                break;
                            }
                        }
                        if (allSpawned) continue;
                    }
                    
                    trySpawnCategoryInVerticalSlice(world, chunkPos, category, actualSurfaceY, actualGroundY, isNight, state, noise);
                }
            } catch (Exception e) {
                ResourceCraft.LOGGER.error("ResourceCraft | [ASYNC] Error spawning mobs for chunk {}", chunkPos, e);
            }
        }, SPAWN_EXECUTOR);
        
        CHUNK_FUTURES.put(cpLong, future);
        future.whenComplete((res, err) -> {
            CHUNK_FUTURES.remove(cpLong);
            refreshPendingConditions(world, chunkPos, isNight);
        });
    }
    private static void trySpawnCategoryInVerticalSlice(ServerLevel world, ChunkPos chunkPos, MobCategory category, int actualSurfaceY, int actualGroundY, boolean isNight, ChunkSpawnState state, ResourceCraftNoise noise) {
        BlockPos centerPosLookup = new BlockPos(chunkPos.getMiddleBlockX(), actualSurfaceY, chunkPos.getMiddleBlockZ());
        WeightedList<MobSpawnSettings.SpawnerData> weightedList = getSpawnsAt(world, centerPosLookup, category);
        List<Weighted<MobSpawnSettings.SpawnerData>> unwrapped = getWeightedEntries(weightedList);
        boolean isCustomMisc = false;
        
        if (unwrapped.isEmpty() && category == MobCategory.MISC) {
            weightedList = getCustomMiscSpawns(world, centerPosLookup);
            unwrapped = getWeightedEntries(weightedList);
            isCustomMisc = true;
        }
        
        if (unwrapped.isEmpty()) return;

        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        long timeOfDay = world.getOverworldClockTime() % 24000;
        boolean isNether = world.dimension().identifier().getPath().contains("nether");
        boolean isOverworld = world.dimension() == Level.OVERWORLD;
        boolean effectivelyNight = isNether || (timeOfDay >= 13000 && timeOfDay < 23000);

        // 1. Explicit Surface Check (Crucial for animals/monsters)
        // For land mobs, we want to check the air block above the surface.
        int surfaceCheckY = actualSurfaceY;
        if (!category.name().contains("WATER") && !isNether && world.dimension() != Level.END) {
            surfaceCheckY++; 
        }
        if (trySpawnAtY(world, chunkPos, category, surfaceCheckY, centerX, centerZ, actualSurfaceY, actualGroundY, effectivelyNight, isNight, state, noise, unwrapped, isCustomMisc, isOverworld, isNether)) {
            return; // Only one allowance per category per chunk
        }

        // 2. Column Scan
        // Use higher resolution for water (4 blocks) and medium for land (8 blocks)
        int step = (category.name().contains("WATER")) ? 4 : 8;
        for (int y = world.getMinY() + 4; y < world.getMaxY() - 4; y += step) {
            if (Math.abs(y - actualSurfaceY) < step) continue; // Surface already checked
            
            if (trySpawnAtY(world, chunkPos, category, y, centerX, centerZ, actualSurfaceY, actualGroundY, effectivelyNight, isNight, state, noise, unwrapped, isCustomMisc, isOverworld, isNether)) {
                break; // One allowance per category per chunk
            }
        }
    }

    private static boolean trySpawnAtY(ServerLevel world, ChunkPos chunkPos, MobCategory category, int y, int centerX, int centerZ, int actualSurfaceY, int actualGroundY, boolean effectivelyNight, boolean isNight, ChunkSpawnState state, ResourceCraftNoise noise, List<Weighted<MobSpawnSettings.SpawnerData>> unwrapped, boolean isCustomMisc, boolean isOverworld, boolean isNether) {
        BlockPos spawnPos = new BlockPos(centerX, y, centerZ);
        boolean isWaterCategory = category.name().contains("WATER");
        
        // [FIX] For land mobs, ensure we are checking the air block above ground if the current Y is solid
        if (!isWaterCategory && world.getBlockState(spawnPos).isSolid()) {
            spawnPos = spawnPos.above();
        }
        
        // Environment Context
        int envType;
        if (isNether) {
            envType = ENV_NETHER;
        } else if (world.dimension() == Level.END) {
            envType = ENV_END;
        } else {
            boolean isWater = world.getBlockState(spawnPos).is(Blocks.WATER) || world.getFluidState(spawnPos).is(FluidTags.WATER);
            boolean isLava = world.getBlockState(spawnPos).is(Blocks.LAVA) || world.getFluidState(spawnPos).is(FluidTags.LAVA);
            
            if (spawnPos.getY() >= actualGroundY - 2) {
                envType = (isWater || isLava) ? ENV_SURFACE_WATER : ENV_SURFACE_LAND;
            } else {
                envType = (isWater || isLava) ? ENV_DEEP_WATER : ENV_DEEP_LAND;
            }
        }
        
        boolean isUnderground = spawnPos.getY() < actualGroundY;
        boolean isEnd = world.dimension() == Level.END;

        // Global Condition Bitflag
        long bitKey = state.getBitKey(chunkPos, category.ordinal(), effectivelyNight, envType);
        if (state.hasKey(bitKey)) return false;

        // Population Control
        if (category == MobCategory.MONSTER) {
            // Monsters: Night only on surface, anytime underground/Nether/End
            if (!effectivelyNight && !isUnderground && !isNether && !isEnd) return false;
        } else if (category == MobCategory.CREATURE) {
            // Animals: Day only on surface, never underground (except Nether)
            if (effectivelyNight && !isNether) return false;
            if (isUnderground && !isNether) return false;
        }

        // High-Density Area Detection
        StructureStart start = world.structureManager().getStructureWithPieceAt(spawnPos, (s) -> true);
        boolean isStructure = false;
        String structureId = null;
        if (start != null && start.isValid()) {
            if (start.getStructure().spawnOverrides().containsKey(category)) {
                isStructure = true;
                net.minecraft.resources.Identifier loc = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(start.getStructure());
                if (loc != null) {
                    structureId = loc.toString();
                }
            }
        }

        if (!isStructure && category == MobCategory.MONSTER) {
            Holder<Biome> biomeHolder = world.getBiome(spawnPos);
            String biomePath = biomeHolder.unwrapKey().isPresent() ? biomeHolder.unwrapKey().get().identifier().getPath() : "";
            if (biomePath.contains("warped_forest")) {
                isStructure = true;
                structureId = "resourcecraft:warped_forest";
            } else if (world.dimension() == Level.END && (spawnPos.getX() * (double)spawnPos.getX() + spawnPos.getZ() * (double)spawnPos.getZ() < 1000000.0)) {
                isStructure = true;
                structureId = "resourcecraft:end_boss_island";
            }
        }
        
        Random random = new Random(bitKey);
        MobSpawnSettings.SpawnerData entry = selectMobWithWeights(world, spawnPos, unwrapped, random, effectivelyNight, isCustomMisc);
        if (entry == null) return false;

        float regionSize = 1.0f;
        if (!isStructure) {
            float baseRegionSize = (float)noise.getRegionSize(centerX, spawnPos.getY(), centerZ, actualSurfaceY);
            regionSize = baseRegionSize;

            if (category.name().contains("WATER") || category == MobCategory.AXOLOTLS) {
                if (spawnPos.getY() >= actualGroundY) {
                    double distToBottom = (double)(actualSurfaceY - actualGroundY);
                    double heightRatio = (distToBottom > 0) ? Math.max(0.0, Math.min(1.0, (double)(actualSurfaceY - spawnPos.getY()) / distToBottom)) : 0.0;
                    
                    double startMult = ResourceCraftConfig.surfaceWater;
                    double endMult = ResourceCraftConfig.deepWater;
                    double mult = startMult * (1.0 - heightRatio) + endMult * heightRatio;
                    
                    regionSize = (float)Math.max(ResourceCraftConfig.deepWater, baseRegionSize * mult);
                }
            } else if (category == MobCategory.CREATURE) {
                regionSize = (float)Math.max(1.0, baseRegionSize);
            } else if (category == MobCategory.MONSTER) {
                if (isOverworld) {
                    // Scale density from surface multiplier to base region size
                    int minY = world.getMinY();
                    double distToBottom = (double)(actualSurfaceY - minY);
                    double heightRatio = (distToBottom > 0) ? Math.max(0.0, Math.min(1.0, (double)(actualSurfaceY - spawnPos.getY()) / distToBottom)) : 0.0;
                    
                    double startMult = ResourceCraftConfig.surfaceMonster;
                    double endMult = ResourceCraftConfig.deepMonster / Math.max(ResourceCraftConfig.deepMonster, baseRegionSize);
                    double mult = startMult * (1.0 - heightRatio) + endMult * heightRatio;
                    
                    regionSize = (float)Math.max(ResourceCraftConfig.deepMonster, baseRegionSize * mult);
                } else if (isNether) {
                    // Population Control: Warped Forest Endermen Mandate (100% rate = 1 region size)
                    Holder<Biome> biomeHolder = world.getBiome(spawnPos);
                    String biomePath = biomeHolder.unwrapKey().isPresent() ? biomeHolder.unwrapKey().get().identifier().getPath() : "";
                    if (biomePath.contains("warped_forest")) {
                        regionSize = 0.5f;
                    } else if (entry.type() == EntityType.GHAST) {
                        regionSize = 0.25f; // High density for Ghasts
                    } else {
                        // Scale density from ceiling multiplier to base region size
                        int minY = world.getMinY();
                        int maxY = world.getMaxY();
                        double distToBottom = (double)(maxY - minY);
                        double heightRatio = (distToBottom > 0) ? Math.max(0.0, Math.min(1.0, (double)(maxY - spawnPos.getY()) / distToBottom)) : 0.0;
                        
                        double startMult = 1.0;
                        double endMult = ResourceCraftConfig.deepMonster / Math.max(ResourceCraftConfig.deepMonster, baseRegionSize);
                        double mult = startMult * (1.0 - heightRatio) + endMult * heightRatio;
                        regionSize = (float)Math.max(ResourceCraftConfig.deepMonster, baseRegionSize * mult);
                    }
                } else if (world.dimension() == Level.END) {
                    if ("resourcecraft:end_boss_island".equals(structureId)) {
                        regionSize = 0.333f; // 1 spawn every 0.333 chunks (3x density)
                    }
                }
            }
            
            if (regionSize >= 1.0f) {
                int rSize = Math.round(regionSize);
                if ((centerX / 16) % rSize != 0 || (centerZ / 16) % rSize != 0) return false;
            }
        }
        
        // [FIX] Phantom: Only spawn high above ground (approx 2 chunks = 32 blocks)
        if (entry.type() == EntityType.PHANTOM) {
            if (spawnPos.getY() < actualSurfaceY + ResourceCraftConfig.phantomMinOffset || spawnPos.getY() > actualSurfaceY + ResourceCraftConfig.phantomMaxOffset) return false;
            if (!world.getBlockState(spawnPos).isAir()) return false;
        }
        
        // [FIX] Witch: Only spawn above ground and respect max count
        if (entry.type() == EntityType.WITCH) {
            if (ResourceCraftConfig.witchAboveGroundOnly && spawnPos.getY() < actualSurfaceY) return false;
        }

        // [FIX] Warped Forest: Strictly Endermen only
        Holder<Biome> biomeHolder = world.getBiome(spawnPos);
        String biomePath = biomeHolder.unwrapKey().isPresent() ? biomeHolder.unwrapKey().get().identifier().getPath() : "";
        if (isNether && biomePath.contains("warped_forest")) {
            if (entry.type() != EntityType.ENDERMAN) return false;
        }

        if (!isEnvironmentCompatible(world, spawnPos, entry.type())) return false;

        // [FIX] Prevent Nether ceiling spawns (bedrock ceiling is typically Y > 120)
        if (isNether && spawnPos.getY() > ResourceCraftConfig.netherCeilingLimit && world.getBlockState(spawnPos.below()).is(Blocks.BEDROCK)) {
            return false;
        }
        
        if (isOverworld && !isStructure && !isUnderground) {
            if (isOverworldSurfaceAnimal(entry.type())) {
                // Animals spawn day and night
                if (!world.canSeeSky(spawnPos) && world.getBrightness(LightLayer.SKY, spawnPos) < ResourceCraftConfig.surfaceSkyLight) return false;
            }
            if (isMonsterType(entry.type())) {
                if (!isNight) return false; // Monsters only at night
                if (world.getBrightness(LightLayer.BLOCK, spawnPos) > 0) return false;
            }
        } else if (isOverworld && isOverworldSurfaceAnimal(entry.type()) && isUnderground) {
            return false;
        }

        if (isStructure || isValidSpawn(world, spawnPos, entry.type(), RandomSource.create(bitKey))) {
            int count = 0;
            
            if (isStructure) {
                float gain = structureId != null ? getStructurePopulationGain(structureId) : 1.0f;
                int baseCount = ResourceCraftConfig.structureBaseCount;
                if ("resourcecraft:warped_forest".equals(structureId) || "resourcecraft:end_boss_island".equals(structureId)) {
                    baseCount = ResourceCraftConfig.specialStructureBaseCount;
                }
                
                float exactCount = baseCount * gain;
                count = (int) exactCount;
                if (random.nextFloat() < (exactCount - count)) {
                    count++;
                }
                
                // If fractional probability failed entirely, mark key and move on
                if (count <= 0) {
                    state.markKey(bitKey);
                    return true;
                }
            } else {
                count = noise.getSpawnCount(centerX, y, centerZ, actualSurfaceY, world.getMinY(), world.getMaxY());
                if (regionSize < 1.0f) {
                    count = (int)(count * (1.0f / regionSize));
                }
                if (category == MobCategory.MISC) {
                    count = Math.max(1, count * 2);
                }

                // Apply Entity-specific population gains (e.g. Zombie Hordes)
                float mobGain = MOB_GAINS.getOrDefault(entry.type(), 1.0f);
                if (mobGain != 1.0f) {
                    float exactCount = count * mobGain;
                    count = (int) exactCount;
                    if (random.nextFloat() < (exactCount - count)) {
                        count++;
                    }
                }
            }
            
            int range = noise.getSpreadRange(centerX, y, centerZ, isNether, world.dimension() == Level.END);
            if (isStructure && structureId != null) {
                range = (int)Math.max(1, range * getStructureSpreadGain(structureId));
            }
            
            // [FIX] Ghast Balancing: Lower count and higher spread
            if (entry.type() == EntityType.GHAST) {
                count = Math.min(count, 2); // Cap at 2
                range *= 1.5; 
            }
            
            // [FIX] Witch Balancing: Max count of 1
            if (entry.type() == EntityType.WITCH) {
                count = Math.min(count, ResourceCraftConfig.witchMax);
            }

            int successful = attemptSpreadSpawn(world, spawnPos, entry, count, range, isCustomMisc, isStructure, random, envType, category, effectivelyNight);
            if (successful > 0) {
                state.markKey(bitKey);
                return true; 
            }
        }
        return false;
    }

    private static int attemptSpreadSpawn(ServerLevel world, BlockPos origin, MobSpawnSettings.SpawnerData entry, int count, int rangeChunks, boolean isCustomMisc, boolean isStructure, Random random, int envType, MobCategory category, boolean isNight) {
        int successful = 0;
        int rangeBlocks = rangeChunks * 16;
        
        for (int i = 0; i < 100 && successful < count; i++) {
            int dx = (random.nextInt(rangeBlocks * 2 + 1)) - rangeBlocks;
            int dz = (random.nextInt(rangeBlocks * 2 + 1)) - rangeBlocks;
            BlockPos targetPos = origin.offset(dx, 0, dz);
            
            // PERFORMANCE FIX: Prevent cascading chunk generation by ignoring unloaded chunks
            if (!world.hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
                ChunkPos targetChunk = new ChunkPos(targetPos.getX() >> 4, targetPos.getZ() >> 4);
                MobSpawnSettings.SpawnerData finalEntry = entry;
                MobCategory finalCategory = category;
                boolean finalIsCustomMisc = isCustomMisc;
                boolean finalIsStructure = isStructure;
                int finalEnvType = envType;
                boolean finalIsNight = isNight;
                long seed = random.nextLong();
                
                Runnable deferredSpawn = () -> {
                    Random r = new Random(seed);
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                        BlockPos finalPos = targetPos.above(dy);
                        if (!isEnvironmentCompatible(world, finalPos, finalEntry.type())) continue;
                        if (finalIsStructure || isValidSpawn(world, finalPos, finalEntry.type(), RandomSource.create(r.nextLong()))) {
                            if (!world.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(finalPos).inflate(1.2), e -> true).isEmpty()) continue;
                            spawnEntity(world, finalPos, finalEntry, finalIsCustomMisc, r, finalEnvType, finalCategory, finalIsNight);
                            break;
                        }
                    }
                };
                DEFERRED_SPAWNS.computeIfAbsent(targetChunk, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(deferredSpawn);
                continue;
            }
            
            // Adjust Y slightly to find ground/water if needed
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                BlockPos finalPos = targetPos.above(dy);
                
                // STRICT HABITAT CHECK: Eliminate suffocation and sky spawns (Mandatory even for structures)
                if (!isEnvironmentCompatible(world, finalPos, entry.type())) continue;

                if (isStructure || isValidSpawn(world, finalPos, entry.type(), RandomSource.create(random.nextLong()))) {
                    // SPATIAL OCCUPANCY CHECK: Prevent merging spawns
                    if (!world.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(finalPos).inflate(1.2), e -> true).isEmpty()) {
                        continue;
                    }
                    
                    if (spawnEntity(world, finalPos, entry, isCustomMisc, random, envType, category, isNight)) {
                        successful++;
                        break;
                    }
                }
            }
        }
        return successful;
    }

    private static boolean spawnEntity(ServerLevel world, BlockPos pos, MobSpawnSettings.SpawnerData entry, boolean isCustomMisc, Random random, int envType, MobCategory category, boolean isNight) {
        float yaw = random.nextFloat() * 360.0F;
        long seed = random.nextLong();
        
        PENDING_SPAWN_QUEUE.add(new PendingSpawn(world, pos, entry, isCustomMisc, seed, envType, category, isNight, yaw));
        return true;
    }

    private static void executeSpawn(PendingSpawn pending) {
        ServerLevel world = pending.world();
        BlockPos pos = pending.pos();
        MobSpawnSettings.SpawnerData entry = pending.entry();
        
        try {
            Entity entity = entry.type().create(world, EntitySpawnReason.NATURAL);
            if (entity == null) return;

            entity.setPos(pos.getX() + 0.5, (double)pos.getY(), pos.getZ() + 0.5);
            entity.setYRot(pending.yaw());
            entity.setXRot(0.0F);

            if (entity instanceof Mob mob) {
                mob.setNoAi(false);
                mob.setPersistenceRequired();
                mob.setYHeadRot(pending.yaw());
                mob.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            }

            entity.addTag("ResourceCraft_Habitat:" + pending.envType());
            entity.addTag("ResourceCraft_Category:" + pending.category().name());
            entity.addTag("ResourceCraft_IsNight:" + pending.isNight());

            if (pending.isCustomMisc()) {
                initializeAbandonedEncounter(entity, new Random(pending.seed()), world, pos);
            }

            if (world.addFreshEntity(entity)) {
                if (ResourceCraftConfig.debugLogs) {
                    if (pending.isCustomMisc()) {
                        ResourceCraft.LOGGER.info("ResourceCraft | [MISC SPAWN] {} at {} | Habitat: {}", 
                            entity.getType().getDescription().getString(), pos, pending.envType());
                    } else {
                        ResourceCraft.LOGGER.info("ResourceCraft | [SPAWN] {} at {} | Habitat: {} | Category: {} | Night: {}", 
                            entity.getType().getDescription().getString(), pos, pending.envType(), pending.category(), pending.isNight());
                    }
                }
            }
        } catch (Exception e) {
            ResourceCraft.LOGGER.error("Failed to spawn entity: {}", entry.type(), e);
        }
    }

    private static MobSpawnSettings.SpawnerData selectMobWithWeights(ServerLevel world, BlockPos pos, List<Weighted<MobSpawnSettings.SpawnerData>> pool, Random random, boolean isNight, boolean isCustomMisc) {
        float totalWeight = 0;
        List<MobSpawnSettings.SpawnerData> eligible = new ArrayList<>();
        
        Holder<Biome> biomeHolder = world.getBiome(pos);
        String biomePath = biomeHolder.unwrapKey().isPresent() ? biomeHolder.unwrapKey().get().identifier().getPath() : "";
        
        boolean isSwamp = biomePath.contains("swamp");
        long slimeSeed = world.getSeed() + (long)(pos.getX() >> 4) * (pos.getX() >> 4) * 0x4c1906 + (long)(pos.getX() >> 4) * 0x5a4a89 + (long)(pos.getZ() >> 4) * (pos.getZ() >> 4) * 0x3ad89bL + (long)(pos.getZ() >> 4) * 0x43e00bL ^ 0x3ad89b016L;
        boolean isSlimeChunk = new Random(slimeSeed).nextInt(10) == 0;

        for (Weighted<MobSpawnSettings.SpawnerData> w : pool) {
            MobSpawnSettings.SpawnerData data = w.value();
            EntityType<?> type = data.type();
            
            // [FIX] Nether: Convert regular Skeletons to Wither Skeletons
            if (world.dimension().identifier().getPath().contains("nether")) {
                if (type == EntityType.SKELETON) {
                    type = EntityType.WITHER_SKELETON;
                    data = new MobSpawnSettings.SpawnerData(type, data.minCount(), data.maxCount());
                }
            }
            
            // [FIX] MISC: Strictly block surface animals from MISC pool
            if (isCustomMisc && isOverworldSurfaceAnimal(type)) continue;
            
            if (type == EntityType.SLIME) {
                if (!( (isSlimeChunk && pos.getY() < 40) || (isSwamp && isNight) )) continue;
            }
            if (type == EntityType.BOGGED && !isSwamp) continue;
            if (type == EntityType.HUSK && !biomePath.contains("desert") && !biomePath.contains("badlands")) continue;
            
            float weight = getBaseWeight(type);
            totalWeight += weight;
            eligible.add(data);
        }

        if (eligible.isEmpty()) return null;

        float roll = random.nextFloat() * totalWeight;
        float current = 0;
        for (MobSpawnSettings.SpawnerData data : eligible) {
            current += getBaseWeight(data.type());
            if (roll < current) return data;
        }
        return eligible.get(0);
    }

    private static void initializeAbandonedEncounter(Entity entity, Random random, ServerLevel world, BlockPos pos) {
        Holder<Biome> biomeHolder = world.getBiome(pos);
        String biomePath = biomeHolder.unwrapKey().isPresent() ? biomeHolder.unwrapKey().get().identifier().getPath() : "";
        String dim = world.dimension().identifier().getPath();

        if (entity instanceof ArmorStand stand) {
            float variant = random.nextFloat();
            if (dim.contains("end")) {
                stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DRAGON_HEAD));
            } else if (biomePath.contains("desert")) {
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
            } else if (biomePath.contains("snow") || biomePath.contains("ice")) {
                stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            } else if (biomePath.contains("jungle")) {
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BAMBOO));
            } else {
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(variant < 0.5f ? Items.IRON_HELMET : Items.CARVED_PUMPKIN));
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant < 0.5f ? Items.IRON_SWORD : Items.STICK));
            }
        } else if (entity instanceof ContainerEntity container) {
            if (dim.contains("nether")) {
                container.setItem(0, new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4)));
                container.setItem(1, new ItemStack(Items.NETHER_WART, 2 + random.nextInt(4)));
            } else if (dim.contains("end")) {
                container.setItem(0, new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
                container.setItem(1, new ItemStack(Items.CHORUS_FRUIT, 4 + random.nextInt(12)));
            } else if (biomePath.contains("swamp")) {
                container.setItem(0, new ItemStack(Items.SLIME_BALL, 2 + random.nextInt(4)));
                container.setItem(1, new ItemStack(Items.BROWN_MUSHROOM, 4 + random.nextInt(8)));
            } else {
                container.setItem(0, new ItemStack(Items.BREAD, 2 + random.nextInt(4)));
                container.setItem(1, new ItemStack(Items.TORCH, 8 + random.nextInt(16)));
                if (random.nextFloat() < 0.1f) container.setItem(2, new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
            }
        } else if (entity instanceof ItemEntity item) {
            float relic = random.nextFloat();
            if (dim.contains("nether")) {
                if (relic < 0.3f) item.setItem(new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
                else if (relic < 0.6f) item.setItem(new ItemStack(Items.FLINT_AND_STEEL));
                else item.setItem(new ItemStack(Items.BLAZE_ROD));
            } else if (dim.contains("end")) {
                if (relic < 0.3f) item.setItem(new ItemStack(Items.ENDER_EYE));
                else if (relic < 0.6f) item.setItem(new ItemStack(Items.SHULKER_SHELL));
                else item.setItem(new ItemStack(Items.CHORUS_FLOWER));
            } else {
                if (relic < 0.2f) item.setItem(new ItemStack(Items.STONE_PICKAXE));
                else if (relic < 0.4f) item.setItem(new ItemStack(Items.SADDLE));
                else if (relic < 0.6f) item.setItem(new ItemStack(Items.WRITABLE_BOOK));
                else if (relic < 0.7f) item.setItem(new ItemStack(Items.MUSIC_DISC_PIGSTEP));
                else if (relic < 0.8f) item.setItem(new ItemStack(Items.SHEARS));
                else if (relic < 0.9f) item.setItem(new ItemStack(Items.HONEY_BOTTLE));
                else if (relic < 0.98f) {
                    item.setItem(new ItemStack(Items.GOLD_INGOT));
                    item.setGlowingTag(true);
                } else {
                    item.setItem(new ItemStack(Items.GOLDEN_APPLE));
                    item.setGlowingTag(true);
                }
            }
        }
    }

    private static boolean isEnvironmentCompatible(ServerLevel world, BlockPos pos, EntityType<?> type) {
        BlockState stateAt = world.getBlockState(pos);
        BlockState stateBelow = world.getBlockState(pos.below());
        boolean posIsWater = stateAt.is(Blocks.WATER) || world.getFluidState(pos).is(FluidTags.WATER);
        boolean posIsLava = stateAt.is(Blocks.LAVA) || world.getFluidState(pos).is(FluidTags.LAVA);
        
        // Aquatic Mobs: Must be IN water (Never in air)
        if (isAquaticType(type)) {
            return posIsWater;
        }
        
        // [FIX] Lava Spawning: Allow Striders/Magma Cubes in lava BEFORE other checks
        if (posIsLava || stateBelow.is(Blocks.LAVA)) {
            if (type == EntityType.STRIDER || type == EntityType.MAGMA_CUBE) {
                return true; 
            }
            return false; // Other land mobs cannot spawn in lava
        }

        // [FIX] Generic Height Check: Ensure the entire mob height is clear of solid blocks
        if (!isAquaticType(type)) {
            AABB bb = type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            for (BlockPos p : BlockPos.betweenClosed(
                    (int)Math.floor(bb.minX), (int)Math.floor(bb.minY), (int)Math.floor(bb.minZ),
                    (int)Math.floor(bb.maxX), (int)Math.floor(bb.maxY), (int)Math.floor(bb.maxZ))) {
                BlockState bs = world.getBlockState(p);
                // Allow water for aquatic-ish mobs if needed, but here we are in the land-check branch
                if (bs.isSolid()) return false;
                if (posIsWater && !isBoatType(type)) return false;
            }
        }
        
        // Ground Check: Must have a solid base (No air, no water) unless flying
        if (!isFlyingType(type) && (stateBelow.isAir() || stateBelow.is(Blocks.WATER))) {
            if (stateBelow.is(Blocks.WATER) && isBoatType(type)) {
                // Boats can spawn on water
            } else {
                return false;
            }
        }
        
        // Habitat specific checks (Prevent leaf spawns to avoid spawning in trees)
        // Note: LOGS are intentionally allowed so mobs can spawn on fallen trees (e.g. Spruce logs in Old Growth Taiga)
        if (stateBelow.is(BlockTags.LEAVES)) return false;
        
        return true;
    }
    
    private static boolean isBoatType(EntityType<?> type) {
        return type == EntityType.OAK_BOAT || type == EntityType.OAK_CHEST_BOAT ||
               type == EntityType.SPRUCE_BOAT || type == EntityType.SPRUCE_CHEST_BOAT ||
               type == EntityType.BIRCH_BOAT || type == EntityType.BIRCH_CHEST_BOAT ||
               type == EntityType.JUNGLE_BOAT || type == EntityType.JUNGLE_CHEST_BOAT ||
               type == EntityType.ACACIA_BOAT || type == EntityType.ACACIA_CHEST_BOAT ||
               type == EntityType.CHERRY_BOAT || type == EntityType.CHERRY_CHEST_BOAT ||
               type == EntityType.DARK_OAK_BOAT || type == EntityType.DARK_OAK_CHEST_BOAT ||
               type == EntityType.MANGROVE_BOAT || type == EntityType.MANGROVE_CHEST_BOAT ||
               type == EntityType.BAMBOO_RAFT || type == EntityType.BAMBOO_CHEST_RAFT;
    }

    private static boolean isOverworldSurfaceAnimal(EntityType<?> type) {
        return type == EntityType.PIG || type == EntityType.COW || type == EntityType.SHEEP || 
               type == EntityType.CHICKEN || type == EntityType.MULE || type == EntityType.HORSE || 
               type == EntityType.RABBIT || type == EntityType.LLAMA || type == EntityType.DONKEY || 
               type == EntityType.WOLF || type == EntityType.FOX || type == EntityType.CAT || 
               type == EntityType.OCELOT || type == EntityType.PARROT || type == EntityType.PANDA || 
               type == EntityType.POLAR_BEAR || type == EntityType.BEE || type == EntityType.GOAT || 
               type == EntityType.MOOSHROOM || type == EntityType.TURTLE || type == EntityType.CAMEL || 
               type == EntityType.SNIFFER || type == EntityType.ARMADILLO || type == EntityType.FROG || 
               type == EntityType.TRADER_LLAMA;
    }

    private static boolean isAquaticType(EntityType<?> type) {
        return type == EntityType.COD || type == EntityType.SALMON || type == EntityType.SQUID || 
               type == EntityType.GLOW_SQUID || type == EntityType.DOLPHIN || type == EntityType.PUFFERFISH || 
               type == EntityType.TROPICAL_FISH || type == EntityType.AXOLOTL || type == EntityType.TADPOLE;
    }

    private static boolean isMonsterType(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.CREEPER || 
               type == EntityType.SPIDER || type == EntityType.ENDERMAN || type == EntityType.WITCH || 
               type == EntityType.SLIME || type == EntityType.DROWNED || type == EntityType.STRAY || 
               type == EntityType.HUSK || type == EntityType.PHANTOM || type == EntityType.GUARDIAN || 
               type == EntityType.CAVE_SPIDER || type == EntityType.SILVERFISH || type == EntityType.BOGGED ||
               type == EntityType.GHAST || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.PIGLIN ||
               type == EntityType.HOGLIN || type == EntityType.BLAZE || type == EntityType.WITHER_SKELETON ||
               type == EntityType.MAGMA_CUBE;
    }

    private static boolean isFlyingType(EntityType<?> type) {
        return type == EntityType.GHAST || type == EntityType.PHANTOM || type == EntityType.ALLAY || 
               type == EntityType.BAT || type == EntityType.BEE || type == EntityType.VEX;
    }

    private static final List<EntityType<?>> BOATS = List.of(
        EntityType.OAK_BOAT, EntityType.SPRUCE_BOAT, EntityType.BIRCH_BOAT, 
        EntityType.JUNGLE_BOAT, EntityType.ACACIA_BOAT, EntityType.CHERRY_BOAT, 
        EntityType.DARK_OAK_BOAT, EntityType.MANGROVE_BOAT, EntityType.BAMBOO_RAFT
    );
    private static final List<EntityType<?>> CHEST_BOATS = List.of(
        EntityType.OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT, EntityType.BIRCH_CHEST_BOAT, 
        EntityType.JUNGLE_CHEST_BOAT, EntityType.ACACIA_CHEST_BOAT, EntityType.CHERRY_CHEST_BOAT, 
        EntityType.DARK_OAK_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.BAMBOO_CHEST_RAFT
    );

    private static WeightedList<MobSpawnSettings.SpawnerData> getCustomMiscSpawns(ServerLevel world, BlockPos pos) {
        WeightedList.Builder<MobSpawnSettings.SpawnerData> builder = WeightedList.builder();
        String dim = world.dimension().identifier().getPath();
        Holder<Biome> biome = world.getBiome(pos);
        String biomePath = biome.unwrapKey().isPresent() ? biome.unwrapKey().get().identifier().getPath() : "";
        
        if (dim.contains("nether")) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.CHEST_MINECART, 1, 1), 5);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 10);
            return builder.build();
        }
        
        if (dim.contains("end")) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ARMOR_STAND, 1, 1), 3);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 12);
            for (EntityType<?> t : CHEST_BOATS) builder.add(new MobSpawnSettings.SpawnerData(t, 1, 1), 1); 
            return builder.build();
        }

        if (biomePath.contains("beach") || biomePath.contains("river") || biomePath.contains("ocean")) {
            for (EntityType<?> t : BOATS) builder.add(new MobSpawnSettings.SpawnerData(t, 1, 1), 10);
            for (EntityType<?> t : CHEST_BOATS) builder.add(new MobSpawnSettings.SpawnerData(t, 1, 1), 6); 
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 4);
        } else if (biomePath.contains("desert")) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ARMOR_STAND, 1, 1), 5);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 5);
        } else if (biomePath.contains("jungle")) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ARMOR_STAND, 1, 1), 8);
            for (EntityType<?> t : CHEST_BOATS) builder.add(new MobSpawnSettings.SpawnerData(t, 1, 1), 2);
        } else if (biomePath.contains("swamp")) {
            for (EntityType<?> t : CHEST_BOATS) builder.add(new MobSpawnSettings.SpawnerData(t, 1, 1), 10);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 5);
        } else if (biomePath.contains("forest") || biomePath.contains("plains") || biomePath.contains("taiga")) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ARMOR_STAND, 1, 1), 10);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 8);
        } else {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 5);
        }
        
        if (pos.getY() < world.getSeaLevel() + ResourceCraftConfig.seaLevelAdjustment) {
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.MINECART, 1, 1), 10);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.CHEST_MINECART, 1, 1), 5);
            builder.add(new MobSpawnSettings.SpawnerData(EntityType.ITEM, 1, 1), 5);
        }
        
        return builder.build();
    }

    private static boolean isValidSpawn(ServerLevel world, BlockPos pos, EntityType<?> type, RandomSource randomSource) {
        // [FIX] Trial Dungeon / Structure Suffocation: Ensure spawn point is air and has enough vertical space
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }

        if (!SpawnPlacements.isSpawnPositionOk(type, world, pos)) {
             // Bypass for Striders in lava or custom MISC in air
             if (type == EntityType.STRIDER && world.getFluidState(pos).is(FluidTags.LAVA)) {
                 // Allow
             } else if (type.getCategory() == MobCategory.MISC || type == EntityType.PHANTOM) {
                 if (!(world.getBlockState(pos.below()).isSolid() || world.getBlockState(pos.below()).is(Blocks.WATER) || isFlyingType(type))) return false;
             } else {
                 return false;
             }
        }
        
        // Final Collision Check: Ensure the mob doesn't suffocate in the ceiling
        AABB bb = type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (!world.noCollision(bb)) {
            return false;
        }

        return SpawnPlacements.checkSpawnRules(type, world, EntitySpawnReason.NATURAL, pos, randomSource);
    }

    private static WeightedList<MobSpawnSettings.SpawnerData> getSpawnsAt(ServerLevel world, BlockPos pos, MobCategory category) {
        WeightedList<MobSpawnSettings.SpawnerData> biomeSpawns = world.getBiome(pos).value().getMobSettings().getMobs(category);

        // Inject Phantoms into Overworld Monster spawns
        if (category == MobCategory.MONSTER && world.dimension() == Level.OVERWORLD) {
            WeightedList.Builder<MobSpawnSettings.SpawnerData> builder = WeightedList.builder();
            List<Weighted<MobSpawnSettings.SpawnerData>> bEntries = getWeightedEntries(biomeSpawns);
            boolean hasPhantom = false;
            for (Weighted<MobSpawnSettings.SpawnerData> w : bEntries) {
                builder.add(w.value(), w.weight());
                if (w.value().type() == EntityType.PHANTOM) hasPhantom = true;
            }
            if (!hasPhantom) {
                builder.add(new MobSpawnSettings.SpawnerData(EntityType.PHANTOM, 1, 1), (int)ResourceCraftConfig.phantomWeight);
                biomeSpawns = builder.build();
            }
        }
        
        // Use getStructureWithPieceAt to detect structure regions
        StructureStart start = world.structureManager().getStructureWithPieceAt(pos, (s) -> true);
        if (start != null && start.isValid()) {
            Structure structure = start.getStructure();
            Map<MobCategory, StructureSpawnOverride> overrides = structure.spawnOverrides();
            if (overrides.containsKey(category)) {
                StructureSpawnOverride override = overrides.get(category);
                
                // Piece check for "tight" structures
                boolean inPiece = true;
                if (override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE) {
                    inPiece = world.structureManager().getStructureWithPieceAt(pos, (s) -> s == structure) != null;
                }
                
                if (inPiece) {
                    return override.spawns();
                }
            }
        }
        return biomeSpawns;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<Weighted<T>> getWeightedEntries(WeightedList<T> list) {
        try {
            // Find the first List field in WeightedList (usually "entries" or similar)
            for (Field field : WeightedList.class.getDeclaredFields()) {
                if (field.getType() == List.class) {
                    field.setAccessible(true);
                    return (List<Weighted<T>>) field.get(list);
                }
            }
        } catch (Exception e) {
            ResourceCraft.LOGGER.error("ResourceCraft | Failed to extract weighted entries from pool", e);
        }
        return new ArrayList<>();
    }
}
