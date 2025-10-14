/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree.EvolutionTreeRequirementVisitor.RenderRequirementData
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.requirement.RequirementVisitor
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.requirements.*
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class EvolutionTreeRequirementVisitor : RequirementVisitor<RenderRequirementData?> {
    data class RenderRequirementData(
        val icon: Item,
        val tooltip: MutableComponent,
        val label: MutableComponent? = null,
    )

    override fun visitAdvancementRequirement(advancementRequirement: AdvancementRequirement): RenderRequirementData? {
        val icon = Items.BOOK
        val advancementLanguageKey = advancementRequirement.requiredAdvancement.toLanguageKey()
        val advancementComponent = Component.translatable("advancements.$advancementLanguageKey").green()
        val tooltip = lang("ui.pokedex.requirement.advancement.tooltip", advancementComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitAnyRequirement(anyRequirement: AnyRequirement): RenderRequirementData? {
        return null
    }

    override fun visitAreaRequirement(areaRequirement: AreaRequirement): RenderRequirementData? {
        return null
    }

    override fun visitAttackDefenceRatioRequirement(attackDefenceRatioRequirement: AttackDefenceRatioRequirement): RenderRequirementData? {
        return null
    }

    override fun visitBattleCriticalHitsRequirement(battleCriticalHitsRequirement: BattleCriticalHitsRequirement): RenderRequirementData? {
        val icon = CobblemonItems.MEDICINAL_LEEK
        val criticalsComponent = Component.literal(battleCriticalHitsRequirement.amount.toString()).green()
        val tooltip = lang("ui.pokedex.requirement.battle_critical_hits.tooltip", criticalsComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitBiomeRequirement(biomeRequirement: BiomeRequirement): RenderRequirementData? {
        return null
    }

    override fun visitBlocksTraveledRequirement(blocksTraveledRequirement: BlocksTraveledRequirement): RenderRequirementData? {
        val icon = Items.LEATHER_BOOTS
        val blocksComponent = Component.literal(blocksTraveledRequirement.amount.toString()).green()
        val tooltip = lang("ui.pokedex.requirement.blocks_traveled.tooltip", blocksComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitDamageTakenRequirement(damageTakenRequirement: DamageTakenRequirement): RenderRequirementData? {
        val icon = CobblemonItems.PUNCHING_GLOVE
        val blocksComponent = Component.literal(damageTakenRequirement.amount.toString()).green()
        val tooltip = lang("ui.pokedex.requirement.damage_taken.tooltip", blocksComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitDefeatRequirement(defeatRequirement: DefeatRequirement): RenderRequirementData? {
        val species = defeatRequirement.target.species?.let { PokemonSpecies.getByIdentifier(cobblemonResource(it)) } ?: return null
        val icon = CobblemonItems.PUNCHING_GLOVE
        val defeatedAmount = defeatRequirement.amount

        val descriptionComponent = Component.literal(defeatedAmount.toString()).plainCopy().add(" ").add(species.translatedName).green()
        val tooltip = lang("ui.pokedex.requirement.defeat.tooltip", descriptionComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitFriendshipRequirement(friendshipRequirement: FriendshipRequirement): RenderRequirementData? {
        val icon = CobblemonItems.SOOTHE_BELL
        val friendshipAmountComponent = Component.literal(friendshipRequirement.amount.toString()).green()
        val tooltip = lang("ui.pokedex.requirement.friendship.tooltip", friendshipAmountComponent)
        val label = lang("ui.pokedex.requirement.friendship.label")

        return RenderRequirementData(icon, tooltip, label)
    }

    override fun visitHeldItemRequirement(heldItemRequirement: HeldItemRequirement): RenderRequirementData? {
        val item = heldItemRequirement.itemCondition.items.get().first().value()
        val tooltip = lang("ui.pokedex.requirement.held_item.tooltip", item.description.plainCopy().green())

        return RenderRequirementData(item, tooltip)
    }

    override fun visitLevelRequirement(levelRequirement: LevelRequirement): RenderRequirementData? {
        val minLevel = levelRequirement.minLevel
        val maxLevel = levelRequirement.maxLevel

        val icon = CobblemonItems.RARE_CANDY
        val tooltip =
            if (minLevel == maxLevel || maxLevel == Int.MAX_VALUE)
                lang("ui.pokedex.requirement.level.tooltip.min", Component.literal(minLevel.toString()).green())
            else
                lang("ui.pokedex.requirement.level.tooltip.between", Component.literal(minLevel.toString()).green(), Component.literal(maxLevel.toString()).green())

        val label = lang("ui.pokedex.requirement.level.label", minLevel)

        return RenderRequirementData(icon, tooltip, label)
    }

    override fun visitMoonPhaseRequirement(moonPhaseRequirement: MoonPhaseRequirement): RenderRequirementData? {
        val icon = Items.CLOCK
        val moonPhaseComponent = moonPhaseRequirement.moonPhase.displayName().green()
        val tooltip = lang("ui.pokedex.requirement.moon_phase.tooltip", moonPhaseComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitMoveSetRequirement(moveSetRequirement: MoveSetRequirement): RenderRequirementData? {
        val icon = CobblemonItems.PP_MAX
        val tooltip = lang("ui.pokedex.requirement.has_move.tooltip", moveSetRequirement.move.displayName.green())

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitMoveTypeRequirement(moveTypeRequirement: MoveTypeRequirement): RenderRequirementData? {
        val type = ElementalTypes.get(moveTypeRequirement.type.name)
        val icon = when (type) {
            ElementalTypes.NORMAL -> CobblemonItems.NORMAL_GEM
            ElementalTypes.FIRE -> CobblemonItems.FIRE_GEM
            ElementalTypes.WATER -> CobblemonItems.WATER_GEM
            ElementalTypes.GRASS -> CobblemonItems.GRASS_GEM
            ElementalTypes.ELECTRIC -> CobblemonItems.ELECTRIC_GEM
            ElementalTypes.ICE -> CobblemonItems.ICE_GEM
            ElementalTypes.FIGHTING -> CobblemonItems.FIGHTING_GEM
            ElementalTypes.POISON -> CobblemonItems.POISON_GEM
            ElementalTypes.GROUND -> CobblemonItems.GROUND_GEM
            ElementalTypes.FLYING -> CobblemonItems.FLYING_GEM
            ElementalTypes.PSYCHIC -> CobblemonItems.PSYCHIC_GEM
            ElementalTypes.BUG -> CobblemonItems.BUG_GEM
            ElementalTypes.ROCK -> CobblemonItems.ROCK_GEM
            ElementalTypes.GHOST -> CobblemonItems.GHOST_GEM
            ElementalTypes.DRAGON -> CobblemonItems.DRAGON_GEM
            ElementalTypes.DARK -> CobblemonItems.DARK_GEM
            ElementalTypes.STEEL -> CobblemonItems.STEEL_GEM
            ElementalTypes.FAIRY -> CobblemonItems.FAIRY_GEM
            else -> CobblemonItems.NORMAL_GEM
        }

        val tooltip = lang("ui.pokedex.requirement.has_move_type.tooltip", moveTypeRequirement.type.displayName.green())
        val label = lang("ui.pokedex.requirement.has_move_type.label")

        return RenderRequirementData(icon, tooltip, label)
    }

    // TODO test
    override fun visitOwnerHoldsItemRequirement(ownerHoldsItemRequirement: OwnerHoldsItemRequirement): RenderRequirementData? {
        val item = ownerHoldsItemRequirement.itemCondition.items.get().first().value()
        val tooltip = lang("ui.pokedex.requirement.owner_held_item.tooltip", item.description.plainCopy().green())

        return RenderRequirementData(item, tooltip)
    }

    override fun visitPartyMemberRequirement(partyMemberRequirement: PartyMemberRequirement): RenderRequirementData? {
        return null
    }

    override fun visitPokemonPropertiesRequirement(pokemonPropertiesRequirement: PokemonPropertiesRequirement): RenderRequirementData? {
        return null
    }

    override fun visitPropertyRangeRequirement(propertyRangeRequirement: PropertyRangeRequirement): RenderRequirementData? {
        return null
    }

    override fun visitRecoilRequirement(recoilRequirement: RecoilRequirement): RenderRequirementData? {
        val icon = CobblemonItems.LIFE_ORB
        val recoilComponent = Component.literal(recoilRequirement.amount.toString()).green()
        val tooltip = lang("ui.pokedex.requirement.recoil.tooltip", recoilComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitStatCompareRequirement(statCompareRequirement: StatCompareRequirement): RenderRequirementData? {
        val highStat = Stats.getStat(statCompareRequirement.highStat)
        val lowStat = Stats.getStat(statCompareRequirement.lowStat)

        val icon = when (highStat) {
            Stats.HP, Stats.EVASION, Stats.ACCURACY -> CobblemonItems.HEALTH_MOCHI
            Stats.ATTACK -> CobblemonItems.MUSCLE_MOCHI
            Stats.DEFENCE -> CobblemonItems.RESIST_MOCHI
            Stats.SPECIAL_ATTACK -> CobblemonItems.GENIUS_MOCHI
            Stats.SPECIAL_DEFENCE -> CobblemonItems.CLEVER_MOCHI
            Stats.SPEED -> CobblemonItems.SWIFT_MOCHI
        }

        val highStatComponent = highStat.displayName.plainCopy().green()
        val lowStatComponent = lowStat.displayName.plainCopy().green()
        val tooltip = lang("ui.pokedex.requirement.stat_compare.tooltip", highStatComponent, lowStatComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitStatEqualRequirement(statEqualRequirement: StatEqualRequirement): RenderRequirementData? {
        val statOne = Stats.getStat(statEqualRequirement.statOne)
        val statTwo = Stats.getStat(statEqualRequirement.statTwo)

        val statOneComponent = statOne.displayName.plainCopy().green()
        val statTwoComponent = statTwo.displayName.plainCopy().green()

        val icon = CobblemonItems.FRESH_START_MOCHI
        val tooltip = lang("ui.pokedex.requirement.stat_equal.tooltip", statOneComponent, statTwoComponent)

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitStructureRequirement(structureRequirement: StructureRequirement): RenderRequirementData? {
        return null
    }

    override fun visitTimeRangeRequirement(timeRangeRequirement: TimeRangeRequirement): RenderRequirementData? {
        val icon = Items.CLOCK
        val timeRangeComponent = timeRangeRequirement.range.displayName() ?: return null
        val tooltip = lang("ui.pokedex.requirement.time_range.tooltip", timeRangeComponent.green())

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitUseMoveRequirement(useMoveRequirement: UseMoveRequirement): RenderRequirementData? {
        val icon = CobblemonItems.CHOICE_BAND
        val tooltip = lang("ui.pokedex.requirement.use_move.tooltip", useMoveRequirement.move.displayName.green())

        return RenderRequirementData(icon, tooltip)

        return null
    }

    override fun visitWeatherRequirement(weatherRequirement: WeatherRequirement): RenderRequirementData? {
        var icon: Item
        var tooltip: MutableComponent

        if (weatherRequirement.isThundering == true) {
            icon = Items.LIGHTNING_ROD
            tooltip = lang("ui.pokedex.requirement.weather.tooltip.thundering")
        } else {
            icon = Items.WATER_BUCKET
            tooltip = lang("ui.pokedex.requirement.weather.tooltip.raining")
        }

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitWorldRequirement(worldRequirement: WorldRequirement): RenderRequirementData? {
        return null
    }
}
