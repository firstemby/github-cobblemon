/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.cobblemon.mod.common.api.breeding.rule.layer.CompatibilityBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.CosmeticBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.FormBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.IVBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MotherBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.MoveBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.NatureBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.SpeciesBreedingRule
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.SpeciesAdapter
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
        .setPrettyPrinting()
        .create()

    var compatibilityRules = mutableListOf<CompatibilityBreedingRule>()
        private set
    var motherRules = mutableListOf<MotherBreedingRule>()
        private set
    var offspringSpeciesRules = mutableListOf<SpeciesBreedingRule>()
        private set
    var formInheritanceRules = mutableListOf<FormBreedingRule>()
        private set
    var natureInheritanceRules = mutableListOf<NatureBreedingRule>()
        private set
    var pokeBallInheritanceRules = mutableListOf<CosmeticBreedingRule>()
        private set
    var ivInheritanceRules = mutableListOf<IVBreedingRule>()
        private set
    var moveInheritanceRules = mutableListOf<MoveBreedingRule>()
        private set

    override fun reload(manager: ResourceManager) {
        compatibilityRules = getRules(manager, "compatibility", CompatibilityBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        motherRules = getRules(manager, "mother", MotherBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        offspringSpeciesRules = getRules(manager, "offspring_species", SpeciesBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        formInheritanceRules = getRules(manager, "form_inheritance", FormBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        natureInheritanceRules = getRules(manager, "nature_inheritance", NatureBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        pokeBallInheritanceRules = getRules(manager, "pokeball_inheritance", CosmeticBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        ivInheritanceRules = getRules(manager, "iv_inheritance", IVBreedingRule::class.java).sortedBy { it.order }.toMutableList()
        moveInheritanceRules = getRules(manager, "move_inheritance", MoveBreedingRule::class.java).sortedBy { it.order }.toMutableList()
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