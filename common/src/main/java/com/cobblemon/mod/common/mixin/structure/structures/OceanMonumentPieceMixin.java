package com.cobblemon.mod.common.mixin.structure.structures;

import com.cobblemon.mod.common.Cobblemon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OceanMonumentPieces.OceanMonumentPiece.class)
public abstract class OceanMonumentPieceMixin {
    @Inject(method = "spawnElder", at = @At("HEAD"), cancellable = true)
    void cobblemon$cancelElderGuardianSpawn(WorldGenLevel level, BoundingBox box, int x, int y, int z, CallbackInfo ci) {
        if (Cobblemon.spawnConfig.isBlacklisted(EntityType.ELDER_GUARDIAN)) ci.cancel();
    }
}
