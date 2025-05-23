package com.cobblemon.mod.common.mixin.worldgen;

import com.cobblemon.mod.common.Cobblemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(BeehiveDecorator.class)
public abstract class BeeHiveDecoratorMixin {
  @WrapOperation(
    method = "place",
    at = @At(
      value = "INVOKE",
      target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"
    )
  )
  <T> void cobblemon$cancelBeeSpawn(Optional instance, Consumer<? super @NotNull T> action, Operation<Void> original) {
    if (Cobblemon.spawnConfig.isBlacklisted(EntityType.BEE)) return;
    original.call(instance, action);
  }

}
