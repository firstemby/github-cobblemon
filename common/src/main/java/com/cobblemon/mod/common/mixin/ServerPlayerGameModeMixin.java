/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.block.habitat.HabitatBlock;
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Shadow @Final protected ServerPlayer player;

    @ModifyVariable(
            method = "destroyBlock",
            at = @At(
                    value = "STORE"
            ),
            name = "bl2")
    private boolean habitatCorrectTool(boolean bl2, @Local(name = "blockState2") BlockState blockState2, @Local(name = "pos") BlockPos pos, @Local(name = "blockEntity") BlockEntity blockEntity) {
        if (blockState2.getBlock() instanceof HabitatBlock && blockEntity instanceof HabitatBlockEntity) {
            BlockState mimicState = ((HabitatBlockEntity) blockEntity).getMimickedState();
            return player.hasCorrectToolForDrops(mimicState);
        }
        return bl2;
    }
}
