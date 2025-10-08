import EvolutionTreeRequirementVisitor.RenderRequirementData
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.requirement.RequirementVisitor
import com.cobblemon.mod.common.pokemon.requirements.*
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item

class EvolutionTreeRequirementVisitor : RequirementVisitor<RenderRequirementData?> {
    data class RenderRequirementData(
        val icon: Item,
        val tooltip: MutableComponent,
    )

    override fun visitAdvancementRequirement(advancementRequirement: AdvancementRequirement): RenderRequirementData? {
        return null
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
        return null
    }

    override fun visitBiomeRequirement(biomeRequirement: BiomeRequirement): RenderRequirementData? {
        return null
    }

    override fun visitBlocksTraveledRequirement(blocksTraveledRequirement: BlocksTraveledRequirement): RenderRequirementData? {
        return null
    }

    override fun visitDamageTakenRequirement(damageTakenRequirement: DamageTakenRequirement): RenderRequirementData? {
        return null
    }

    override fun visitDefeatRequirement(defeatRequirement: DefeatRequirement): RenderRequirementData? {
        return null
    }

    override fun visitFriendshipRequirement(friendshipRequirement: FriendshipRequirement): RenderRequirementData? {
        return null
    }

    override fun visitHeldItemRequirement(heldItemRequirement: HeldItemRequirement): RenderRequirementData? {
        return null
    }

    override fun visitLevelRequirement(levelRequirement: LevelRequirement): RenderRequirementData? {
        val minLevel = levelRequirement.minLevel
        val maxLevel = levelRequirement.maxLevel

        val icon = CobblemonItems.RARE_CANDY
        val tooltip =
            if (minLevel == maxLevel || maxLevel == Int.MAX_VALUE)
                lang("ui.pokedex.requirement.level.tooltip.min", minLevel)
            else
                lang("ui.pokedex.requirement.level.tooltip.between", "$minLevel-$maxLevel")

        return RenderRequirementData(icon, tooltip)
    }

    override fun visitMoonPhaseRequirement(moonPhaseRequirement: MoonPhaseRequirement): RenderRequirementData? {
        return null
    }

    override fun visitMoveSetRequirement(moveSetRequirement: MoveSetRequirement): RenderRequirementData? {
        return null
    }

    override fun visitMoveTypeRequirement(moveTypeRequirement: MoveTypeRequirement): RenderRequirementData? {
        return null
    }

    override fun visitOwnerHoldsItemRequirement(ownerHoldsItemRequirement: OwnerHoldsItemRequirement): RenderRequirementData? {
        return null
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
        return null
    }

    override fun visitStatCompareRequirement(statCompareRequirement: StatCompareRequirement): RenderRequirementData? {
        return null
    }

    override fun visitStatEqualRequirement(statEqualRequirement: StatEqualRequirement): RenderRequirementData? {
        return null
    }

    override fun visitStructureRequirement(structureRequirement: StructureRequirement): RenderRequirementData? {
        return null
    }

    override fun visitTimeRangeRequirement(timeRangeRequirement: TimeRangeRequirement): RenderRequirementData? {
        return null
    }

    override fun visitUseMoveRequirement(useMoveRequirement: UseMoveRequirement): RenderRequirementData? {
        return null
    }

    override fun visitWeatherRequirement(weatherRequirement: WeatherRequirement): RenderRequirementData? {
        return null
    }

    override fun visitWorldRequirement(worldRequirement: WorldRequirement): RenderRequirementData? {
        return null
    }
}
