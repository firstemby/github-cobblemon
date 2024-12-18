/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

/**
 * Behavioural properties relating to a Pokémon's ability to look and move.
 *
 * @author Hiroku
 * @since July 30th, 2022
 */
class MoveBehaviour {
    val walk = WalkBehaviour()
    val swim = SwimBehaviour()
    val fly = FlyBehaviour()
    val stepHeight = 0.6F
    val wanderChance = 120
    val wanderSpeed = 1.0
    val canLook = true
    val looksAtEntities = true
    val hideInGrass = false
}