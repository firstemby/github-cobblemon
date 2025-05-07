package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.OwnerQueryRequirement
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.hasKeyItem
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import kotlin.collections.iterator

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