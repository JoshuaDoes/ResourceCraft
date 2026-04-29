package com.resourcecraft.mixin;

import com.resourcecraft.GlobalResourceState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerMixin {

    @Inject(method = "trySummonWarden", at = @At("HEAD"), cancellable = true)
    private void onTrySummonWarden(ServerLevel world, CallbackInfoReturnable<Boolean> cir) {
        SculkShriekerBlockEntity shrieker = (SculkShriekerBlockEntity) (Object) this;
        BlockPos pos = shrieker.getBlockPos();
        
        long cityId = getAncientCityId(world, pos);
        if (cityId != -1) {
            GlobalResourceState state = GlobalResourceState.get(world);
            if (state.hasWardenSpawnedIn(cityId)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "trySummonWarden", at = @At("RETURN"))
    private void afterTrySummonWarden(ServerLevel world, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            SculkShriekerBlockEntity shrieker = (SculkShriekerBlockEntity) (Object) this;
            BlockPos pos = shrieker.getBlockPos();
            
            long cityId = getAncientCityId(world, pos);
            if (cityId != -1) {
                GlobalResourceState state = GlobalResourceState.get(world);
                state.markWardenSpawned(cityId);
            }
        }
    }

    private long getAncientCityId(ServerLevel world, BlockPos pos) {
        // Find the Ancient City structure at the shrieker's position
        Holder<Structure> cityHolder = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(BuiltinStructures.ANCIENT_CITY);
        
        StructureStart start = world.structureManager().getStructureWithPieceAt(pos, cityHolder.value());
        if (start != null && start.isValid()) {
            // In 26.1.2, ChunkPos.asLong() is replaced by ChunkPos.pack()
            return start.getChunkPos().pack();
        }
        return -1;
    }
}
