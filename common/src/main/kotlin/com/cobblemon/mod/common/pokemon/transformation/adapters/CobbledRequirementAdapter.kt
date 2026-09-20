/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.adapters

import com.cobblemon.mod.common.api.interaction.InteractionEffect
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.adapters.RequirementAdapter
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.requirements.AdvancementRequirement
import com.cobblemon.mod.common.pokemon.requirements.ChanceRequirement
import com.cobblemon.mod.common.pokemon.requirements.OwnerHoldsItemRequirement
import com.cobblemon.mod.common.pokemon.requirements.StatCompareRequirement
import com.cobblemon.mod.common.pokemon.requirements.StatEqualRequirement
import com.cobblemon.mod.common.pokemon.transformation.requirements.*
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.InteractionEffectAdapter
import com.cobblemon.mod.common.util.adapters.pokemonPropertiesShortAdapter
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import com.google.common.collect.HashBiMap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import java.lang.reflect.Type
import kotlin.reflect.KClass
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.LowerCaseEnumTypeAdapterFactory

/**
 * The default implementation of [RequirementAdapter].
 *
 * @author Licious
 * @since March 21st, 2022
 */
object CobblemonRequirementAdapter : RequirementAdapter {

    private const val VARIANT = "variant"

    private val types = HashBiMap.create<String, KClass<out TransformationRequirement>>()

    init {
        this.registerType(AreaRequirement.ADAPTER_VARIANT, AreaRequirement::class)
        this.registerType(BiomeRequirement.ADAPTER_VARIANT, BiomeRequirement::class)
        this.registerType(FriendshipRequirement.ADAPTER_VARIANT, FriendshipRequirement::class)
        this.registerType(HeldItemRequirement.ADAPTER_VARIANT, HeldItemRequirement::class)
        this.registerType(WorldRequirement.ADAPTER_VARIANT, WorldRequirement::class)
        this.registerType(MoveSetRequirement.ADAPTER_VARIANT, MoveSetRequirement::class)
        this.registerType(MoveTypeRequirement.ADAPTER_VARIANT, MoveTypeRequirement::class)
        this.registerType(PartyMemberRequirement.ADAPTER_VARIANT, PartyMemberRequirement::class)
        this.registerType(PokemonPropertiesRequirement.ADAPTER_VARIANT, PokemonPropertiesRequirement::class)
        this.registerType(TimeRangeRequirement.ADAPTER_VARIANT, TimeRangeRequirement::class)
        this.registerType(LevelRequirement.ADAPTER_VARIANT, LevelRequirement::class)
        this.registerType(WeatherRequirement.ADAPTER_VARIANT, WeatherRequirement::class)
        this.registerType(StatCompareRequirement.ADAPTER_VARIANT, StatCompareRequirement::class)
        this.registerType(StatEqualRequirement.ADAPTER_VARIANT, StatEqualRequirement::class)
        this.registerType(AttackDefenceRatioRequirement.ADAPTER_VARIANT, AttackDefenceRatioRequirement::class)
        this.registerType(BattleCriticalHitsRequirement.ADAPTER_VARIANT, BattleCriticalHitsRequirement::class)
        this.registerType(DamageTakenRequirement.ADAPTER_VARIANT, DamageTakenRequirement::class)
        this.registerType(UseMoveRequirement.ADAPTER_VARIANT, UseMoveRequirement::class)
        this.registerType(MoonPhaseRequirement.ADAPTER_VARIANT, MoonPhaseRequirement::class)
        this.registerType(RecoilRequirement.ADAPTER_VARIANT, RecoilRequirement::class)
        this.registerType(DefeatRequirement.ADAPTER_VARIANT, DefeatRequirement::class)
        this.registerType(BlocksTraveledRequirement.ADAPTER_VARIANT, BlocksTraveledRequirement::class)
        this.registerType(StructureRequirement.ADAPTER_VARIANT, StructureRequirement::class)
        this.registerType(AnyRequirement.ADAPTER_VARIANT, AnyRequirement::class)
        this.registerType(PropertyRangeRequirement.ADAPTER_VARIANT, PropertyRangeRequirement::class)
        this.registerType(PlayerHasAdvancementRequirement.ADAPTER_VARIANT, PlayerHasAdvancementRequirement::class)
        this.registerType(HealthRequirement.ADAPTER_VARIANT, HealthRequirement::class)
        this.registerType(AdvancementRequirement.ADAPTER_VARIANT, AdvancementRequirement::class)
        this.registerType(OwnerHoldsItemRequirement.ADAPTER_VARIANT, OwnerHoldsItemRequirement::class)
        this.registerType(ChanceRequirement.ADAPTER_VARIANT, ChanceRequirement::class)
        this.registerType(MolangRequirement.ADAPTER_VARIANT, MolangRequirement::class)
    }

    val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .registerTypeAdapter(PokemonProperties::class.java, pokemonPropertiesShortAdapter)
            .registerTypeAdapter(ItemPredicate::class.java, LegacyItemConditionWrapperAdapter)
            .registerTypeAdapter(TransformationRequirement::class.java, CobblemonRequirementAdapter)
            .registerTypeAdapter(InteractionEffect::class.java, InteractionEffectAdapter)
            .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
            .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
            .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory())
            .setPrettyPrinting()
            .create()
    }

    override fun <T : TransformationRequirement> registerType(id: String, type: KClass<T>) {
        this.types[id.lowercase()] = type
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TransformationRequirement {
        val variant = json.asJsonObject.get(VARIANT).asString.lowercase()
        val type = this.types[variant] ?: throw IllegalArgumentException("Cannot resolve transformation requirement type for variant $variant")
        return context.deserialize(json, type.java)
    }

    override fun serialize(src: TransformationRequirement, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val json = context.serialize(src, src::class.java).asJsonObject
        val variant = this.types.inverse()[src::class] ?: throw IllegalArgumentException("Cannot resolve transformation requirement for type ${src::class.qualifiedName}")
        json.addProperty(VARIANT, variant)
        return json
    }

    fun encode(requirement: TransformationRequirement, buffer: FriendlyByteBuf) {
        buffer.writeString(gson.toJson(requirement, TransformationRequirement::class.java))
    }

    fun decode(buffer: FriendlyByteBuf): TransformationRequirement {
        return gson.fromJson(buffer.readString(), TransformationRequirement::class.java)
    }

}