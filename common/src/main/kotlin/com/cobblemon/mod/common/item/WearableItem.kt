/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot

class WearableItem(val name: String): CobblemonItem(Properties().setId(ResourceKey.create(Registries.ITEM, cobblemonResource(name))).equippableUnswappable(EquipmentSlot.HEAD)) {
    companion object {
        const val MODEL_PATH = "item/wearable"
    }

    fun getModel3d(): Identifier = cobblemonResource("${MODEL_PATH}/${this.name}")
    fun getModel2d(): Identifier = cobblemonResource(this.name)
}
