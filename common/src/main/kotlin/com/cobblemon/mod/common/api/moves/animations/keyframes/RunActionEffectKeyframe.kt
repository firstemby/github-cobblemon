/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations.keyframes

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import java.util.concurrent.CompletableFuture
import net.minecraft.resources.ResourceLocation

/**
 * An action effect keyframe that simply runs another action effect based on an identifier. This action
 * effect is marked complete only when the action effect it runs is complete unless [waitForActionEffect] is
 * false.
 *
 * If [expression] is set, it will be resolved at runtime to determine the action effect identifier.
 * This allows dynamic delegation based on MoLang variables (e.g. v.granting_move).
 * If [expression] is not set, the static [actionEffect] identifier is used instead.
 *
 * @author Hiroku
 * @since January 21st, 2024
 */
class RunActionEffectKeyframe : ConditionalActionEffectKeyframe() {
    val actionEffect: ResourceLocation? = null
    val expression: ExpressionLike? = null
    val waitForActionEffect = true
    override fun playWhenTrue(context: ActionEffectContext): CompletableFuture<Unit> {
        val effectId = if (expression != null) {
            val resolved = expression.resolveString(context.runtime)
            if (resolved == "0" || resolved.isBlank()) {
                Cobblemon.LOGGER.warn("RunActionEffectKeyframe expression resolved to empty/zero string: $resolved")
                return skip()
            }
            try {
                ResourceLocation.parse(resolved)
            } catch (e: Exception) {
                Cobblemon.LOGGER.warn("RunActionEffectKeyframe expression resolved to invalid resource location: $resolved with exception: $e")
                return skip()
            }
        } else {
            actionEffect
        }
        return if (effectId != null) {
            ActionEffects.actionEffects[effectId]?.run(context)?.takeIf { waitForActionEffect } ?: skip()
        } else {
            Cobblemon.LOGGER.warn("RunActionEffectKeyframe has no action effect identifier or expression to run")
            skip()
        }
    }
}