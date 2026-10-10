/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.entry

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeTagCondition
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.pokemon.requirements.*
import com.cobblemon.mod.common.util.adapters.RegistryAccessResolver
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.lang
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.structure.Structure
import kotlin.jvm.optionals.getOrNull

data class DexEvolutionDisplay(
    val fromSpecies: ResourceLocation,
    val fromForm: String,
    val fromAspects: Set<String>,
    val toSpecies: ResourceLocation,
    val toForm: String,
    val toAspects: Set<String>,
    val requirements: List<DexEvolutionRequirementDisplay>,
)

interface DexEvolutionRequirementDisplay {
    val icon: Item
    val tooltip: MutableComponent
}

data class CobblemonDexEvolutionRequirementDisplay(
    override val icon: Item,
    override val tooltip: MutableComponent,
) : DexEvolutionRequirementDisplay

data class CobblemonBiomeDexEvolutionRequirementDisplay(
    override val icon: Item,
    override val tooltip: MutableComponent,
    val condition: ExtraCodecs.TagOrElementLocation? = null,
    val antiCondition: ExtraCodecs.TagOrElementLocation? = null,
) : DexEvolutionRequirementDisplay

fun getToolTipHeaderIcon(evolution: Evolution): DexEvolutionRequirementDisplay? {
    when (evolution) {
        is ItemInteractionEvolution -> return getToolTipHeaderIcon(evolution)
        is TradeEvolution -> return getToolTipHeaderIcon(evolution)
    }

    return null
}


fun getToolTipHeaderIcon(itemInteractionEvolution: ItemInteractionEvolution): CobblemonDexEvolutionRequirementDisplay {
    val item = itemInteractionEvolution.requiredContext.items.get().first().value()
    return CobblemonDexEvolutionRequirementDisplay(item, lang("ui.pokedex.evolution.item_interact.tooltip", item.description.plainCopy().green()))
}

fun getToolTipHeaderIcon(tradeEvolution: TradeEvolution): CobblemonDexEvolutionRequirementDisplay {
    val species = tradeEvolution.requiredContext.species
    val tooltip = if (species == null) {
        lang("ui.pokedex.evolution.trade.tooltip.with_any_species")
    } else {
        lang("ui.pokedex.evolution.trade.tooltip.with_species", PokemonSpecies.getByIdentifier(species.asIdentifierDefaultingNamespace())!!.translatedName.green())
    }
    return CobblemonDexEvolutionRequirementDisplay(CobblemonItems.LINK_CABLE, tooltip)
}

fun resolveRequirementData(requirement: Requirement): DexEvolutionRequirementDisplay? {
    val icon = requirement.pokedexIcon?.let {
        RegistryAccessResolver.getAccess().registryOrThrow(Registries.ITEM).get(it)
    }

    val tooltip = requirement.pokedexTooltip?.let {
        Component.translatable(it)
    }

    if (icon != null && tooltip != null) {
        return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
    }

    return buildRequirementData(requirement)
}

fun buildRequirementData(requirement: Requirement): DexEvolutionRequirementDisplay? {
    when (requirement) {
        is AdvancementRequirement -> return buildAdvancementRequirement(requirement)
        is BattleCriticalHitsRequirement -> return buildBattleCriticalHitsRequirement(requirement)
        is BiomeRequirement -> return buildBiomeRequirement(requirement)
        is BlocksTraveledRequirement -> return buildBlocksTraveledRequirement(requirement)
        is DamageTakenRequirement -> return buildDamageTakenRequirement(requirement)
        is DefeatRequirement -> return buildDefeatRequirement(requirement)
        is FriendshipRequirement -> return buildFriendshipRequirement(requirement)
        is HeldItemRequirement -> return buildHeldItemRequirement(requirement)
        is LevelRequirement -> return buildLevelRequirement(requirement)
        is MoonPhaseRequirement -> return buildMoonPhaseRequirement(requirement)
        is MoveSetRequirement -> return buildMoveSetRequirement(requirement)
        is MoveTypeRequirement -> return buildMoveTypeRequirement(requirement)
        is NegateRequirement -> return buildNegateRequirement(requirement)
        is OwnerHoldsItemRequirement -> return buildOwnerHoldsItemRequirement(requirement)
        is PartyMemberRequirement -> return buildPartyMemberRequirement(requirement)
        is PokemonPropertiesRequirement -> return buildPokemonPropertiesRequirement(requirement)
        is PropertyRangeRequirement -> return buildPropertyRangeRequirement(requirement)
        is RecoilRequirement -> return buildRecoilRequirement(requirement)
        is StatCompareRequirement -> return buildStatCompareRequirement(requirement)
        is StatEqualRequirement -> return buildStatEqualRequirement(requirement)
        is StructureRequirement -> return buildStructureRequirement(requirement)
        is TimeRangeRequirement -> return buildTimeRangeRequirement(requirement)
        is UseMoveRequirement -> return buildUseMoveRequirement(requirement)
        is WeatherRequirement -> return buildWeatherRequirement(requirement)
    }

    return null
}

fun buildAdvancementRequirement(advancementRequirement: AdvancementRequirement): DexEvolutionRequirementDisplay {
    val icon = Items.BOOK
    val identifier = advancementRequirement.requiredAdvancement
    val advancementLanguageKey = "${identifier.namespace}.${identifier.path.substringAfterLast('/')}"
    val advancementComponent = Component.translatable("advancements.$advancementLanguageKey").green()
    val tooltip = lang("ui.pokedex.requirement.advancement.tooltip", advancementComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildBattleCriticalHitsRequirement(battleCriticalHitsRequirement: BattleCriticalHitsRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.MEDICINAL_LEEK
    val criticalsComponent = Component.literal(battleCriticalHitsRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.battle_critical_hits.tooltip", criticalsComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

private fun tagDisplayName(registry: String, id: ResourceLocation): MutableComponent {
    return Component.translatableWithFallback("tag.$registry.${id.namespace}.${id.path.replace('/', '.')}", prettifyPath(id.path))
}

private fun prettifyPath(path: String): String {
    return path.substringAfterLast('/').removePrefix("is_").split('_').joinToString(" ") { part -> part.replaceFirstChar(Char::titlecase) }
}

fun buildNegateRequirement(negateRequirement: NegateRequirement): DexEvolutionRequirementDisplay? {
    val inner = negateRequirement.inner
    when (inner) {
        is BiomeRequirement -> buildBiomeRequirement(inner, negated = true)?.let { return it }
        is StructureRequirement -> buildStructureRequirement(inner, negated = true)?.let { return it }
    }
    val innerDisplay = resolveRequirementData(inner) ?: return null
    return CobblemonDexEvolutionRequirementDisplay(innerDisplay.icon, lang("ui.pokedex.requirement.negate.tooltip", innerDisplay.tooltip))
}

fun buildBiomeRequirement(biomeRequirement: BiomeRequirement, negated: Boolean = false): DexEvolutionRequirementDisplay? {
    // Negating "in X" is "not in X"; negating both a condition and an anticondition isn't expressible as one line
    if (negated && biomeRequirement.biomeCondition != null && biomeRequirement.biomeAnticondition != null) return null
    val condition = if (negated) biomeRequirement.biomeAnticondition else biomeRequirement.biomeCondition
    val antiCondition = if (negated) biomeRequirement.biomeCondition else biomeRequirement.biomeAnticondition
    if (condition == null && antiCondition == null) {
        return null
    }

    val icon = Items.COMPASS
    val displayName: (RegistryLikeCondition<Biome>) -> MutableComponent = {
        when (it) {
            is RegistryLikeTagCondition -> tagDisplayName("worldgen.biome", it.tag.location())
            is RegistryLikeIdentifierCondition -> Component.translatable("biome.${it.identifier.namespace}.${it.identifier.path}")
            else -> Component.literal(it.toString())
        }
    }

    val tooltip = if (condition != null && antiCondition != null) {
        lang("ui.pokedex.requirement.biome.both.tooltip", displayName(condition).green(), displayName(antiCondition).green())
    } else if (condition != null) {
        lang("ui.pokedex.requirement.biome.tooltip", displayName(condition).green())
    } else {
        lang("ui.pokedex.requirement.biome.anticondition.tooltip", displayName(antiCondition!!).green())
    }

    var conditionConverted: ExtraCodecs.TagOrElementLocation? = null
    if (condition != null) {
        val conditionIdentifier = if (condition is RegistryLikeIdentifierCondition) condition.identifier else (condition as RegistryLikeTagCondition).tag.location
        conditionConverted = ExtraCodecs.TagOrElementLocation(conditionIdentifier, condition is RegistryLikeTagCondition)
    }

    var antiConditionConverted: ExtraCodecs.TagOrElementLocation? = null
    if (antiCondition != null) {
        val conditionIdentifier = if (antiCondition is RegistryLikeIdentifierCondition) antiCondition.identifier else (antiCondition as RegistryLikeTagCondition).tag.location
        antiConditionConverted = ExtraCodecs.TagOrElementLocation(conditionIdentifier, antiCondition is RegistryLikeTagCondition)
    }

    return CobblemonBiomeDexEvolutionRequirementDisplay(icon, tooltip, conditionConverted, antiConditionConverted)
}

fun buildBlocksTraveledRequirement(blocksTraveledRequirement: BlocksTraveledRequirement): DexEvolutionRequirementDisplay {
    val icon = Items.LEATHER_BOOTS
    val blocksComponent = Component.literal(blocksTraveledRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.blocks_traveled.tooltip", blocksComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildDamageTakenRequirement(damageTakenRequirement: DamageTakenRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.PUNCHING_GLOVE
    val blocksComponent = Component.literal(damageTakenRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.damage_taken.tooltip", blocksComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildDefeatRequirement(defeatRequirement: DefeatRequirement): DexEvolutionRequirementDisplay? {
    val description = describePokemonProperties(defeatRequirement.target) ?: return null
    val icon = CobblemonItems.PUNCHING_GLOVE
    val defeatedAmount = defeatRequirement.amount

    val descriptionComponent = Component.literal(defeatedAmount.toString()).green().add(" ").add(description)
    val tooltip = lang("ui.pokedex.requirement.defeat.tooltip", descriptionComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildFriendshipRequirement(friendshipRequirement: FriendshipRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.SOOTHE_BELL
    val friendshipAmountComponent = Component.literal(friendshipRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.friendship.tooltip", friendshipAmountComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildHeldItemRequirement(heldItemRequirement: HeldItemRequirement): DexEvolutionRequirementDisplay? {
    val items = heldItemRequirement.itemCondition.items.getOrNull() ?: return null
    val item = items.firstOrNull()?.value() ?: return null
    val itemComponent = items.unwrapKey().map { tag ->
        val id = tag.location()
        val fallback = id.path.substringAfterLast('/').split('_').joinToString(" ") { part -> part.replaceFirstChar(Char::titlecase) }
        Component.translatableWithFallback("tag.item.${id.namespace}.${id.path.replace('/', '.')}", fallback)
    }.orElseGet { item.description.plainCopy() }
    val tooltip = lang("ui.pokedex.requirement.held_item.tooltip", itemComponent.green())

    return CobblemonDexEvolutionRequirementDisplay(item, tooltip)
}

fun buildLevelRequirement(levelRequirement: LevelRequirement): DexEvolutionRequirementDisplay {
    val minLevel = levelRequirement.minLevel
    val maxLevel = levelRequirement.maxLevel

    val icon = CobblemonItems.RARE_CANDY
    val tooltip =
        if (minLevel == maxLevel || maxLevel == Int.MAX_VALUE)
            lang("ui.pokedex.requirement.level.tooltip.min", Component.literal(minLevel.toString()).green())
        else
            lang("ui.pokedex.requirement.level.tooltip.between", Component.literal(minLevel.toString()).green(), Component.literal(maxLevel.toString()).green())

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildMoonPhaseRequirement(moonPhaseRequirement: MoonPhaseRequirement): DexEvolutionRequirementDisplay {
    val icon = Items.CLOCK
    val moonPhaseComponent = moonPhaseRequirement.moonPhase.displayName().green()
    val tooltip = lang("ui.pokedex.requirement.moon_phase.tooltip", moonPhaseComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildMoveSetRequirement(moveSetRequirement: MoveSetRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.TECHNICAL_MACHINE
    val tooltip = lang("ui.pokedex.requirement.has_move.tooltip", moveSetRequirement.move.displayName.green())

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildMoveTypeRequirement(moveTypeRequirement: MoveTypeRequirement): DexEvolutionRequirementDisplay {
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

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildOwnerHoldsItemRequirement(ownerHoldsItemRequirement: OwnerHoldsItemRequirement): DexEvolutionRequirementDisplay {
    val item = ownerHoldsItemRequirement.itemCondition.items.get().first().value()
    val tooltip = lang("ui.pokedex.requirement.owner_held_item.tooltip", item.description.plainCopy().green())

    return CobblemonDexEvolutionRequirementDisplay(item, tooltip)
}

fun buildPartyMemberRequirement(partyMemberRequirement: PartyMemberRequirement): DexEvolutionRequirementDisplay? {
    val description = describePokemonProperties(partyMemberRequirement.target) ?: return null
    val icon = CobblemonItems.POKE_BALL
    val tooltip = if (partyMemberRequirement.contains) {
        lang("ui.pokedex.requirement.party_member.tooltip", description)
    } else {
        lang("ui.pokedex.requirement.party_member.anticondition.tooltip", description)
    }

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildPokemonPropertiesRequirement(pokemonPropertiesRequirement: PokemonPropertiesRequirement): DexEvolutionRequirementDisplay? {
    val description = describePokemonProperties(pokemonPropertiesRequirement.target) ?: return null
    val icon = Items.NAME_TAG
    val tooltip = lang("ui.pokedex.requirement.pokemon_properties.tooltip", description)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildPropertyRangeRequirement(propertyRangeRequirement: PropertyRangeRequirement): DexEvolutionRequirementDisplay? {
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

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildRecoilRequirement(recoilRequirement: RecoilRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.LIFE_ORB
    val recoilComponent = Component.literal(recoilRequirement.amount.toString()).green()
    val tooltip = lang("ui.pokedex.requirement.recoil.tooltip", recoilComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildStatCompareRequirement(statCompareRequirement: StatCompareRequirement): DexEvolutionRequirementDisplay {
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

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildStatEqualRequirement(statEqualRequirement: StatEqualRequirement): DexEvolutionRequirementDisplay {
    val statOne = Stats.getStat(statEqualRequirement.statOne)
    val statTwo = Stats.getStat(statEqualRequirement.statTwo)

    val statOneComponent = statOne.displayName.plainCopy().green()
    val statTwoComponent = statTwo.displayName.plainCopy().green()

    val icon = CobblemonItems.FRESH_START_MOCHI
    val tooltip = lang("ui.pokedex.requirement.stat_equal.tooltip", statOneComponent, statTwoComponent)

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildStructureRequirement(structureRequirement: StructureRequirement, negated: Boolean = false): DexEvolutionRequirementDisplay? {
    if (negated && structureRequirement.structureCondition != null && structureRequirement.structureAnticondition != null) return null
    val condition = if (negated) structureRequirement.structureAnticondition else structureRequirement.structureCondition
    val anticondition = if (negated) structureRequirement.structureCondition else structureRequirement.structureAnticondition
    if (condition == null && anticondition == null) {
        return null
    }

    val icon = Items.STRUCTURE_BLOCK
    val displayName: (RegistryLikeCondition<Structure>) -> MutableComponent = {
        when (it) {
            is RegistryLikeTagCondition -> tagDisplayName("worldgen.structure", it.tag.location())
            is RegistryLikeIdentifierCondition -> Component.literal(prettifyPath(it.identifier.path))
            else -> Component.literal(it.toString())
        }
    }
    val tooltip = if (condition != null && anticondition != null) {
        lang("ui.pokedex.requirement.structure.both.tooltip", displayName(condition).green(), displayName(anticondition).green())
    } else if (condition != null) {
        lang("ui.pokedex.requirement.structure.tooltip", displayName(condition).green())
    } else {
        lang("ui.pokedex.requirement.structure.anticondition.tooltip", displayName(anticondition!!).green())
    }

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildTimeRangeRequirement(timeRangeRequirement: TimeRangeRequirement): DexEvolutionRequirementDisplay? {
    val icon = Items.CLOCK
    val timeRangeComponent = timeRangeRequirement.range.displayName() ?: return null
    val tooltip = lang("ui.pokedex.requirement.time_range.tooltip", timeRangeComponent.green())

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildUseMoveRequirement(useMoveRequirement: UseMoveRequirement): DexEvolutionRequirementDisplay {
    val icon = CobblemonItems.CHOICE_BAND
    val tooltip = lang("ui.pokedex.requirement.use_move.tooltip", useMoveRequirement.move.displayName.green())

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}

fun buildWeatherRequirement(weatherRequirement: WeatherRequirement): DexEvolutionRequirementDisplay {
    val icon: Item
    val tooltip: MutableComponent

    if (weatherRequirement.isThundering == true) {
        icon = Items.LIGHTNING_ROD
        tooltip = lang("ui.pokedex.requirement.weather.tooltip.thundering")
    } else {
        icon = Items.WATER_BUCKET
        tooltip = lang("ui.pokedex.requirement.weather.tooltip.raining")
    }

    return CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
}
