/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.distraction

/* Unsure if I instantiate Distraction frequently or just have singletons. To be figured out. */

open class Distraction(val types: Set<DistractionType>) {
    constructor(vararg types: DistractionType) : this(types.toSet())

    val isForce = DistractionType.FORCE in types

    companion object {
        val FORCE = Distraction(DistractionType.FORCE)
    }
}