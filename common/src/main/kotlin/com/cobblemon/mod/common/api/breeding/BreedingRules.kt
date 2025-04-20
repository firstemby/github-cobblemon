/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.breeding.BreedingRuleRegistrySyncPacket
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object BreedingRules : JsonDataRegistry<BreedingRule> {
    override val id = cobblemonResource("breeding_rules")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BreedingRules>()
    override val typeToken: TypeToken<BreedingRule> = TypeToken.get(BreedingRule::class.java)
    override val resourcePath = "breeding_rules"
    override val gson: Gson = GsonBuilder()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .setPrettyPrinting()
            .create()

    private val registry = mutableMapOf<ResourceLocation, BreedingRule>()

    override fun sync(player: ServerPlayer) {
        // Send the entire list of BreedingRule entries to the player
        BreedingRuleRegistrySyncPacket(this.registry.values.toList()).sendToPlayer(player)
    }

    override fun reload(data: Map<ResourceLocation, BreedingRule>) {
        // Clear existing data and load new data from JSON
        registry.clear()
        data.forEach { (id, breedingRule) ->
            registry[id] = breedingRule
        }
        println("Loaded Breeding Rules: ${registry.size}")
    }

    /**
     * Attempts to find a single BreedingRule that matches the given mother/father conditions.
     * Returns the first matching BreedingRule, or null if no match is found.
     */
    fun getBreedingRule(mother: Pokemon, father: Pokemon): BreedingRule? {
        // 1. Prepare the actual ParentRules objects for mother/father.
        val motherRules = createParentRules(mother)
        val fatherRules = createParentRules(father)

        // 2. Find all matching breeding rules in the registry.
        //    A rule matches if parent1rules & parent2rules match the mother/father conditions.
        //    If multiple rules match, we just return the first (or you could randomize).
        val matchingRules = registry.values.firstOrNull { rule ->
            rule.species != null && (
                    (checkParentRules(rule.parent1rules, motherRules) && checkParentRules(rule.parent2rules, fatherRules)) ||
                    (checkParentRules(rule.parent1rules, fatherRules) && checkParentRules(rule.parent2rules, motherRules))
            )
        }

        // 3. If none match, return null. Otherwise return the first match.
        return matchingRules
    }

    /**
     * Helper function to create a BreedingRule.ParentRules object from an actual Pokémon instance.
     * Adjust as needed if you'd like more robust checking (e.g. multiple moves, alt forms, etc.).
     */
    private fun createParentRules(parent: Pokemon): BreedingRule.ParentRules {
        // NOTE: Adjust these calls if your Pokemon class has different ways to get form/ability
        val speciesId = parent.species.name.lowercase()
        val formId = parent.form.name.lowercase()         // If you store form as a ResourceLocation
        val abilityId = parent.ability.name.lowercase()   // If you store ability as a ResourceLocation

        return BreedingRule.ParentRules(
                species = speciesId,
                form = formId,
                ability = abilityId,
                gender = parent.gender.name.lowercase(),
                hasMove = parent.moveSet.getMoves().firstOrNull()?.template?.name,
                hasItem = parent.heldItemNoCopy().item.let {
                    BuiltInRegistries.ITEM.getKey(it) // If empty, might be minecraft:air
                }
        )
    }

    /**
     * Check if a parent's actual rules match the expected parent rules from the data.
     */
    private fun checkParentRules(
            expected: BreedingRule.ParentRules,
            actual: BreedingRule.ParentRules
    ): Boolean {
        return (expected.species == null || expected.species == actual.species) &&
                (expected.form == null || expected.form == actual.form) &&
                (expected.ability == null || expected.ability == actual.ability) &&
                (expected.gender == null || expected.gender == actual.gender) &&
                (expected.hasMove == null || expected.hasMove == actual.hasMove) &&
                (expected.hasItem == null || expected.hasItem == actual.hasItem)
    }
}
