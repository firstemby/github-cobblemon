import EvolutionTreeRequirementVisitor.RenderRequirementData
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.requirement.RequirementVisitor
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.requirements.*
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item

class EvolutionTreeRequirementVisitor : RequirementVisitor<RenderRequirementData?> {
    data class RenderRequirementData(
        val icon: Item,
        val tooltip: MutableComponent,
        val label: MutableComponent? = null,
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
        return null
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
        val icon = CobblemonItems.CHOICE_BAND
        val tooltip = lang("ui.pokedex.requirement.use_move.tooltip", useMoveRequirement.move.displayName.green())

        return RenderRequirementData(icon, tooltip)

        return null
    }

    override fun visitWeatherRequirement(weatherRequirement: WeatherRequirement): RenderRequirementData? {
        return null
    }

    override fun visitWorldRequirement(worldRequirement: WorldRequirement): RenderRequirementData? {
        return null
    }
}
