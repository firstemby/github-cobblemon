/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.abilities.AbilityPool
import com.cobblemon.mod.common.api.abilities.AbilityTemplate
import com.cobblemon.mod.common.api.abilities.CommonAbility
import com.cobblemon.mod.common.api.abilities.PotentialAbility
import com.cobblemon.mod.common.api.ai.SleepDepth
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.drop.DropEntry
import com.cobblemon.mod.common.api.drop.ItemDropMethod
import com.cobblemon.mod.common.api.entity.EntityDimensionsAdapter
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.adapters.MoveTemplateAdapter
import com.cobblemon.mod.common.api.pokemon.effect.ShoulderEffect
import com.cobblemon.mod.common.api.pokemon.effect.adapter.ShoulderEffectAdapter
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceGroup
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceGroupAdapter
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings
import com.cobblemon.mod.common.api.riding.stats.RidingStatDefinition
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.adapters.ElementalTypeAdapter
import com.cobblemon.mod.common.net.messages.client.data.SpeciesRegistrySyncPacket
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.SpeciesAdditions
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.abilities.SpecialAbility
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import com.cobblemon.mod.common.pokemon.transformation.adapters.*
import com.cobblemon.mod.common.pokemon.transformation.form.GigantamaxForm
import com.cobblemon.mod.common.pokemon.transformation.form.PermanentForm
import com.cobblemon.mod.common.pokemon.transformation.form.StandardForm
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.pokemon.transformation.requirements.HeldItemRequirement
import com.cobblemon.mod.common.pokemon.transformation.requirements.MoveSetRequirement
import com.cobblemon.mod.common.util.adapters.*
import com.cobblemon.mod.common.util.adapters.RidingBehaviourSettingsAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.ifClient
import com.google.common.collect.HashBasedTable
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.item.Item
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.phys.AABB
import java.util.NoSuchElementException

object PokemonSpecies : JsonDataRegistry<Species> {

    override val id = cobblemonResource("species")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Stat::class.java, Cobblemon.statProvider.typeAdapter)
        .registerTypeAdapter(ElementalType::class.java, ElementalTypeAdapter)
        .registerTypeAdapter(AbilityTemplate::class.java, AbilityTemplateAdapter)
        .registerTypeAdapter(ShoulderEffect::class.java, ShoulderEffectAdapter)
        .registerTypeAdapter(MoveTemplate::class.java, MoveTemplateAdapter)
        .registerTypeAdapter(ExperienceGroup::class.java, ExperienceGroupAdapter)
        .registerTypeAdapter(EntityDimensions::class.java, EntityDimensionsAdapter)
        .registerTypeAdapter(Learnset::class.java, LearnsetAdapter)
        .registerTypeAdapter(AABB::class.java, BoxAdapter)
        .registerTypeAdapter(AbilityPool::class.java, AbilityPoolAdapter)
        .registerTypeAdapter(PreEvolution::class.java, CobblemonPreEvolutionAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(Set::class.java, Evolution::class.java).type, LazySetAdapter(Evolution::class))
        .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
        .registerTypeAdapter(PokemonProperties::class.java, pokemonPropertiesShortAdapter)
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(TimeRange::class.java, IntRangesAdapter(TimeRange.timeRanges) { TimeRange(*it) })
        .registerTypeAdapter(ItemDropMethod::class.java, ItemDropMethod.adapter)
        .registerTypeAdapter(SleepDepth::class.java, SleepDepth.adapter)
        .registerTypeAdapter(DropEntry::class.java, DropEntryAdapter)
        .registerTypeAdapter(CompoundTag::class.java, NbtCompoundAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(Component::class.java, TextAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Biome::class.java).type, BiomeLikeConditionAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Block::class.java).type, BlockLikeConditionAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Item::class.java).type, ItemLikeConditionAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Structure::class.java).type, StructureLikeConditionAdapter)
        .registerTypeAdapter(EggGroup::class.java, EggGroupAdapter)
        .registerTypeAdapter(MobEffect::class.java, RegistryElementAdapter<MobEffect>(BuiltInRegistries::MOB_EFFECT))
        .registerTypeAdapter(TransformationTrigger::class.java, CobblemonTriggerAdapter)
        .registerTypeAdapter(TransformationRequirement::class.java, CobblemonRequirementAdapter)
        .registerTypeAdapter(ItemPredicate::class.java, LegacyItemConditionWrapperAdapter)
        .registerTypeAdapter(FormData::class.java, FormAdapter)
        .registerTypeAdapter(RidingBehaviourSettings::class.java, RidingBehaviourSettingsAdapter)
        .registerTypeAdapter(RidingStatDefinition::class.java, RidingStatDefinitionAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .disableHtmlEscaping()
        .enableComplexMapKeySerialization()
        .create()

    override val typeToken: TypeToken<Species> = TypeToken.get(Species::class.java)
    override val resourcePath = "species"

    override val observable = SimpleObservable<PokemonSpecies>()

    private val speciesByIdentifier = hashMapOf<ResourceLocation, Species>()
    private val speciesByDex = HashBasedTable.create<String, Int, Species>()

    val species: Collection<Species>
        get() = this.speciesByIdentifier.values
    val implemented = mutableListOf<Species>()

    val forms: Collection<FormData>
        get() = this.species.flatMap { it.allForms }

    init {
        SpeciesAdditions.observable.subscribe {
            this.species.forEach(Species::initialize)
            this.species.forEach {
                if (it.implemented) {
                    this.implemented.add(it)
                }
            }
            this.species.forEach(Species::resolveEvolutionMoves)
            Cobblemon.showdownThread.queue {
                it.registerSpecies()
                it.indicateSpeciesInitialized()
                // Reload this with the mod
                CobblemonHeldItemManager.load()
                Cobblemon.LOGGER.info("Loaded {} Pokémon species", this.speciesByIdentifier.size)
                this.observable.emit(this)
            }
        }
    }

    /**
     * Finds a species by the pathname of their [ResourceLocation].
     * This method exists for the convenience of finding Cobble default Pokémon.
     * This uses [getByIdentifier] using the [Cobblemon.MODID] as the namespace and the [name] as the path.
     *
     * @param name The path of the species asset.
     * @return The [Species] if existing.
     */
    fun getByName(name: String) = this.getByIdentifier(cobblemonResource(name))

    /**
     * Finds a [Species] by its national Pokédex entry number.
     *
     * @param ndex The [Species.nationalPokedexNumber].
     * @return The [Species] if existing.
     */
    fun getByPokedexNumber(ndex: Int, namespace: String = Cobblemon.MODID) = this.speciesByDex.get(namespace, ndex)

    /**
     * Finds a [Species] by its unique [ResourceLocation].
     *
     * @param identifier The unique [Species.resourceIdentifier] of the [Species].
     * @return The [Species] if existing.
     */
    fun getByIdentifier(identifier: ResourceLocation) = this.speciesByIdentifier[identifier]

    /**
     * Counts the currently loaded species.
     *
     * @return The loaded species amount.
     */
    fun count() = this.speciesByIdentifier.size

    /**
     * Gets a map of dex numbers to species.
     *
     * @return The dex numbers map to species.
     */
    fun getSpeciesInNamespace(namespace: String = Cobblemon.MODID): MutableMap<Int, Species> = speciesByDex.row(namespace)

    /**
     * Get a list of loaded namespaces.
     *
     * @return The list of loaded namespaces.
     */
    fun getNamespaces() = speciesByDex.rowKeySet().toList()

    /**
     * Picks a random [Species].
     *
     * @throws [NoSuchElementException] if there are no Pokémon species loaded.
     *
     * @return A randomly selected [Species].
     */
    fun random(): Species = this.implemented.random()

    override fun reload(data: Map<ResourceLocation, Species>) {
        this.speciesByIdentifier.clear()
        this.implemented.clear()
        this.speciesByDex.clear()
        data.forEach { (identifier, species) ->
            species.resourceIdentifier = identifier
            this.speciesByIdentifier.put(identifier, species)?.let { old ->
                this.speciesByDex.remove(old.resourceIdentifier.namespace, old.nationalPokedexNumber)
            }
            this.speciesByDex.put(species.resourceIdentifier.namespace, species.nationalPokedexNumber, species)
            ifClient {
                if (species.implemented) {
                    this.implemented.add(species)
                }
            }
        }
    }

    override fun sync(player: ServerPlayer) {
        SpeciesRegistrySyncPacket(species.toList()).sendToPlayer(player)
    }

    /**
     * The representation of [FormData] in the context of Showdown.
     * This is intended as a sort of DTO that can be easily converted between JSON and Java/JS objects.
     *
     * @param form The [FormData] being converted into a species (Showdown considers them species).
     */
    @Suppress("unused")
    internal open class ShowdownSpecies(form: FormData) {
        val num = form.species.nationalPokedexNumber
        val name = form.showdownName()
        val types = form.types.map { it.name.replaceFirstChar(Char::uppercase) }
        val eggGroups = form.eggGroups.map { it.showdownID }
        val gender: String? = when (form.maleRatio) {
            0F -> "F"
            1F -> "M"
            -1F, 1.125F -> "N"
            else -> null
        }
        val genderRatio = if (this.gender == null)
            mapOf(
                "M" to (form.maleRatio),
                "F" to (1F - (form.maleRatio))
            ) else null
        val baseStats = Stats.PERMANENT.associateBy({ it.showdownId }, { form.baseStats[it] ?: 1 })
        val heightm = form.height / 10
        val weightkg = form.weight / 10
        // This is ugly, but we already have it hardcoded in the mod anyway
        val maxHP = if (this.num == 292) 1 else null
        val cannotDynamax = form.dynamaxBlocked
        val abilities = form.showdownAbilityMapping()
        // Common fields exempt from standard forms
        val baseSpecies = if (form !is StandardForm) form.species.standardForm.showdownName() else null
        val forme = if (form !is StandardForm) form.name else null

        /**
         * The following fields are optional and currently omitted
         *
         * cosmeticFormes   (not needed, we have species features)
         * addedType        (tertiary type [by name or showdownid] added by moves or abilities)
         * tier             (smogon tryhard bs)
         * doublesTier      (smogon tryhard bs)
         * natDexTier       (smogon tryhard bs)
         * evoType          (the evolution method used to evolve from 'prevo'; trade', 'useItem', 'levelMove', 'levelExtra', 'levelFriendship', 'levelHold', 'other')
         * evoMove          (the move [by name or showdownid] used to evolve from 'prevo')
         * evoLevel         (the minimum level required to evolve from 'prevo')
         * canHatch         (flag for baby pokemon)
         * color            (color, value is arbitrary)
         * tags             (flags for filtering pokemon; 'Mega', 'Mythical', 'Sub-Legendary', 'Restricted Legendary', 'Paradox')
         * unreleasedHidden (whether this pokemon has an unreleased hidden ability)
         * maleOnlyHidden   (whether it is only possible to get the hidden ability on a male pokemon)
         * gmaxUnreleased   (whether this pokemon has an unreleased gmax form)
         * pokemonGoData    (no one cares)
         */
    }

    /**
     * The representation of a [PermanentForm] in the context of Showdown.
     *
     * @param form The [PermanentForm] being converted into a species.
     */
    internal open class ShowdownPermanentForm(form: PermanentForm) : ShowdownSpecies(form) {
        // species this form evolves from
        val prevo = form.preEvolution?.form?.showdownName()
        // For the context of battles the content here doesn't matter whatsoever and due to how PokemonProperties work we can't guarantee an actual specific species is defined.
        val evos = form.evolutions.map { "" }
        // not fully evolved
        val nfe = this.evos.isNotEmpty()
        // whether there is a gmax form to change to
        val canGigantamax: String? = form.changeableForms.filterIsInstance<GigantamaxForm>().firstOrNull()?.name  // TODO id better
    }

    /**
     * The representation of a [StandardForm] in the context of Showdown.
     *
     * @param form The [StandardForm] being converted into a species.
     */
    internal class ShowdownStandardForm(form: StandardForm) : ShowdownPermanentForm(form) {
        // list of all forms descending from this base species
        val otherFormes = form.flattenForms().map { it.showdownName() }
        // list of forms [by name or showdownid] in the order they appear in game [union of baseSpecies, otherFormes, and cosmeticFormes] with the exception of ending with 'Gmax' forms
        val formeOrder = if (this.otherFormes.isNotEmpty()) arrayListOf(this.name, *this.otherFormes.toTypedArray()) else null

        // TODO we don't enforce any form order with our Species registration - right now this looks like only a problem with Mega Rayquaza, but investigate changes needed on Showdown's end to enable these lists to be more dynamic
    }

    /**
     * The representation of a [TemporaryForm] in the context of Showdown.
     *
     * @param form The [TemporaryForm] being converted into a species.
     */
    internal class ShowdownTemporaryForm(form: TemporaryForm) : ShowdownSpecies(form) {
        // form change can occur during a battle
        val battleOnly = if (form.battleOnly) form.parentForm.showdownName() else null
        // form change can occur outside of battle (mutually exclusive with battleOnly)
        val changesFrom = if (!form.battleOnly) form.parentForm.showdownName() else null
        // pokemon must know this move to be in this form
        val requiredMove = form.requirements.filterIsInstance<MoveSetRequirement>().firstOrNull()?.move?.showdownId() // TODO update showdown to support 'requiredMoves'
        // pokemon must hold one of these item(s) to be in this form
        val requiredItems = form.requirements.filterIsInstance<HeldItemRequirement>().firstOrNull()?.let { requirement ->
            try {
                requirement.itemCondition.items.get().map { BuiltInRegistries.ITEM.getKey(it.value()).path }  // TODO held items on showdown are ignorant of namespace
            }
            catch (e: NoSuchElementException)
            {
                Cobblemon.LOGGER.error("Failed to resolve required held item for ${form.showdownId()}}")  // TODO this needs to be done after tags are loaded...
                null
            }
        }
    }

    /** Accommodate for Showdown bullshit. */
    /*
    private fun TemporaryForm.showdownParentForm(): Any {
        if (this.parentOverrides.isNotEmpty())
            return this.species.standardForm.flattenForms().filter {
                parentOverrides.contains(it.showdownId())
            }.map {
                it.showdownName()
            }
        else
            return parentForm.showdownName()
    }

     */

    /**
     * Constructs a mapping of the [PotentialAbility]s belonging to a [FormData] in Showdown identifiable format.
     *
     * @return A map of type (numeric for [CommonAbility]s, H for [HiddenAbility], S for [SpecialAbility]) to showdownid.
     */
    private fun FormData.showdownAbilityMapping(): Map<String, String>? {
        // potential abilities
        val commonAbilities = this.abilities.filterIsInstance<CommonAbility>()
        val hiddenAbility = this.abilities.find { it is HiddenAbility }
        val specialAbility = this.abilities.find { it is SpecialAbility }

        // can have multiple common abilities
        val abilities = mutableMapOf<String, String>()
        val mapping: (String, PotentialAbility) -> Unit =  { key, ability ->
            abilities.put(key, ability.template.showdownId())
        }

        // can only have one hidden and one special ability
        commonAbilities.forEachIndexed { index, ability -> mapping(index.toString(), ability) }
        hiddenAbility?.let { ability -> mapping("H", ability) }
        specialAbility?.let { ability -> mapping("S", ability) }
        return if (abilities.isEmpty()) null else abilities
    }
}
