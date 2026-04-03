/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup

/**
 * A breeding rule that takes place somewhere in the breeding logical stack.
 *
 * Breeding is divided into a number of distinct layers that are processed
 * sequentially. Each layer has a specific purpose, and there can be any number
 * of rules within a particular layer.
 *
 * For a list of the layers, what they do, and the Cobblemon-provided rules in
 * the layers, see `docs/breeding-rules.png` in the root of this repository.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface BreedingRule {
    companion object {
        val breedingRuntime = MoLangRuntime().setup()
    }

    val order: Int
}