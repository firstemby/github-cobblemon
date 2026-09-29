/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.EntityProvider
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.scheduling.delayedFuture
import com.cobblemon.mod.common.util.asExpressionLike
import java.util.concurrent.CompletableFuture

class CameraFocusActionEffectKeyframe : ActionEffectKeyframe {
    enum class FocusSource {
        USERS,
        TARGETS,
        ENTITIES
    }

    var source: FocusSource = FocusSource.USERS
    var release: Boolean = false
    var duration: ExpressionLike = "0".asExpressionLike()

    override fun play(context: ActionEffectContext): CompletableFuture<Unit> {
        if (release) {
            CameraFocusRequests.release(context)
            return skip()
        }
        val battle = context.providers.filterIsInstance<PokemonBattle>().firstOrNull() ?: return skip()
        val sceneId = CinematicManager.sceneForBattle(battle.battleId) ?: return skip()
        val scene = CinematicManager.getScene(sceneId) ?: return skip()
        val entities = when (source) {
            FocusSource.USERS -> context.providers.filterIsInstance<UsersProvider>().flatMap { it.entities }
            FocusSource.TARGETS -> context.providers.filterIsInstance<TargetsProvider>().flatMap { it.entities }
            FocusSource.ENTITIES -> context.providers.filterIsInstance<EntityProvider>().flatMap { it.entities }
        }
        val entity = entities.firstOrNull() ?: return skip()
        val seconds = duration.resolveFloat(context.runtime)
        val effectiveSeconds = seconds.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_FOCUS_DURATION_SECONDS
        val focus = CameraFocusRequests.acquire(scene, context, entity, source == FocusSource.TARGETS) ?: return skip()
        try {
            delayedFuture(effectiveSeconds).whenComplete { _, _ -> focus.close() }
        } catch (error: Exception) {
            focus.close()
            throw error
        }
        return skip()
    }

    override fun interrupt(context: ActionEffectContext) {
        CameraFocusRequests.release(context)
    }

    companion object {
        private const val DEFAULT_FOCUS_DURATION_SECONDS: Float = 3.0f
    }
}
