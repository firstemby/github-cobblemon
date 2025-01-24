/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.breeding

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.abilities.Ability
import com.cobblemon.mod.common.api.abilities.AbilityPool
import com.cobblemon.mod.common.api.abilities.AbilityTemplate
import com.cobblemon.mod.common.api.breeding.BreedingRule
import com.cobblemon.mod.common.api.breeding.BreedingRules
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.pokemon.*
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbilityType
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.item.Item
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Holder
import kotlin.random.Random

interface BreedingLogic {

    /**
     * Takes two [Pokemon] and returns a [BreedingResult].
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return The [BreedingResult] of the two [Pokemon]
     * @author Apion, Nick, whatsy, plastered_crab
     */
    // TODO we may want to call this method "hatch" now since we store parents on the egg itself now
    fun breed(mother: Pokemon, father: Pokemon): BreedingResult {
        if (!this.canBreed(mother, father)) {
            return BreedingResult()
        }


        // todo check for compatability here (OT, species, etc)
        val compatible = checkCompatibility(mother, father)

        // Crab note: TODO Clean this shit up it is ugly af

        // find matching breeding rule
        val rule = BreedingRules.getBreedingRule(mother, father)

        // Suppose we already have a fallbackForm: FormData
        val fallbackForm: FormData = this.calculateForm(mother, father)

        val finalSpecies: Species = if (rule?.species != null) {
            PokemonSpecies.getByName(rule.species.lowercase()) ?: fallbackForm.species
        } else {
            fallbackForm.species
        }

        val finalForm: FormData = if (rule != null && rule.form != null) {
            // find the species by rule.species or fallback
            val speciesEntry = rule.species?.let { PokemonSpecies.getByName(it.lowercase()) } ?: fallbackForm.species
            speciesEntry.forms.find { formData ->
                // Compare ignoring case. formData.name might be "Normal", "Alolan", etc.
                formData.name.lowercase() == rule.form.lowercase()
            } ?: speciesEntry.standardForm
        } else {
            fallbackForm
        }

        // Calculate fallback IVs, then override stats from the rule if non-null
        val finalIVs = mergeIvs(rule, mother, father)

        // If the rule has a 'nature' field, use it, else fallback
        val finalNature: Nature = if (rule?.nature != null) {
            // If rule.nature = "adamant"
            Natures.getNature(rule.nature.lowercase()) ?: this.calculateNature(mother, father)
        } else {
            this.calculateNature(mother, father)
        }

        // TODO get the ability to actually apply
        // If the rule has an 'ability', use it, else fallback
        val finalAbility: Ability = if (rule?.ability != null) {
            Abilities.get(rule.ability.lowercase())?.create(true, Priority.HIGHEST) ?: this.calculateAbility(mother, father, finalForm)
        } else {
            this.calculateAbility(mother, father, finalForm)
        }

        // Handle moves from the rule. We read move1..move4 if present, turn them into MoveTemplates,
        // then pass them as `eggMoves` to our `calculateMoveset`.
        val ruleMoveTemplates: List<MoveTemplate> = if (rule != null) {
            listOfNotNull(
                    rule.moves.move1,
                    rule.moves.move2,
                    rule.moves.move3,
                    rule.moves.move4
            ).mapNotNull { moveString ->
                // e.g. "flare_blitz"
                Moves.getByName(moveString.lowercase())
            }
        } else {
            emptyList()
        }

        // Pass these move templates to `calculateMoveset` as the optional egg moves.
        val finalMoveSet = this.calculateMoveset(
                mother,
                father,
                finalForm ?: fallbackForm,
                eggMoves = ruleMoveTemplates
        )

        // Determine shiny randomly (or however your system does it)
        val isShiny = (Random.nextFloat() < 1f / Cobblemon.config.shinyRate)

        // If the rule has a 'gender' string, convert it to Gender; else fallback
        val finalGender: Gender = if (rule?.gender != null) {
            // e.g. "male" / "female"
            Gender.entries.find { it.name.equals(rule.gender, ignoreCase = true) }
                    ?: this.calculateGender(mother, father, finalForm)
        } else {
            this.calculateGender(mother, father, finalForm)
        }

        // If the rule has a 'pokeball', use it, else fallback
        val finalPokeball: PokeBall = if (rule?.pokeball != null) {
            PokeBalls.getPokeBall(cobblemonResource(rule.pokeball.lowercase())) ?: this.calculatePokeball(mother, father)
        } else {
            this.calculatePokeball(mother, father)
        }

        // create final egg from all this awful data
        val newPoke = EggPokemon(
                ivs = finalIVs,
                nature = finalNature,
                species = getPreEvoSpecies(finalSpecies),
                formData = finalForm,
                ability = finalAbility,
                moveSet = finalMoveSet,
                isShiny = isShiny,
                gender = finalGender,
                pokeball = finalPokeball
        )

        return BreedingResult(newPoke, calcTimeToHatch(mother, father))
    }


    private fun mergeIvs(
            rule: BreedingRule?,
            mother: Pokemon,
            father: Pokemon
    ): IVs {
        val fallbackIvs = this.calculateIVs(mother, father)
        if (rule == null) return fallbackIvs

        // 3. For each stat, if rule.ivs.<stat> is non-null, override fallback
        rule.ivs.hp?.let { fallbackIvs[Stats.HP] = it }
        rule.ivs.attack?.let { fallbackIvs[Stats.ATTACK] = it }
        rule.ivs.defence?.let { fallbackIvs[Stats.DEFENCE] = it }
        rule.ivs.special_attack?.let { fallbackIvs[Stats.SPECIAL_ATTACK] = it }
        rule.ivs.special_defence?.let { fallbackIvs[Stats.SPECIAL_DEFENCE] = it }
        rule.ivs.speed?.let { fallbackIvs[Stats.SPEED] = it }

        return fallbackIvs
    }

    /**
     * Determines if two [Pokemon] can breed.
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return Whether the two [Pokemon] can breed
     * @author Apion, whatsy, plastered_crab
     */
    fun canBreed(mother: Pokemon, father: Pokemon): Boolean {
        // check custom breeding rules first
        if (BreedingRules.getBreedingRule(mother, father) != null)
            return true

        // Check if either Pokémon is in the No Eggs Discovered egg group
        if (mother.form.eggGroups.contains(EggGroup.UNDISCOVERED) || father.form.eggGroups.contains(EggGroup.UNDISCOVERED))
            return false


        // Check if both Pokémon are Ditto
        if (mother.form.eggGroups.contains(EggGroup.DITTO) && father.form.eggGroups.contains(EggGroup.DITTO))
            return false


        // Check if at least one parent is Ditto
        val anyDitto = mother.form.eggGroups.contains(EggGroup.DITTO) || father.form.eggGroups.contains(EggGroup.DITTO)
        if (anyDitto)
            return true


        // Check for shared egg groups and opposite genders
        val sharedEggGroups = mother.form.eggGroups.intersect(father.form.eggGroups)
        return sharedEggGroups.isNotEmpty() && mother.gender != father.gender
    }


    /**
     * Determines the base pre-evolution of a [Pokemon] species.
     *
     * @param species The Species of [Pokemon]
     * @return The base pre-evolution Species of [Pokemon]
     * @author Plastered_Crab
     */
    private fun getPreEvoSpecies(species: Species): Species {
        var currentSpecies = species
        while (currentSpecies.preEvolution != null) {
            currentSpecies = currentSpecies.preEvolution!!.species
        }
        return currentSpecies
    }

    /**
     * Determines the [Gender] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @param form The [FormData] of the child
     * @return The [Gender] of the child
     * @author Apion, whatsy
     */
    fun calculateGender(mother: Pokemon, father: Pokemon, form: FormData): Gender {
        val maleRatio = form.maleRatio
        if (maleRatio.toDouble() == -1.0) {
            return Gender.GENDERLESS
        }
        val rand = Random.nextDouble(0.0, 1.0)
        return if (rand < maleRatio) {
            Gender.MALE
        } else {
            Gender.FEMALE
        }
    }

    /**
     * Determines the [FormData] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return The [FormData] of the child
     * @author Nick, Apion, whatsy
     */
    fun calculateForm(mother: Pokemon, father: Pokemon): FormData {
        /*
         * In most cases, if a hatched species has multiple forms (not dependent on in-battle conditions), it will
         * often inherit the form of the mother or non-Ditto parent. Burmy will always hatch with the same cloak as
         * its mother, and if bred between Mothim and Ditto, it will always hatch with a Plant Cloak. Rockruff with the
         * Ability Own Tempo, which evolves into Lycanroc's Dusk Form, is treated as a separate form and can pass down
         * this trait accordingly to its offspring. Oricorio, which changes its form using nectars, can also pass down
         * its form by breeding.
         *
         * There are a few exceptions to this inheritance pattern:
         *
         * - Rotom will always hatch into its normal form, but can be changed afterwards.
         * - Vivillon's pattern depends on the real-world, geographic area of the save file it originated from, regardless of its parent's pattern.
         * - The form that Scatterbug will evolve into is predetermined when the Egg is first obtained, not when hatched or evolved.
         * - Scatterbug hatched in Scarlet and Violet will only evolve into Fancy Pattern Vivillon.
         * - Furfrou, whose trims are temporary, always hatch in its Natural Form.
         * - Sinistea will always hatch as a Phony Form, even if its parent is an Antique Form.
         *
         * In addition, species with regional forms will hatch into whichever form is native to that region. However,
         * if a parent of a foreign form and the same family is holding an Everstone, the offspring will be of that
         * parent's form instead.
         *
         * The Gigantamax Factor, Alpha, Jumbo and Mini marks cannot be passed down by breeding.
         *
         * Pokémon such as Dunsparce, Wurmple, and Tandemaus, where the species and form they will evolve into is
         * predetermined when they are first generated, do not follow form inheritance rules. The forms of the parents
         * have no influence on the personality value or encryption constant.
         *
         * TODO - Handle this via form data parameters?
         */
        return nonDittoPreferMother(mother, father).form
    }

    /**
     * Determines the [Nature] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return The [Nature] of the child
     * @author Nick, whatsy
     */
    fun calculateNature(mother: Pokemon, father: Pokemon): Nature {
        val motherHasEverstone = mother.heldItemNoCopy().item.let { BuiltInRegistries.ITEM.wrapAsHolder(it) }.`is`(CobblemonItemTags.EVERSTONE)
        val fatherHasEverstone = father.heldItemNoCopy().item.let { BuiltInRegistries.ITEM.wrapAsHolder(it) }.`is`(CobblemonItemTags.EVERSTONE)

        return when {
            motherHasEverstone && fatherHasEverstone -> if (Random.nextInt(100) < 50) mother.nature else father.nature
            motherHasEverstone -> mother.nature
            fatherHasEverstone -> father.nature
            else -> Natures.getRandomNature()
        }
    }

    /**
     * Determines the [Ability] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @param child The [FormData] of the child
     * @return The [Ability] of the child
     * @author Nick, whatsy
     */
    fun calculateAbility(mother: Pokemon, father: Pokemon, child: FormData): Ability {
        val parent = this.nonDittoPreferMother(mother, father)
        val hidden = parent.form.abilities.filter { it.type == HiddenAbilityType }
            .map { it.template.name }
            .map { it == parent.ability.name }
            .firstOrNull()

        return if(hidden == true) {
            val rng = Random.nextInt(0, 100) < 60
            if(rng) {
                parent.ability
            } else {
                child.abilities.select(child.species, child.aspects.toSet()).first
            }
        } else {
            val rng = Random.nextInt(0, 100) < 80
            if(rng) {
                parent.ability
            } else {
                child.abilities.select(child.species, child.aspects.toSet()).first
            }
        }
    }

    /**
     * Determines the [MoveSet] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @form the form of the [Pokemon]
     * @return The [MoveSet] of the child
     * @author Nick, Apion, whatsy
     */
    // TODO: This, based off of https://pokemondb.net/mechanics/breeding
    fun calculateMoveset(mother: Pokemon, father: Pokemon, form: FormData, eggMoves: List<MoveTemplate>? = null): MoveSet {
        val babyLearnset = form.moves.levelUpMoves[1] ?: emptySet()
        val inheritedMoves = mutableListOf<MoveTemplate>()

        // Add Egg Moves using the JSON registry based on parent breeding rules
        //val eggMoves = BreedingRules.getEggMoveList(mother, father, babySpecies.resourceIdentifier)
        if (eggMoves != null) {
            inheritedMoves.addAll(eggMoves)
        }

        // Add TM/HM Moves from both mother and father
        val tmHmMoves = (mother.moveSet.getMoves() + father.moveSet.getMoves())
                .map { it.template }
                .filter { form.species.moves.tmMoves.contains(it) }
        inheritedMoves.addAll(tmHmMoves)

        // Add Inherited Level-Up Moves
        inheritedMoves.addAll(mother.moveSet.getMoves()
                .map { it.template }
                .filter { it in babyLearnset })
        inheritedMoves.addAll(father.moveSet.getMoves()
                .map { it.template }
                .filter { it in babyLearnset })

        // Add Baby's Default Moves
        inheritedMoves.addAll(babyLearnset)

        // TODO sort the moves properly
        // Limit to 4 moves, prioritizing certain moves first based off rules set by the games
        val finalMoves = inheritedMoves.distinct().sortedWith(
            compareByDescending<MoveTemplate> { it.name == "volt_tackle" } // Volt Tackle first
                .thenByDescending { eggMoves?.contains(it) == true }       // Egg moves next
                .thenByDescending { tmHmMoves.contains(it) }              // TM/HM moves after
                .thenByDescending { babyLearnset.contains(it) }           // Default moves last
        ).take(4)

        val moveSet = MoveSet()
        finalMoves.forEach { moveTemplate ->
            moveSet.add(moveTemplate.create())
        }

        return moveSet
    }



    /**
     * Determines the [IVs] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return The [IVs] of the child
     * @author Nick, whatsy, Apion
     */
    fun calculateIVs(mother: Pokemon, father: Pokemon): IVs {
        //FIXME: Use registry holder
        val motherItem = mother.heldItemNoCopy().item.let { BuiltInRegistries.ITEM.wrapAsHolder(it) }
        val fatherItem = father.heldItemNoCopy().item.let { BuiltInRegistries.ITEM.wrapAsHolder(it) }
        val isMotherDestinyKnotted = motherItem.`is`(CobblemonItemTags.DESTINY_KNOT)
        val isFatherDestinyKnotted = fatherItem.`is`(CobblemonItemTags.DESTINY_KNOT)
        val passedStats = mutableSetOf<Stat>()
        val childStats = IVs.createRandomIVs()
        var numPassed = if(isMotherDestinyKnotted || isFatherDestinyKnotted) {
            5
        } else {
            3
        }

        fun calcForcedStat(holder: Holder<Item>?, mon: Pokemon) {
            fun passStat(stats: Stat) {
                if (!passedStats.contains(stats)) {
                    childStats[stats] = mon.ivs[stats]!!
                    passedStats.add(stats)
                    numPassed--
                }
            }
            if(holder == null) {
                return
            }

            if(holder.`is`(CobblemonItemTags.POWER_ANKLET)) {
                passStat(Stats.SPEED)
            } else if(holder.`is`(CobblemonItemTags.POWER_BAND)) {
                passStat(Stats.SPECIAL_DEFENCE)
            } else if(holder.`is`(CobblemonItemTags.POWER_BELT)) {
                passStat(Stats.DEFENCE)
            } else if(holder.`is`(CobblemonItemTags.POWER_BRACER)) {
                passStat(Stats.ATTACK)
            } else if(holder.`is`(CobblemonItemTags.POWER_LENS)) {
                passStat(Stats.SPECIAL_ATTACK)
            } else if(holder.`is`(CobblemonItemTags.POWER_WEIGHT)) {
                passStat(Stats.HP)
            }
        }
        calcForcedStat(fatherItem, father)
        calcForcedStat(motherItem, mother)

        val parentIvs = mother.ivs.toMutableList()
        parentIvs.addAll(father.ivs)
        while (numPassed > 0 && parentIvs.isNotEmpty()) {
            val statPicked: Map.Entry<Stat, Int> = parentIvs.random()
            if (!passedStats.contains(statPicked.key)) {
                passedStats.add(statPicked.key)
                childStats[statPicked.key] = statPicked.value
                parentIvs.removeAll {it.key == statPicked.key}
                numPassed--
            }
        }

        return childStats
    }

    /**
     * Determines the [PokeBall] of a child
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @return The [PokeBall] of the child
     * @author Nick, whatsy
     */
    fun calculatePokeball(mother: Pokemon, father: Pokemon): PokeBall {
        /*
         * The Master Ball, Cherish Ball, and Strange Ball cannot be inherited via breeding; instead, the game treats
         *  any parent obtained in those balls as if they were in a standard Poké Ball for the purposes of inheritance.
         *  To add Poké Balls to this list, add to the defaulting_poke_balls.json item tag.
         */
        val anyDitto = listOf(mother, father).any { it.form.eggGroups.contains(EggGroup.DITTO) }

        val motherBallItem = mother.caughtBall.item().let { BuiltInRegistries.ITEM.wrapAsHolder(it) }
        val fatherBallItem = father.caughtBall.item().let { BuiltInRegistries.ITEM.wrapAsHolder(it) }
        println(motherBallItem.`is`(CobblemonItemTags.DEFAULTING_POKE_BALLS))
        val motherBall = if (motherBallItem.`is`(CobblemonItemTags.DEFAULTING_POKE_BALLS)) PokeBalls.POKE_BALL else mother.caughtBall
        val fatherBall = if (fatherBallItem.`is`(CobblemonItemTags.DEFAULTING_POKE_BALLS)) PokeBalls.POKE_BALL else father.caughtBall

        val ball: PokeBall = if(anyDitto) {
            if (nonDittoPreferMother(mother, father) == mother) motherBall else fatherBall
        } else if(mother.species.resourceIdentifier == father.species.resourceIdentifier) {
            if(Random.nextInt(100) < 50) {
                motherBall
            } else {
                fatherBall
            }
        } else {
            motherBall
        }

        return ball
    }

    /**
     * If the mother is a Ditto, returns the father.
     * Otherwise, returns the mother.
     *
     * @param mother The mother [Pokemon]
     * @param father The father [Pokemon]
     * @author Nick
     */
    fun nonDittoPreferMother(mother: Pokemon, father: Pokemon): Pokemon {
        return if(mother.form.eggGroups.contains(EggGroup.DITTO)) father else mother
    }

    fun resetBreedingCooldown(pokemon: Pokemon): Int {
        val eggCycle = (pokemon.species.eggCycles).coerceAtLeast(1)
        val minEggCycle = 5     // this is the lowest eggCycle (like caterpie)
        val avgEggCycle = 40    // average eggCycle value
        val minTime = 5         // in minutes
        val avgTime = 20        // in minutes

        val slope = (avgTime - minTime) / (avgEggCycle - minEggCycle)
        val intercept = minTime - slope * minEggCycle

        val cooldown = slope * eggCycle + intercept

        return cooldown * 1200 // convert to ticks
    }

    fun calcTimeToHatch(mother: Pokemon, father: Pokemon): Int {
        val avgEggCycle = ((mother.species.eggCycles + father.species.eggCycles) / 2).toInt()
        val cycleToMinute = 2

        return (avgEggCycle * cycleToMinute) * 1200 // convert to ticks
    }

    fun checkCompatibility(mother: Pokemon, father: Pokemon): Boolean {
        val sameSpecies = mother.species == father.species
        val sameTrainer = mother.getOwnerUUID() == father.getOwnerUUID()

        val chance = when {
            sameSpecies && !sameTrainer -> 70 // Same species, different trainer
            sameSpecies && sameTrainer -> 50 // Same species, same trainer
            !sameSpecies && !sameTrainer -> 50 // Different species, different trainer
            !sameSpecies && sameTrainer -> 20
            else -> 0 // Different species, same trainer
        }

        return Random.nextInt(100) < chance
    }
}