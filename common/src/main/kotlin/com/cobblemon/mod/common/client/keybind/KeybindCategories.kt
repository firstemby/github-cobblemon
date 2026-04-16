/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.keybind

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.KeyMapping

object KeybindCategories {
    val COBBLEMON_CATEGORY: KeyMapping.Category = KeyMapping.Category.register(cobblemonResource("categories.cobblemon"))
    val COBBLEMON_DEBUG_CATEGORY: KeyMapping.Category = KeyMapping.Category.register(cobblemonResource("categories.cobblemon.debug"))
}
