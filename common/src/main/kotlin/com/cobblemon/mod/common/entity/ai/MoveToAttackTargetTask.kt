/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType

object MoveToAttackTargetTask {
    fun create(
        speedMultiplier: Expression = "0.5".asExpression(),
        closeEnoughDistance: Expression = "0".asExpression(),
        // Gap between the pokemon's hitbox and the target's before it sprints. At 0 it sprints until they touch
        sprintDistance: Expression = "0.0".asExpression()
    ): OneShot<LivingEntity> = BehaviorBuilder.create {
        it.group(
            it.present(MemoryModuleType.ATTACK_TARGET),
            it.registered(MemoryModuleType.WALK_TARGET)
        ).apply(it) { attackTarget, walkTarget ->
            Trigger { _, entity, _ ->
                mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                val speedMultiplier = mainThreadRuntime.resolveFloat(speedMultiplier)
                val closeEnoughDistance = mainThreadRuntime.resolveInt(closeEnoughDistance)
                val sprintDistance = mainThreadRuntime.resolveFloat(sprintDistance)

                val attackTarget = it.get(attackTarget)
                val position = attackTarget.position()
                val walkTarget = it.tryGet(walkTarget).orElse(null)
                if (walkTarget == null || walkTarget.target.currentPosition().distanceToSqr(position) > closeEnoughDistance) {
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        CobblemonWalkTarget(
                            target = EntityTracker(attackTarget, false),
                            speedModifier = speedMultiplier,
                            completionRange = closeEnoughDistance,
                            sprintDistance = sprintDistance,
                            stopDistance = 0F
                        )
                    )
                    true
                } else {
                    false
                }
            }
        }
    }
}
