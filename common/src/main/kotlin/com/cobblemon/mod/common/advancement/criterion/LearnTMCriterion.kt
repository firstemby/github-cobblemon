/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.advancement.criterion

import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.advancements.critereon.EntityPredicate
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

class LearnTMContext(val tm: ResourceLocation)

class LearnTMCriterion(
    playerCtx: Optional<ContextAwarePredicate>,
    val tm: String
) : SimpleCriterionCondition<LearnTMContext>(playerCtx) {
    companion object {
        val CODEC: Codec<LearnTMCriterion> = RecordCodecBuilder.create { it.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LearnTMCriterion::playerCtx),
            Codec.STRING.optionalFieldOf("tm", "any").forGetter(LearnTMCriterion::tm)
        ).apply(it, ::LearnTMCriterion) }
    }

    override fun matches(player: ServerPlayer, context: LearnTMContext): Boolean {
        return tm == "any" || context.tm == tm.asIdentifierDefaultingNamespace()
    }
}

class LearnAllTMContext

class LearnAllTMCriterion(
    playerCtx: Optional<ContextAwarePredicate>
) : SimpleCriterionCondition<LearnAllTMContext>(playerCtx) {
    companion object {
        val CODEC: Codec<LearnAllTMCriterion> = RecordCodecBuilder.create { it.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LearnAllTMCriterion::playerCtx)
        ).apply(it, ::LearnAllTMCriterion) }
    }

    override fun matches(player: ServerPlayer, context: LearnAllTMContext) = true
}
