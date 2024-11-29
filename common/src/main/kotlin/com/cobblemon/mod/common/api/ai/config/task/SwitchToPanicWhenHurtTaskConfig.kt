/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.schedule.Activity

class SwitchToPanicWhenHurtTaskConfig : SingleTaskConfig {
    var condition = "true".asExpression()
    var includePassiveDamage = false

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", (entity as? PosableEntity)?.struct ?: QueryStruct(hashMapOf()))
        if (!runtime.resolveBoolean(condition)) return null
        return if (includePassiveDamage) {
            BehaviorBuilder.create {
                it.group(it.present(MemoryModuleType.HURT_BY))
                    .apply(it) { _ ->
                        Trigger { world, entity, _ ->
                            (entity as? PathfinderMob)?.navigation?.stop()
                            entity.brain.setActiveActivityIfPossible(Activity.PANIC)
                            return@Trigger true
                        }
                    }
            }
        } else {
            BehaviorBuilder.create {
                it.group(it.present(MemoryModuleType.HURT_BY_ENTITY))
                    .apply(it) { _ ->
                        Trigger { world, entity, _ ->
                            (entity as? PathfinderMob)?.navigation?.stop()
                            entity.brain.setActiveActivityIfPossible(Activity.PANIC)
                            return@Trigger true
                        }
                    }
            }
        }
    }
}