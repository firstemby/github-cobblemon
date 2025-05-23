package com.cobblemon.mod.common.mixin.structure.structures;

import com.cobblemon.mod.common.Cobblemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(WoodlandMansionPieces.WoodlandMansionPiece.class)
public abstract class WoodlandMansionPieceMixin {
  @WrapOperation(
    method = "handleDataMarker",
    at = @At(
      value = "INVOKE",
      target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
    )) // Wraps calls to add to the list and ignores it if the mob to add is blacklisted
  boolean cobblemon$cancelMansionSpawns(List instance, Object entity, Operation<Boolean> original) {
    if (Cobblemon.spawnConfig.isBlacklisted(((Mob) entity).getType())) {
      return true;
    }
    return original.call(instance, entity);
  }
}
