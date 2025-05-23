package com.cobblemon.mod.common.mixin.block.entity;

import com.cobblemon.mod.common.Cobblemon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin {
  @Inject(method = "tryShriek", at = @At("HEAD"), cancellable = true)
  // Cancel warden spawn by preventing the shrieker from activating
  void cobblemon$cancelWardenSpawn(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
    if (Cobblemon.spawnConfig.isBlacklisted(EntityType.WARDEN)) ci.cancel();
  }
}
