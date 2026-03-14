/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.cobblemon.mod.common.api.breeding.rule.layer.CompatibilityBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MoveBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.toProperties

object CobblemonBreeding {
    fun getOffspring(parent1: Pokemon, parent2: Pokemon): Pokemon? {
        if (!getCompatible(parent1, parent2)) {
            return null
        }

        val mother = getMother(parent1, parent2)
            ?: return null
        val father = if (mother == parent1) parent2 else parent1
        val offspringSpecies = getOffspringSpecies(mother, father)
        val formModifier = getFormModifier(mother, father, offspringSpecies)
        val offspring = formModifier.copy().also { it.species = offspringSpecies.resourceIdentifier.toString() }.create()
        val nature = getOffspringNature(mother, father)
        val ivs = getOffspringIVs(mother, father)
        val moves = getOffspringMoves(mother, father, offspring)

        offspring.nature = nature ?: offspring.nature
        offspring.ivs = ivs ?: offspring.ivs
        // TODO moves logic
        // TODO other logic

        return offspring
    }

    fun getCompatible(parent1: Pokemon, parent2: Pokemon): Boolean {
        val compatibilityRules = BreedingRules.compatibilityRules
        var compatible = true
        val input = CompatibilityBreedingRule.Input(BreedingRule.breedingRuntime, parent1, parent2)
        input.addToStruct(input.runtime.environment.query)
        for (rule in compatibilityRules) {
            compatible = rule.getCompatible(input, compatible)
        }
        return compatible
    }

    fun getMother(parent1: Pokemon, parent2: Pokemon): Pokemon? {
        val motherRules = BreedingRules.motherRules
        var mother: Pokemon? = null
        val input = MotherBreedingRule.Input(BreedingRule.breedingRuntime, parent1, parent2)
        input.addToStruct(input.runtime.environment.query)
        for (rule in motherRules) {
            mother = rule.getMother(input, mother)
        }
        return mother
    }

    fun getOffspringSpecies(mother: Pokemon, father: Pokemon): Species {
        val speciesRules = BreedingRules.offspringSpeciesRules
        var offspringSpecies: Species? = null
        val input = SpeciesBreedingRule.Input(BreedingRule.breedingRuntime, mother = mother, father = father)
        input.addToStruct(input.runtime.environment.query)
        for (rule in speciesRules) {
            offspringSpecies = rule.getOffspringSpecies(input, offspringSpecies)
        }
        return offspringSpecies ?: mother.species
    }

    fun getFormModifier(mother: Pokemon, father: Pokemon, offspringSpecies: Species): PokemonProperties {
        val formRules = BreedingRules.formInheritanceRules
        var modifier = ""
        val input = FormBreedingRule.Input(BreedingRule.breedingRuntime, mother, father, offspringSpecies)
        input.addToStruct(input.runtime.environment.query)
        for (rule in formRules) {
            modifier = rule.getOffspringFormModifier(input, modifier.toProperties()).asString()
        }
        return modifier.toProperties()
    }

    fun getOffspringNature(mother: Pokemon, father: Pokemon): Nature? {
        val natureRules = BreedingRules.natureInheritanceRules
        var nature: Nature? = null
        val input = SpeciesBreedingRule.Input(BreedingRule.breedingRuntime, mother, father)
        input.addToStruct(input.runtime.environment.query)
        for (rule in natureRules) {
            nature = rule.getOffspringNature(input, nature)
        }
        return nature
    }

    fun getOffspringIVs(mother: Pokemon, father: Pokemon): IVs? {
        val ivRules = BreedingRules.ivInheritanceRules
        var ivs: IVs? = null
        val input = SpeciesBreedingRule.Input(BreedingRule.breedingRuntime, mother, father)
        input.addToStruct(input.runtime.environment.query)
        for (rule in ivRules) {
            ivs = rule.getOffspringIVs(input, ivs)
        }
        return ivs
    }

    fun getOffspringMoves(mother: Pokemon, father: Pokemon, offspring: Pokemon): MoveSet? {
        val moveRules = BreedingRules.moveInheritanceRules
        var moveSet: MoveSet? = null
        val input = MoveBreedingRule.Input(BreedingRule.breedingRuntime, mother, father, offspring)
        input.addToStruct(input.runtime.environment.query)
        for (rule in moveRules) {
            rule.apply(input)
        }
        return moveSet
    }
}