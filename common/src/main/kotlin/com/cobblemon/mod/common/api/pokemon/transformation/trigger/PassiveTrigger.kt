/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.trigger

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger

/**
 * Represents a [TransformationTrigger] of a [Pokemon] that is attempted periodically without any additional context or actions.
 * For the default implementation see [LevelUpTrigger].
 *
 * Triggers that implement this marker will be tested every second.
 *
 * @author Licious
 * @since March 19th, 2022
 */
interface PassiveTrigger : TransformationTrigger