package com.cobblemon.mod.common.mixin.block.entity;

import com.cobblemon.mod.common.Cobblemon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin {
  @Inject(method = "canRespond", at = @At("HEAD"), cancellable = true)
  // Cancel warden spawn by preventing the shrieker from activating
  void cobblemon$cancelWardenSpawn(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
    if (Cobblemon.spawnConfig.isBlacklisted(EntityType.WARDEN)) cir.setReturnValue(false);
  }
}
