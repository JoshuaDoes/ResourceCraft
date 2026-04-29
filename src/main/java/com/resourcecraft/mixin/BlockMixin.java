package com.resourcecraft.mixin;

import com.resourcecraft.SpawnerExt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {

    /**
     * When a block is broken by a player, check if it's a spawner and release one more mob.
     */
    @Inject(method = "playerDestroy", at = @At("HEAD"))
    private void onPlayerDestroy(Level world, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack stack, CallbackInfo ci) {
        if (world instanceof ServerLevel serverLevel && be instanceof SpawnerBlockEntity spawnerBE) {
            // Trigger one last spawn by forcing the spawner to tick once more
            SpawnerExt ext = (SpawnerExt) spawnerBE.getSpawner();
            ext.setForceSpawn(true);
            
            // We must bypass the delay and the "spawnedOnce" check to force a final spawn
            // We set the internal spawnDelay to 0 to ensure the next serverTick results in a spawn.
            // Note: We use a custom method or access the field if possible. 
            // Since we don't have an accessor yet, we rely on the fact that serverTick will run once.
            // But we need it to spawn NOW.
            
            spawnerBE.getSpawner().serverTick(serverLevel, pos);
            ext.setForceSpawn(false);
        }
    }
}
