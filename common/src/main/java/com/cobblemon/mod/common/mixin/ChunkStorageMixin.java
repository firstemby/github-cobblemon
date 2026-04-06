/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import static com.cobblemon.mod.common.datafixer.CobblemonSchemas.VERSION_KEY;

import com.cobblemon.mod.common.datafixer.CobblemonSchemas;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SimpleRegionStorage.class)
public class ChunkStorageMixin {
    //Modifies the NBT returned by the vanilla upgradeChunkTag to also incorporate cobblemon fixup stuff
    @ModifyExpressionValue(
        method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/NbtUtils;getDataVersion(Lnet/minecraft/nbt/CompoundTag;I)I"
        )
    )
    public int cobblemon$doChunkFix(
            int original,
            @Local(name = "compoundTag", argsOnly = true) CompoundTag compoundTag
    ) {
        int curVersion = compoundTag.getIntOr(VERSION_KEY, 0);
        return DataFixTypes.CHUNK.update(CobblemonSchemas.getDATA_FIXER(), compoundTag, curVersion, CobblemonSchemas.DATA_VERSION);
    }
}
