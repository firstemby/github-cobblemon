/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.CobblemonMechanics
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.item.battle.BagItem
import com.cobblemon.mod.common.util.genericRuntime
import com.cobblemon.mod.common.util.resolveInt
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Registry for mapping item IDs to BagItem implementations for NPC usage.
 * NPCs don't have actual inventories, so this provides virtual BagItem instances
 * based on item resource locations.
 *
 * @author Amo Aster
 * @since December 2025
 */
object BagItemRegistry {
    private val itemMappings = mutableMapOf<String, BagItem>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        // Potions
        register("cobblemon:potion", createHealingItem("item.cobblemon.potion") {
            genericRuntime.resolveInt(CobblemonMechanics.potions.potionRestoreAmount, it)
        })
        register("cobblemon:super_potion", createHealingItem("item.cobblemon.super_potion") {
            genericRuntime.resolveInt(CobblemonMechanics.potions.superPotionRestoreAmount, it)
        })
        register("cobblemon:hyper_potion", createHealingItem("item.cobblemon.hyper_potion") {
            genericRuntime.resolveInt(CobblemonMechanics.potions.hyperPotionRestoreAmount, it)
        })
        register("cobblemon:max_potion", createHealingItem("item.cobblemon.max_potion") {
            it.maxHealth - it.health
        })
        register("cobblemon:full_restore", object : BagItem {
            override val itemName = "item.cobblemon.full_restore"
            override val returnItem = Items.GLASS_BOTTLE
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =
                target.health < target.maxHealth && target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "full_restore"
        })

        // Revives
        register("cobblemon:revive", createReviveItem("item.cobblemon.revive", false))
        register("cobblemon:max_revive", createReviveItem("item.cobblemon.max_revive", true))

        // Status cures
        register("cobblemon:antidote", createStatusCureItem("item.cobblemon.antidote", "psn"))
        register("cobblemon:paralyze_heal", createStatusCureItem("item.cobblemon.paralyze_heal", "par"))
        register("cobblemon:burn_heal", createStatusCureItem("item.cobblemon.burn_heal", "brn"))
        register("cobblemon:ice_heal", createStatusCureItem("item.cobblemon.ice_heal", "frz"))
        register("cobblemon:awakening", createStatusCureItem("item.cobblemon.awakening", "slp"))
        register("cobblemon:full_heal", createStatusCureItem("item.cobblemon.full_heal", "any"))

        // PP restore
        register("cobblemon:ether", createPPRestoreItem("item.cobblemon.ether", 10, false))
        register("cobblemon:max_ether", createPPRestoreItem("item.cobblemon.max_ether", 999, false))
        register("cobblemon:elixir", createPPRestoreItem("item.cobblemon.elixir", 10, true))
        register("cobblemon:max_elixir", createPPRestoreItem("item.cobblemon.max_elixir", 999, true))

        // X Items (stat boosters)
        register("cobblemon:x_attack", createXItem("item.cobblemon.x_attack", "atk"))
        register("cobblemon:x_defense", createXItem("item.cobblemon.x_defense", "def"))
        register("cobblemon:x_sp_atk", createXItem("item.cobblemon.x_sp_atk", "spa"))
        register("cobblemon:x_sp_def", createXItem("item.cobblemon.x_sp_def", "spd"))
        register("cobblemon:x_speed", createXItem("item.cobblemon.x_speed", "spe"))
        register("cobblemon:x_accuracy", createXItem("item.cobblemon.x_accuracy", "accuracy"))
        register("cobblemon:dire_hit", object : BagItem {
            override val itemName = "item.cobblemon.dire_hit"
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "direhit"
        })
        register("cobblemon:guard_spec", object : BagItem {
            override val itemName = "item.cobblemon.guard_spec"
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "guardspec"
        })
    }

    private fun createHealingItem(name: String, healAmount: (BattlePokemon) -> Int): BagItem {
        return object : BagItem {
            override val itemName = name
            override val returnItem = Items.GLASS_BOTTLE
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =
                target.health < target.maxHealth && target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) =
                "potion ${healAmount(battlePokemon)}"
        }
    }

    private fun createReviveItem(name: String, max: Boolean): BagItem {
        return object : BagItem {
            override val itemName = name
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health <= 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) =
                "revive ${if (max) "1" else "0.5"}"
        }
    }

    private fun createStatusCureItem(name: String, status: String): BagItem {
        return object : BagItem {
            override val itemName = name
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon): Boolean {
                if (target.health <= 0) return false
                val pokemon = target.originalPokemon
                return when (status) {
                    "any" -> pokemon.status != null
                    "psn" -> pokemon.status?.status?.showdownName == "psn" || pokemon.status?.status?.showdownName == "tox"
                    else -> pokemon.status?.status?.showdownName == status
                }
            }
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) =
                "cure $status"
        }
    }

    private fun createPPRestoreItem(name: String, amount: Int, allMoves: Boolean): BagItem {
        return object : BagItem {
            override val itemName = name
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) =
                "pp ${if (allMoves) "all" else "one"} $amount"
        }
    }

    private fun createXItem(name: String, stat: String): BagItem {
        return object : BagItem {
            override val itemName = name
            override val returnItem = Items.AIR
            override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) = target.health > 0
            override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) =
                "xstat $stat"
        }
    }

    /**
     * Registers a BagItem implementation for a specific item ID.
     *
     * @param itemId The item resource location (e.g., "cobblemon:potion")
     * @param bagItem The BagItem implementation
     */
    fun register(itemId: String, bagItem: BagItem) {
        itemMappings[itemId] = bagItem
    }

    /**
     * Gets the BagItem for a given item ID.
     *
     * @param itemId The item resource location
     * @return The BagItem implementation, or null if not registered
     */
    fun getBagItem(itemId: String): BagItem? {
        return itemMappings[itemId]
    }

    /**
     * Checks if an item ID is registered.
     */
    fun isRegistered(itemId: String): Boolean {
        return itemMappings.containsKey(itemId)
    }

    /**
     * Gets all registered item IDs.
     */
    fun getAllItemIds(): Set<String> {
        return itemMappings.keys.toSet()
    }
}
