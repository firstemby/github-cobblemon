/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.requirements.*
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.structure.Structure

data class RenderRequirementData(
    val icon: Item,
    val tooltip: MutableComponent,
)

fun renderRequirementData(requirement: Requirement): RenderRequirementData? {
    when (requirement) {
        is AdvancementRequirement -> return renderAdvancementRequirement(requirement)
        is AnyRequirement -> return renderAnyRequirement(requirement)
        is AreaRequirement -> return renderAreaRequirement(requirement)
        is AttackDefenceRatioRequirement -> return renderAttackDefenceRatioRequirement(requirement)
        is BattleCriticalHitsRequirement -> return renderBattleCriticalHitsRequirement(requirement)
        is BiomeRequirement -> return renderBiomeRequirement(requirement)
        is BlocksTraveledRequirement -> return renderBlocksTraveledRequirement(requirement)
        is DamageTakenRequirement -> return renderDamageTakenRequirement(requirement)
        is DefeatRequirement -> return renderDefeatRequirement(requirement)
        is FriendshipRequirement -> return renderFriendshipRequirement(requirement)
        is HeldItemRequirement -> return renderHeldItemRequirement(requirement)
        is LevelRequirement -> return renderLevelRequirement(requirement)
        is MoonPhaseRequirement -> return renderMoonPhaseRequirement(requirement)
        is MoveSetRequirement -> return renderMoveSetRequirement(requirement)
        is MoveTypeRequirement -> return renderMoveTypeRequirement(requirement)
        is OwnerHoldsItemRequirement -> return renderOwnerHoldsItemRequirement(requirement)
        is PartyMemberRequirement -> return renderPartyMemberRequirement(requirement)
        is PokemonPropertiesRequirement -> return renderPokemonPropertiesRequirement(requirement)
        is PropertyRangeRequirement -> return renderPropertyRangeRequirement(requirement)
        is RecoilRequirement -> return renderRecoilRequirement(requirement)
        is StatCompareRequirement -> return renderStatCompareRequirement(requirement)
        is StatEqualRequirement -> return renderStatEqualRequirement(requirement)
        is StructureRequirement -> return renderStructureRequirement(requirement)
        is TimeRangeRequirement -> return renderTimeRangeRequirement(requirement)
        is UseMoveRequirement -> return renderUseMoveRequirement(requirement)
        is WeatherRequirement -> return renderWeatherRequirement(requirement)
        is WorldRequirement -> return renderWorldRequirement(requirement)
    }

    return null
}

fun renderAdvancementRequirement(advancementRequirement: AdvancementRequirement): RenderRequirementData? {
    val icon = Items.BOOK
    val identifier = advancementRequirement.requiredAdvancement
    val advancementLanguageKey = "${identifier.namespace}.${identifier.path.substringAfterLast('/')}"
    val advancementComponent = Component.translatable("advancements.$advancementLanguageKey").green()
    val tooltip = lang("ui.pokedex.requirement.advancement.tooltip", advancementComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderAnyRequirement(anyRequirement: AnyRequirement): RenderRequirementData? {
    return null
}

fun renderAreaRequirement(areaRequirement: AreaRequirement): RenderRequirementData? {
    return null
}

fun renderAttackDefenceRatioRequirement(attackDefenceRatioRequirement: AttackDefenceRatioRequirement): RenderRequirementData? {
    return null
}

fun renderBattleCriticalHitsRequirement(battleCriticalHitsRequirement: BattleCriticalHitsRequirement): RenderRequirementData? {
    val icon = CobblemonItems.MEDICINAL_LEEK
    val criticalsComponent = Component.literal(battleCriticalHitsRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.battle_critical_hits.tooltip", criticalsComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderBiomeRequirement(biomeRequirement: BiomeRequirement): RenderRequirementData? {
    val condition = biomeRequirement.biomeCondition
    val anticondition = biomeRequirement.biomeAnticondition
    if (condition == null && anticondition == null) {
        return null
    }

    val icon = Items.COMPASS
    val displayName: (RegistryLikeCondition<Biome>) -> MutableComponent = {
        when (it) {
            is RegistryLikeTagCondition -> Component.literal(it.tag.location().path.substringAfterLast('/').split('_').joinToString(" ") { part -> part.replaceFirstChar(Char::titlecase) })
            is RegistryLikeIdentifierCondition -> Component.translatable("biome.${it.identifier.namespace}.${it.identifier.path}")
            else -> Component.literal(it.toString())
        }
    }
    val tooltip = if (condition != null && anticondition != null) {
        lang("ui.pokedex.requirement.biome.both.tooltip", displayName(condition).green(), displayName(anticondition).green())
    } else if (condition != null) {
        lang("ui.pokedex.requirement.biome.tooltip", displayName(condition).green())
    } else {
        lang("ui.pokedex.requirement.biome.anticondition.tooltip", displayName(anticondition!!).green())
    }

    val playerBiome = Minecraft.getInstance()?.player?.let { it.level().getBiome(it.blockPosition()) }
    if (playerBiome != null) {
        val isMet = (condition == null || condition.fits(playerBiome)) && (anticondition == null || !anticondition.fits(playerBiome))
        tooltip.add(if (isMet) Component.literal(" ✔").green() else Component.literal(" ✘").red())
    }

    return RenderRequirementData(icon, tooltip)
}

fun renderBlocksTraveledRequirement(blocksTraveledRequirement: BlocksTraveledRequirement): RenderRequirementData? {
    val icon = Items.LEATHER_BOOTS
    val blocksComponent = Component.literal(blocksTraveledRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.blocks_traveled.tooltip", blocksComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderDamageTakenRequirement(damageTakenRequirement: DamageTakenRequirement): RenderRequirementData? {
    val icon = CobblemonItems.PUNCHING_GLOVE
    val blocksComponent = Component.literal(damageTakenRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.damage_taken.tooltip", blocksComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderDefeatRequirement(defeatRequirement: DefeatRequirement): RenderRequirementData? {
    val description = describePokemonProperties(defeatRequirement.target) ?: return null
    val icon = CobblemonItems.PUNCHING_GLOVE
    val defeatedAmount = defeatRequirement.amount

    val descriptionComponent = Component.literal(defeatedAmount.toString()).green().add(" ").add(description)
    val tooltip = lang("ui.pokedex.requirement.defeat.tooltip", descriptionComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderFriendshipRequirement(friendshipRequirement: FriendshipRequirement): RenderRequirementData? {
    val icon = CobblemonItems.SOOTHE_BELL
    val friendshipAmountComponent = Component.literal(friendshipRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.friendship.tooltip", friendshipAmountComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderHeldItemRequirement(heldItemRequirement: HeldItemRequirement): RenderRequirementData? {
    val item = heldItemRequirement.itemCondition.items.get().first().value()
    val tooltip = lang("ui.pokedex.requirement.held_item.tooltip", item.description.plainCopy().green())

    return RenderRequirementData(item, tooltip)
}

fun renderLevelRequirement(levelRequirement: LevelRequirement): RenderRequirementData? {
    val minLevel = levelRequirement.minLevel
    val maxLevel = levelRequirement.maxLevel

    val icon = CobblemonItems.RARE_CANDY
    val tooltip =
        if (minLevel == maxLevel || maxLevel == Int.MAX_VALUE)
            lang("ui.pokedex.requirement.level.tooltip.min", Component.literal(minLevel.toString()).green())
        else
            lang("ui.pokedex.requirement.level.tooltip.between", Component.literal(minLevel.toString()).green(), Component.literal(maxLevel.toString()).green())

    return RenderRequirementData(icon, tooltip)
}

fun renderMoonPhaseRequirement(moonPhaseRequirement: MoonPhaseRequirement): RenderRequirementData? {
    val icon = Items.CLOCK
    val moonPhaseComponent = moonPhaseRequirement.moonPhase.displayName().green()
    val tooltip = lang("ui.pokedex.requirement.moon_phase.tooltip", moonPhaseComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderMoveSetRequirement(moveSetRequirement: MoveSetRequirement): RenderRequirementData? {
    val icon = CobblemonItems.TECHNICAL_MACHINE
    val tooltip = lang("ui.pokedex.requirement.has_move.tooltip", moveSetRequirement.move.displayName.green())

    return RenderRequirementData(icon, tooltip)
}

fun renderMoveTypeRequirement(moveTypeRequirement: MoveTypeRequirement): RenderRequirementData? {
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

    return RenderRequirementData(icon, tooltip)
}

fun renderOwnerHoldsItemRequirement(ownerHoldsItemRequirement: OwnerHoldsItemRequirement): RenderRequirementData? {
    val item = ownerHoldsItemRequirement.itemCondition.items.get().first().value()
    val tooltip = lang("ui.pokedex.requirement.owner_held_item.tooltip", item.description.plainCopy().green())

    return RenderRequirementData(item, tooltip)
}

fun renderPartyMemberRequirement(partyMemberRequirement: PartyMemberRequirement): RenderRequirementData? {
    val description = describePokemonProperties(partyMemberRequirement.target) ?: return null
    val icon = CobblemonItems.POKE_BALL
    val tooltip = if (partyMemberRequirement.contains) {
        lang("ui.pokedex.requirement.party_member.tooltip", description)
    } else {
        lang("ui.pokedex.requirement.party_member.anticondition.tooltip", description)
    }

    return RenderRequirementData(icon, tooltip)
}

fun renderPokemonPropertiesRequirement(pokemonPropertiesRequirement: PokemonPropertiesRequirement): RenderRequirementData? {
    val description = describePokemonProperties(pokemonPropertiesRequirement.target) ?: return null
    val icon = Items.NAME_TAG
    val tooltip = lang("ui.pokedex.requirement.pokemon_properties.tooltip", description)

    return RenderRequirementData(icon, tooltip)
}

fun renderPropertyRangeRequirement(propertyRangeRequirement: PropertyRangeRequirement): RenderRequirementData? {
    val provider = SpeciesFeatures.getFeature(propertyRangeRequirement.feature) as? IntSpeciesFeatureProvider ?: return null
    val displayName = provider.display?.name?.takeIf { it.isNotBlank() }?.asTranslated()?.green() ?: return null
    val icon = provider.itemPoints.keys.firstOrNull()?.let { BuiltInRegistries.ITEM.get(it) } ?: Items.NAME_TAG

    val range = propertyRangeRequirement.range
    val tooltip = lang(
        "ui.pokedex.requirement.property_range.tooltip",
        displayName,
        Component.literal(range.first.toString()).green(),
        Component.literal(range.last.toString()).green()
    )

    return RenderRequirementData(icon, tooltip)
}

fun renderRecoilRequirement(recoilRequirement: RecoilRequirement): RenderRequirementData? {
    val icon = CobblemonItems.LIFE_ORB
    val recoilComponent = Component.literal(recoilRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.recoil.tooltip", recoilComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderStatCompareRequirement(statCompareRequirement: StatCompareRequirement): RenderRequirementData? {
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

fun renderStatEqualRequirement(statEqualRequirement: StatEqualRequirement): RenderRequirementData? {
    val statOne = Stats.getStat(statEqualRequirement.statOne)
    val statTwo = Stats.getStat(statEqualRequirement.statTwo)

    val statOneComponent = statOne.displayName.plainCopy().green()
    val statTwoComponent = statTwo.displayName.plainCopy().green()

    val icon = CobblemonItems.FRESH_START_MOCHI
    val tooltip = lang("ui.pokedex.requirement.stat_equal.tooltip", statOneComponent, statTwoComponent)

    return RenderRequirementData(icon, tooltip)
}

fun renderStructureRequirement(structureRequirement: StructureRequirement): RenderRequirementData? {
    val condition = structureRequirement.structureCondition
    val anticondition = structureRequirement.structureAnticondition
    if (condition == null && anticondition == null) {
        return null
    }

    val icon = Items.STRUCTURE_BLOCK
    val displayName: (RegistryLikeCondition<Structure>) -> MutableComponent = {
        val path = when (it) {
            is RegistryLikeTagCondition -> it.tag.location().path
            is RegistryLikeIdentifierCondition -> it.identifier.path
            else -> it.toString()
        }
        Component.literal(path.substringAfterLast('/').split('_').joinToString(" ") { part -> part.replaceFirstChar(Char::titlecase) })
    }
    val tooltip = if (condition != null && anticondition != null) {
        lang("ui.pokedex.requirement.structure.both.tooltip", displayName(condition).green(), displayName(anticondition).green())
    } else if (condition != null) {
        lang("ui.pokedex.requirement.structure.tooltip", displayName(condition).green())
    } else {
        lang("ui.pokedex.requirement.structure.anticondition.tooltip", displayName(anticondition!!).green())
    }

    return RenderRequirementData(icon, tooltip)
}

fun renderTimeRangeRequirement(timeRangeRequirement: TimeRangeRequirement): RenderRequirementData? {
    val icon = Items.CLOCK
    val timeRangeComponent = timeRangeRequirement.range.displayName() ?: return null
    val tooltip = lang("ui.pokedex.requirement.time_range.tooltip", timeRangeComponent.green())

    return RenderRequirementData(icon, tooltip)
}

fun renderUseMoveRequirement(useMoveRequirement: UseMoveRequirement): RenderRequirementData? {
    val icon = CobblemonItems.CHOICE_BAND
    val tooltip = lang("ui.pokedex.requirement.use_move.tooltip", useMoveRequirement.move.displayName.green())

    return RenderRequirementData(icon, tooltip)
}

fun renderWeatherRequirement(weatherRequirement: WeatherRequirement): RenderRequirementData? {
    val icon: Item
    val tooltip: MutableComponent

    if (weatherRequirement.isThundering == true) {
        icon = Items.LIGHTNING_ROD
        tooltip = lang("ui.pokedex.requirement.weather.tooltip.thundering")
    } else {
        icon = Items.WATER_BUCKET
        tooltip = lang("ui.pokedex.requirement.weather.tooltip.raining")
    }

    return RenderRequirementData(icon, tooltip)
}

fun renderWorldRequirement(worldRequirement: WorldRequirement): RenderRequirementData? {
    return null
}
