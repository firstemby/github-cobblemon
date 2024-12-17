/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.entity.ai.LookInDirectionTask
import com.cobblemon.mod.common.util.asExpression
import net.minecraft.world.entity.LivingEntity

class LookInDirectionTaskConfig : SingleTaskConfig {
    var yaw: Expression = "0".asExpression()
    var pitch: Expression = "0".asExpression()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ) = LookInDirectionTask(yaw, pitch)
}