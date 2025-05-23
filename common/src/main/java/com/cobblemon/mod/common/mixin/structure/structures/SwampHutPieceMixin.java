package com.cobblemon.mod.common.mixin.structure.structures;

import com.cobblemon.mod.common.Cobblemon;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SwampHutPiece.class)
public abstract class SwampHutPieceMixin {
  @WrapOperation(
    method = "postProcess",
    at = @At(
      value = "INVOKE",
      target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;isInside(Lnet/minecraft/core/Vec3i;)Z"
    )) // Prevent witch spawn by making box.isInside return false
  boolean cobblemon$cancelWitchSpawn(BoundingBox box, Vec3i vector, Operation<Boolean> original) {
    return !Cobblemon.spawnConfig.isBlacklisted(EntityType.WITCH) && original.call(box, vector);
  }

  @WrapOperation(method = "postProcess",
    at = @At(
      value = "INVOKE",
      target = "Lnet/minecraft/world/level/levelgen/structure/structures/SwampHutPiece;spawnCat(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V"
    )) // Prevent cat spawn by calling nothing
  void cobblemon$cancelWitchCatSpawn(SwampHutPiece piece, ServerLevelAccessor level, BoundingBox box, Operation<Void> original) {
    if (Cobblemon.spawnConfig.isBlacklisted(EntityType.CAT)) return;
    original.call(piece, level, box);
  }
}
