package com.resourcecraft.mixin;

import com.resourcecraft.ResourceCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

import net.minecraft.network.Connection;
import net.minecraft.server.network.CommonListenerCookie;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlaceNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        handlePlayerSpawn(player);
    }

    @Inject(method = "respawn", at = @At("RETURN"))
    private void onRespawn(ServerPlayer player, boolean alive, net.minecraft.world.entity.Entity.RemovalReason removalReason, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer newPlayer = cir.getReturnValue();
        ((EntityAccessor)newPlayer).getTags().remove("ResourceCraft_Spawned");
        handlePlayerSpawn(newPlayer);
    }

    private void handlePlayerSpawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel world)) {
            ResourceCraft.LOGGER.warn("ResourceCraft | [PLAYER_SPAWN] Level is not a ServerLevel for player {}!", player.getName().getString());
            return;
        }
        
        long worldSeed = world.getSeed();
        BlockPos playerPos = player.blockPosition();
        
        // Use a tag to ensure we only force the spawn once per life.
        // Also check if they are near 0,0 to avoid forcing players who already have a set spawn far away.
        boolean hasSpawnedThisLife = ((EntityAccessor)player).getTags().contains("ResourceCraft_Spawned");
        double distSqToOrigin = playerPos.getX() * playerPos.getX() + playerPos.getZ() * playerPos.getZ();
        
        if (!hasSpawnedThisLife && distSqToOrigin < 4096) { // Within 64 blocks of 0,0
            ((EntityAccessor)player).getTags().add("ResourceCraft_Spawned");
            // Force to 0,0
                int targetX = 0;
                int targetZ = 0;
                int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, targetX, targetZ);
                double finalY = (surfaceY > world.getMinY()) ? surfaceY : 64;

                long rotSeed = worldSeed ^ ((long)targetX << 32) ^ (long)targetZ;
                Random random = new Random(rotSeed);
                float yaw = random.nextFloat() * 360.0F;
                float pitch = (random.nextFloat() * 180.0F) - 90.0F;

                player.setPos(targetX, finalY, targetZ);
                player.setYRot(yaw);
                player.setXRot(pitch);
                if (player.connection != null) {
                    player.connection.teleport(targetX, finalY, targetZ, yaw, pitch);
                }

                ResourceCraft.LOGGER.info("ResourceCraft | [PLAYER_SPAWN] Intercepted join for {} at {} - Forced to x:{} y:{} z:{} yaw:{} pitch:{}", 
                    player.getName().getString(), playerPos, targetX, finalY, targetZ, yaw, pitch);
            }
    }
}
