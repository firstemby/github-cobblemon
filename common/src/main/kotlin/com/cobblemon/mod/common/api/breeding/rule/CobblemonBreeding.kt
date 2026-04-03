/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.breeding.rule.layer.EggBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.IVsBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.toProperties
import kotlin.random.Random
import net.minecraft.world.item.ItemStack

object CobblemonBreeding {
    fun createEgg(parent1: Pokemon, parent2: Pokemon, isSimulated: Boolean = false): ItemStack? {
        val offspring = getOffspring(parent1, parent2, isSimulated) ?: return null

        var hatchTime = 1F
        for (rule in BreedingRules.eggRules) {
            hatchTime = rule.getHatchTime(EggBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, parent1, parent2, offspring), hatchTime)
        }

        return offspring.createEgg(parent1, parent2, timeToHatch = hatchTime)
    }

    fun getOffspring(parent1: Pokemon, parent2: Pokemon, isSimulated: Boolean): Pokemon? {
        val mother = getMother(parent1, parent2, isSimulated)
            ?: return null
        val father = if (mother == parent1) parent2 else parent1
        val offspringSpecies = getOffspringSpecies(mother, father, isSimulated)
        val formModifier = getFormModifier(mother, father, offspringSpecies, isSimulated)
        val offspring = formModifier.copy().also { it.species = offspringSpecies.resourceIdentifier.toString() }.create()
        applyOffspringIVs(mother, father, offspring, isSimulated)
        applyOffspringMoves(mother, father, offspring, isSimulated)
        applyOffspringShiny(mother, father, offspring, isSimulated)
        applyPokemonModifications(mother, father, offspring, isSimulated)
        return offspring
    }

    /**
     * Gets the logical mother of the given pairing Pokémon, if it is a valid pairing. This can be used as a means
     * of checking if two Pokémon can breed together.
     */
    fun getMother(parent1: Pokemon, parent2: Pokemon, isSimulated: Boolean): Pokemon? {
        val motherRules = BreedingRules.motherRules
        var mother: Pokemon? = null
        val input = MotherBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, parent1, parent2)
        input.addToStruct(input.runtime.environment.query)
        for (rule in motherRules) {
            mother = rule.getMother(input, mother)
        }
        return mother
    }

    fun getOffspringSpecies(mother: Pokemon, father: Pokemon, isSimulated: Boolean): Species {
        val speciesRules = BreedingRules.offspringSpeciesRules
        var offspringSpecies: Species? = null
        val input = SpeciesBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother = mother, father = father)
        input.addToStruct(input.runtime.environment.query)
        for (rule in speciesRules) {
            offspringSpecies = rule.getOffspringSpecies(input, offspringSpecies)
        }
        return offspringSpecies ?: mother.species
    }

    fun getFormModifier(mother: Pokemon, father: Pokemon, offspringSpecies: Species, isSimulated: Boolean): PokemonProperties {
        val formRules = BreedingRules.formInheritanceRules
        var modifier = ""
        val input = FormBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother, father, offspringSpecies)
        input.addToStruct(input.runtime.environment.query)
        for (rule in formRules) {
            modifier = rule.getOffspringFormModifier(input, modifier.toProperties()).asString()
        }
        return modifier.toProperties()
    }

    fun applyOffspringIVs(mother: Pokemon, father: Pokemon, offspring: Pokemon, isSimulated: Boolean) {
        val ivRules = BreedingRules.ivInheritanceRules
        val input = PokemonBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother, father, offspring)
        val data = IVsBreedingRule.IVInheritanceData(ivs = IVs.createRandomIVs(0))
        input.addToStruct(input.runtime.environment.query)
        data.addToStruct(input.runtime.environment.query)
        for (rule in ivRules) {
            rule.calculateOffspringIVs(input, data)
        }
        offspring.ivs.copyFrom(data.ivs)
    }

    fun applyOffspringMoves(mother: Pokemon, father: Pokemon, offspring: Pokemon, isSimulated: Boolean) {
        val moveRules = BreedingRules.moveInheritanceRules
        val moveStack = mutableListOf<MutableList<MoveTemplate>>()
        val input = PokemonBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother, father, offspring)
        input.addToStruct(input.runtime.environment.query)
        for (rule in moveRules) {
            rule.apply(input, moveStack)
        }
    }

    fun applyOffspringShiny(mother: Pokemon, father: Pokemon, offspring: Pokemon, isSimulated: Boolean) {
        val shinyRules = BreedingRules.shinyRules
        val input = PokemonBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother, father, offspring)
        input.addToStruct(input.runtime.environment.query)
        var shinyRate = Cobblemon.config.shinyRate
        for (rule in shinyRules) {
            shinyRate = rule.getShinyChance(input, shinyRate)
        }
        offspring.shiny = shinyRate > 0 && Random.nextFloat() < 1F / shinyRate
    }

    fun applyPokemonModifications(mother: Pokemon, father: Pokemon, offspring: Pokemon, isSimulated: Boolean) {
        val pokemonRules = BreedingRules.pokemonRules
        val input = PokemonBreedingRule.Input(BreedingRule.breedingRuntime, isSimulated, mother, father, offspring)
        input.addToStruct(input.runtime.environment.query)
        for (rule in pokemonRules) {
            rule.apply(input)
        }
    }
}