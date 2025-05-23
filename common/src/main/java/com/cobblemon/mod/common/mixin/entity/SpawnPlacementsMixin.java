/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.entity;

import com.cobblemon.mod.common.Cobblemon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public class SpawnPlacementsMixin {
  @Inject(method = "isSpawnPositionOk", at = @At("HEAD"), cancellable = true)
  // Cancel general mob spawn by returning false
  private static void cobblemon$cancelMobSpawns(EntityType<?> entityType, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (Cobblemon.spawnConfig.isBlacklisted(entityType)) cir.setReturnValue(false);
  }
}
