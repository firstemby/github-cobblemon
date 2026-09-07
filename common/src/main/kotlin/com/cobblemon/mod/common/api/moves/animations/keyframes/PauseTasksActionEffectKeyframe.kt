/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.EntityProvider
import com.cobblemon.mod.common.api.moves.animations.NPCProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import java.util.concurrent.CompletableFuture
import net.minecraft.world.entity.LivingEntity

class PauseTasksActionEffectKeyframe : ActionEffectKeyframe {
    override fun play(context: ActionEffectContext): CompletableFuture<Unit> {
        val entities = context.providers
            .filterIsInstance<EntityProvider>()
            .filter { it is UsersProvider || it is NPCProvider }
            .flatMap { it.entities }

        for (entity in entities) {
            if (entity is LivingEntity) {
                entity.brain.setActiveActivityIfPossible(CobblemonActivities.ACTION_EFFECT)
            }
        }

        return CompletableFuture.completedFuture(Unit)
    }
}
