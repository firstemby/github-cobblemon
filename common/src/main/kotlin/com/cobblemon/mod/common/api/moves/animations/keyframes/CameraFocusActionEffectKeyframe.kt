/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.EntityProvider
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.scheduling.delayedFuture
import com.cobblemon.mod.common.battles.stadium.StadiumBattleCameraCoordinator
import com.cobblemon.mod.common.battles.stadium.StadiumBattleCameraCoordinator.FocusRole
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

    /**
     * Blend duration for smooth camera transitions.
     *
     * When set to a value > 0, the camera will smoothly transition to the new focus
     * over this duration (in seconds) instead of snapping instantly.
     *
     * This helps prevent motion sickness by avoiding abrupt camera movements during battles.
     *
     * Recommended values:
     * - 0.0: Instant snap (use for quick reactions)
     * - 0.3-0.5: Fast but smooth transition (good for most moves)
     * - 0.8-1.0: Slow, cinematic transition (for dramatic moments)
     *
     * Note: The actual transition smoothness is also limited by MAX_ROTATION_SPEED_DEG_PER_SEC
     * in ClientCameraController to prevent motion sickness.
     */
    var blendDuration: ExpressionLike = "0.5".asExpressionLike()

    override fun play(context: ActionEffectContext): CompletableFuture<Unit> {
        val battle = context.providers.filterIsInstance<PokemonBattle>().firstOrNull() ?: return skip()

        if (release) {
            StadiumBattleCameraCoordinator.forceRelease(battle.battleId)
            return skip()
        }

        val entities = when (source) {
            FocusSource.USERS -> context.providers.filterIsInstance<UsersProvider>().flatMap { it.entities }
            FocusSource.TARGETS -> context.providers.filterIsInstance<TargetsProvider>().flatMap { it.entities }
            FocusSource.ENTITIES -> context.providers.filterIsInstance<EntityProvider>().flatMap { it.entities }
        }

        if (entities.isEmpty()) {
            return skip()
        }

        val focusRole = if (source == FocusSource.TARGETS) FocusRole.TARGET else FocusRole.USER
        val releaseHandle = StadiumBattleCameraCoordinator.startFocus(battle, entities, focusRole)
        val seconds = duration.resolveFloat(context.runtime)

        if (seconds > 0f && releaseHandle != null) {
            delayedFuture(seconds).whenComplete { _, _ -> releaseHandle.invoke() }
        }

        return CompletableFuture.completedFuture(Unit)
    }
}


