/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.advancement.criterion

import com.cobblemon.mod.common.block.TumblestoneBlock
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import java.util.*

class PlantTumblestoneContext(val pos: BlockPos, val block: TumblestoneBlock)

class PlantTumblestoneCriterion(
    playerCtx: Optional<ContextAwarePredicate>
): SimpleCriterionCondition<PlantTumblestoneContext>(playerCtx) {

    companion object {
        val CODEC: Codec<PlantTumblestoneCriterion> = RecordCodecBuilder.create { it.group(
            ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(PlantTumblestoneCriterion::playerCtx)
        ).apply(it, ::PlantTumblestoneCriterion) }
    }

    override fun matches(player: ServerPlayer, context: PlantTumblestoneContext): Boolean {
        return context.block.canGrow(player.level().getBlockState(context.pos), context.pos, player.level())
    }
}