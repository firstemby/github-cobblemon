/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.advancement.criterion

import com.cobblemon.mod.common.block.TypeGemClusterBlock
import com.cobblemon.mod.common.block.TypeGemCoreBlock
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import java.util.*

class PlantTypeGemContext(val pos: BlockPos, val block: TypeGemClusterBlock)

class PlantTypeGemCriterion(
    playerCtx: Optional<ContextAwarePredicate>
): SimpleCriterionCondition<PlantTypeGemContext>(playerCtx) {

    companion object {
        val CODEC: Codec<PlantTypeGemCriterion> = RecordCodecBuilder.create { it.group(
            ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(PlantTypeGemCriterion::playerCtx)
        ).apply(it, ::PlantTypeGemCriterion) }
    }

    override fun matches(player: ServerPlayer, context: PlantTypeGemContext): Boolean {
        return player.level().getBlockState(context.pos).block is TypeGemCoreBlock
    }
}