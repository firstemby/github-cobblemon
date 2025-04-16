/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour

import com.cobblemon.mod.common.api.net.Decodable
import com.cobblemon.mod.common.api.net.Encodable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/**
 * Represents static settings of a riding behaviour.
 * Values in this class are intended to be constant and not change during the riding process.
 * Typically this will be initialized for each pokemon form during deserialization
 * to determine how they should ride.
 *
 * @author landonjw
 */
interface RidingBehaviourSettings: Encodable, Decodable {
    val key: ResourceLocation
}
