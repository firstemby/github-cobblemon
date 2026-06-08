/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.util.cobblemonResource

/**
 * A basic [HabitatSpawn] that will be triggered via activated spawning in a habitat. Nothing to it, really.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
class ActivatedHabitatSpawn : HabitatSpawn() {
    companion object {
        val TYPE = cobblemonResource("activated")
    }
}