/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.util.asExpression

/**
 * This is used in the AI system to store optional additional information about the attack target,
 * mainly things like when to give up pursuit, what sort of attacks are permitted, whatever. It's
 * a temporary memory, same as ATTACK_TARGET, so relogs will clear it and that's why it has no codec.
 *
 * To help you get a sense of a use case, imagine a Pidgeotto has an AI task that says it should hunt
 * Magikarp that are close to the surface. Once they've found one and chosen them as the attack target,
 * they should give up the target if the Magikarp goes too deep, but if it's attacking an entity because
 * they attacked its owner, it shouldn't give up on the target for that reason. To address this, when
 * hunting Magikarp, the Pidgeotto will set this memory to a CobblemonAttackTargetData with some
 * [Expression] that encourages the entity to give up the target if the Magikarp goes too deep.
 *
 * To make this work, any transition from the FIGHT activity will erase this memory.
 *
 * @author Hiroku
 * @since June 23rd, 2025
 */
class CobblemonAttackTargetData(
    /** An expression that returns false when the target is no longer worth attacking. */
    val shouldContinue: Expression = "true".asExpression()
)