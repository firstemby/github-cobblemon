/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.breeding.rule.layer.EggBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.IVsBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MovesBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.ShinyBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.adapters.MoveTemplateAdapter
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.SpeciesAdapter
import com.cobblemon.mod.common.util.adapters.TypeMapAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

object BreedingRules : DataRegistry {
    override val id = cobblemonResource("breeding_rules")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BreedingRules>()
    val gson = GsonBuilder()
        .registerTypeAdapter(Species::class.java, SpeciesAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .registerTypeAdapter(MoveTemplate::class.java, MoveTemplateAdapter)
        .registerTypeAdapter(MotherBreedingRule::class.java, TypeMapAdapter(typeName = "motherRule", types = MotherBreedingRule.types))
        .registerTypeAdapter(SpeciesBreedingRule::class.java, TypeMapAdapter(typeName = "speciesRule", types = SpeciesBreedingRule.types))
        .registerTypeAdapter(FormBreedingRule::class.java, TypeMapAdapter(typeName = "formRule", types = FormBreedingRule.types))
        .registerTypeAdapter(IVsBreedingRule::class.java, TypeMapAdapter(typeName = "ivRule", types = IVsBreedingRule.types))
        .registerTypeAdapter(MovesBreedingRule::class.java, TypeMapAdapter(typeName = "moveRule", types = MovesBreedingRule.types))
        .registerTypeAdapter(ShinyBreedingRule::class.java, TypeMapAdapter(typeName = "shinyRule", types = ShinyBreedingRule.types))
        .registerTypeAdapter(PokemonBreedingRule::class.java, TypeMapAdapter(typeName = "pokemonRule", types = PokemonBreedingRule.types))
        .registerTypeAdapter(EggBreedingRule::class.java, TypeMapAdapter(typeName = "eggRule", types = EggBreedingRule.types))
        .setPrettyPrinting()
        .create()

    var motherRules = mutableListOf<MotherBreedingRule>()
        private set
    var offspringSpeciesRules = mutableListOf<SpeciesBreedingRule>()
        private set
    var formInheritanceRules = mutableListOf<FormBreedingRule>()
        private set
    var ivInheritanceRules = mutableListOf<IVsBreedingRule>()
        private set
    var moveInheritanceRules = mutableListOf<MovesBreedingRule>()
        private set
    var shinyRules = mutableListOf<ShinyBreedingRule>()
        private set
    var pokemonRules = mutableListOf<PokemonBreedingRule>()
        private set
    var eggRules = mutableListOf<EggBreedingRule>()
        private set

    override fun reload(manager: ResourceManager) {
        motherRules = getRules(manager, "mother", MotherBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        offspringSpeciesRules = getRules(manager, "species", SpeciesBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        formInheritanceRules = getRules(manager, "form", FormBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        ivInheritanceRules = getRules(manager, "ivs", IVsBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        moveInheritanceRules = getRules(manager, "moves", MovesBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        shinyRules = getRules(manager, "shiny", ShinyBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        eggRules = getRules(manager, "egg", EggBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        pokemonRules = getRules(manager, "pokemon", PokemonBreedingRule::class.java).sortedBy { it.order }.toMutableList()

        observable.emit(this)
    }

    fun <T : BreedingRule> getRules(manager: ResourceManager, name: String, clazz: Class<T>): List<T> {
        val rules = mutableListOf<T>()
        manager.listResources("${id.path}/$name", { it.path.endsWith(".json") }).forEach { resource ->
            val rule = resource.value.openAsReader().use { gson.fromJson(it, clazz) }
            rules.add(rule)
        }
        return rules
    }

    override fun sync(player: ServerPlayer) {}
}