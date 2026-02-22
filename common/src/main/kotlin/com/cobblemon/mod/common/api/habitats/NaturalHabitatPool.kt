/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.spawning.SpawnBucket
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * A [HabitatPool] of [NaturalHabitatSpawn]s. Mainly just to help with deserialization and ensuring
 * a single type for everything that's applied to a habitat block.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
class NaturalHabitatPool : HabitatPool<NaturalHabitatSpawn>() {
    override val type: ResourceLocation = NaturalHabitatSpawn.TYPE
    override var spawns: List<NaturalHabitatSpawn> = listOf()

    companion object {
        fun default() = NaturalHabitatPool().apply {
            id = ResourceLocation("cobblemon", "custom__default_natural_pool")
            name = "Custom"
        }
    }

    override fun decode(buffer: RegistryFriendlyByteBuf, buckets: List<SpawnBucket>) {
        super.decode(buffer, buckets)
        spawns = buffer.readList { NaturalHabitatSpawn().apply { decode(buffer, buckets) } }
    }
}