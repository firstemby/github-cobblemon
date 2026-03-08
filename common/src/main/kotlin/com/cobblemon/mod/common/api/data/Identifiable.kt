/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.data

import net.minecraft.resources.Identifier

/**
 * A functional interface that resolves implementation from type to [Identifier].
 */
fun interface Identifiable {

    /**
     * Returns the [Identifier] attached to the implementation instance.
     *
     * @return The [Identifier] attached to the implementation instance.
     */
    fun id(): Identifier

}
