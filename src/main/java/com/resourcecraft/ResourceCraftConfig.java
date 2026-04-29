package com.resourcecraft;

import eu.midnightdust.lib.config.MidnightConfig;

public class ResourceCraftConfig extends MidnightConfig {

    @Comment(category = "weights", centered = true) public static Comment animalsHeader;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float pig = 12.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float cow = 8.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float sheep = 12.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float chicken = 10.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float horse = 5.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float rabbit = 20.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float wolf = 8.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float fox = 8.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float bat = 5.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float squid = 10.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float glowSquid = 10.0f;

    @Comment(category = "weights", centered = true) public static Comment monstersOverworldHeader;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float zombie = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float skeleton = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float creeper = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float spider = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float enderman = 10.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float witch = 5.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float slime = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float phantom = 20.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float phantomWeight = 10.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float silverfish = 50.0f;

    @Comment(category = "weights", centered = true) public static Comment monstersNetherHeader;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float zombifiedPiglin = 30.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float piglin = 8.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float hoglin = 50.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float ghast = 60.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float magmaCube = 10.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float blaze = 80.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float witherSkeleton = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float strider = 60.0f;

    @Comment(category = "weights", centered = true) public static Comment monstersVariantsHeader;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float bogged = 80.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float husk = 80.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float stray = 80.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float drowned = 100.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float guardian = 2.0f;
    @Entry(category = "weights", min = 0.0f, max = 10000.0f) public static float caveSpider = 80.0f;

    @Comment(category = "gains", centered = true) public static Comment gainsHeader;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float surfaceMonster = 0.8f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float deepMonster = 0.3f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float surfaceWater = 1.5f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float deepWater = 0.5f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float village = 1.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float outpost = 1.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float fortress = 1.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float bastion = 1.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float ancientCity = 0.5f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float warpedForest = 0.5f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float endIsland = 1.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float swampHut = 2.0f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float trialChambers = 1.5f;
    @Entry(category = "gains", min = 0.0f, max = 100.0f) public static float zombieHorde = 4.0f;

    @Comment(category = "spread", centered = true) public static Comment spreadHeader;
    @Entry(category = "spread", min = 0.0f, max = 100.0f) public static float netherGlobal = 1.5f;
    @Entry(category = "spread", min = 0.0f, max = 100.0f) public static float endGlobal = 2.0f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float villageSpread = 0.8f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float outpostSpread = 0.3f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float fortressSpread = 0.3f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float bastionSpread = 0.5f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float ancientCitySpread = 0.5f;
    @Entry(category = "spread", min = 0.0f, max = 50.0f) public static float endIslandSpread = 0.5f;

    @Comment(category = "constraints", centered = true) public static Comment limitsHeader;
    @Entry(category = "constraints", min = 1, max = 512) public static int phantomMinOffset = 24;
    @Entry(category = "constraints", min = 1, max = 512) public static int phantomMaxOffset = 64;
    @Entry(category = "constraints") public static boolean witchAboveGroundOnly = true;
    @Entry(category = "constraints", min = 0, max = 100) public static int witchMax = 1;
    @Entry(category = "constraints", min = 0, max = 320) public static int netherCeilingLimit = 120;
    @Entry(category = "constraints", min = 0, max = 15) public static int surfaceSkyLight = 13;
    @Entry(category = "constraints", min = -128, max = 128) public static int seaLevelAdjustment = -10;
    @Entry(category = "constraints", min = 0, max = 512) public static int structureBaseCount = 4;
    @Entry(category = "constraints", min = 0, max = 512) public static int specialStructureBaseCount = 24;

    @Comment(category = "noise", centered = true) public static Comment noiseHeader;
    @Entry(category = "noise", min = 0.0f, max = 512.0f) public static float regionDecay = 48.0f;
    @Entry(category = "noise", min = 0.0f, max = 100.0f) public static float regionTarget = 5.0f;
    @Entry(category = "noise", min = 0.0f, max = 100.0f) public static float regionVariance = 7.0f;
    @Entry(category = "noise", min = 0.0f, max = 100.0f) public static float baseDensityFactor = 3.0f;
    @Entry(category = "noise", min = 0.0f, max = 100.0f) public static float rangeFactor = 5.0f;

    @Comment(category = "misc", centered = true) public static Comment globalHeader;
    @Entry(category = "misc") public static boolean deferredMigration = true;
    @Entry(category = "misc", min = 1, max = 1024) public static int migrationMin = 24;
    @Entry(category = "misc", min = 1, max = 1024) public static int migrationMax = 128;
    @Entry(category = "misc", min = 0, max = 10000) public static int gracePeriod = 100;
    @Entry(category = "misc", min = 1, max = 128) public static int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    @Entry(category = "misc") public static boolean debugLogs = false;
}
