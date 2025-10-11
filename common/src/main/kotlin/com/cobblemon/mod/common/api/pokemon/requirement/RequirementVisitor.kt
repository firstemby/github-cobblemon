/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.requirement

import com.cobblemon.mod.common.pokemon.requirements.*

interface RequirementVisitor<R> {
    fun visit(requirement: Requirement): R? {
        return when (requirement) {
            is AdvancementRequirement -> visitAdvancementRequirement(requirement)
            is AnyRequirement -> visitAnyRequirement(requirement)
            is AreaRequirement -> visitAreaRequirement(requirement)
            is AttackDefenceRatioRequirement -> visitAttackDefenceRatioRequirement(requirement)
            is BattleCriticalHitsRequirement -> visitBattleCriticalHitsRequirement(requirement)
            is BiomeRequirement -> visitBiomeRequirement(requirement)
            is BlocksTraveledRequirement -> visitBlocksTraveledRequirement(requirement)
            is DamageTakenRequirement -> visitDamageTakenRequirement(requirement)
            is DefeatRequirement -> visitDefeatRequirement(requirement)
            is FriendshipRequirement -> visitFriendshipRequirement(requirement)
            is HeldItemRequirement -> visitHeldItemRequirement(requirement)
            is LevelRequirement -> visitLevelRequirement(requirement)
            is MoonPhaseRequirement -> visitMoonPhaseRequirement(requirement)
            is MoveSetRequirement -> visitMoveSetRequirement(requirement)
            is MoveTypeRequirement -> visitMoveTypeRequirement(requirement)
            is OwnerHoldsItemRequirement -> visitOwnerHoldsItemRequirement(requirement)
            is PartyMemberRequirement -> visitPartyMemberRequirement(requirement)
            is PokemonPropertiesRequirement -> visitPokemonPropertiesRequirement(requirement)
            is PropertyRangeRequirement -> visitPropertyRangeRequirement(requirement)
            is RecoilRequirement -> visitRecoilRequirement(requirement)
            is StatCompareRequirement -> visitStatCompareRequirement(requirement)
            is StatEqualRequirement -> visitStatEqualRequirement(requirement)
            is StructureRequirement -> visitStructureRequirement(requirement)
            is TimeRangeRequirement -> visitTimeRangeRequirement(requirement)
            is UseMoveRequirement -> visitUseMoveRequirement(requirement)
            is WeatherRequirement -> visitWeatherRequirement(requirement)
            is WorldRequirement -> visitWorldRequirement(requirement)
            else -> null
        }
    }

    fun visitAdvancementRequirement(advancementRequirement: AdvancementRequirement): R
    fun visitAnyRequirement(anyRequirement: AnyRequirement): R
    fun visitAreaRequirement(areaRequirement: AreaRequirement): R
    fun visitAttackDefenceRatioRequirement(attackDefenceRatioRequirement: AttackDefenceRatioRequirement): R
    fun visitBattleCriticalHitsRequirement(battleCriticalHitsRequirement: BattleCriticalHitsRequirement): R
    fun visitBiomeRequirement(biomeRequirement: BiomeRequirement): R
    fun visitBlocksTraveledRequirement(blocksTraveledRequirement: BlocksTraveledRequirement): R
    fun visitDamageTakenRequirement(damageTakenRequirement: DamageTakenRequirement): R
    fun visitDefeatRequirement(defeatRequirement: DefeatRequirement): R
    fun visitFriendshipRequirement(friendshipRequirement: FriendshipRequirement): R
    fun visitHeldItemRequirement(heldItemRequirement: HeldItemRequirement): R
    fun visitLevelRequirement(levelRequirement: LevelRequirement): R
    fun visitMoonPhaseRequirement(moonPhaseRequirement: MoonPhaseRequirement): R
    fun visitMoveSetRequirement(moveSetRequirement: MoveSetRequirement): R
    fun visitMoveTypeRequirement(moveTypeRequirement: MoveTypeRequirement): R
    fun visitOwnerHoldsItemRequirement(ownerHoldsItemRequirement: OwnerHoldsItemRequirement): R
    fun visitPartyMemberRequirement(partyMemberRequirement: PartyMemberRequirement): R
    fun visitPokemonPropertiesRequirement(pokemonPropertiesRequirement: PokemonPropertiesRequirement): R
    fun visitPropertyRangeRequirement(propertyRangeRequirement: PropertyRangeRequirement): R
    fun visitRecoilRequirement(recoilRequirement: RecoilRequirement): R
    fun visitStatCompareRequirement(statCompareRequirement: StatCompareRequirement): R
    fun visitStatEqualRequirement(statEqualRequirement: StatEqualRequirement): R
    fun visitStructureRequirement(structureRequirement: StructureRequirement): R
    fun visitTimeRangeRequirement(timeRangeRequirement: TimeRangeRequirement): R
    fun visitUseMoveRequirement(useMoveRequirement: UseMoveRequirement): R
    fun visitWeatherRequirement(weatherRequirement: WeatherRequirement): R
    fun visitWorldRequirement(worldRequirement: WorldRequirement): R
}