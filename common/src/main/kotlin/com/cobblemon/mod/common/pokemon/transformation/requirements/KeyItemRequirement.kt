/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.OwnerQueryRequirement
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.hasKeyItem
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * A [OwnerQueryRequirement] that checks if the player has a certain "key item". Key items are not real items,
 * but can be awarded (and removed) at free will of cobblemon or 3rd party mods.
 *
 * @property requiredKeyItem The key item the player needs to have, for example `cobblemon:z_ring` or `cobblemon:key_stone` for Z-Moves and Mega evolutions respectively
 *
 * @author Polymeta
 * @since May 6th, 2025
 */
class KeyItemRequirement(val requiredKeyItem: ResourceLocation) : OwnerQueryRequirement {
    override fun checkPlayer(pokemon: Pokemon, owner: ServerPlayer): Boolean {
        return owner.hasKeyItem(requiredKeyItem)
    }

    override fun checkNPC(pokemon: Pokemon, owner: NPCEntity): Boolean {
        //TODO some sort of config potentially on the NPC itself
        return true
    }

    companion object {
        const val ADAPTER_VARIANT = "key_item"
    }
}