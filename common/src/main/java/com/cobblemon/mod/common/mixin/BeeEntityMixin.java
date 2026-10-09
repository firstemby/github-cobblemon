/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;


@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal")
public abstract class BeeEntityMixin {

    @Shadow(aliases = {"this$0", "field_20373"}) @Final private Bee bee;

    // Grows bonemealable blocks vanilla's hardcoded checks miss, returning the state so it handles the rest.
    @ModifyVariable(method = "tick", at = @At(value = "LOAD", ordinal = 0), index = 5)
    private BlockState cobblemon$growBonemealableBlocks(BlockState grownState, @Local(ordinal = 0) BlockState blockState, @Local BlockPos pos) {
        if (grownState != null || blockState.is(Blocks.CAVE_VINES) || blockState.is(Blocks.CAVE_VINES_PLANT)) {
            return grownState;
        }

        if (!(blockState.getBlock() instanceof BonemealableBlock bonemealable)) {
            return null;
        }

        ServerLevel level = (ServerLevel) this.bee.level();
        if (!bonemealable.isValidBonemealTarget(level, pos, blockState)) {
            return null;
        }

        bonemealable.performBonemeal(level, this.bee.getRandom(), pos, blockState);
        return level.getBlockState(pos);
    }
}
