/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.vehicle.boat.Boat
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import java.util.function.Supplier

object CobblemonEntities : PlatformRegistry<Registry<EntityType<*>>, ResourceKey<Registry<EntityType<*>>>, EntityType<*>>() {


    override val registry: Registry<EntityType<*>> = BuiltInRegistries.ENTITY_TYPE
    override val resourceKey: ResourceKey<Registry<EntityType<*>>> = Registries.ENTITY_TYPE

    @JvmField
    val POKEMON_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("pokemon"))
    @JvmField
    val POKEMON: EntityType<PokemonEntity> = this.create(
        POKEMON_KEY.identifier().path,
        EntityType.Builder.of({ _, world -> PokemonEntity(world) }, MobCategory.CREATURE)
            .build(POKEMON_KEY)
    )

    @JvmField
    val EMPTY_POKEBALL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("empty_pokeball"))
    @JvmField
    val EMPTY_POKEBALL: EntityType<EmptyPokeBallEntity> = this.create(
        EMPTY_POKEBALL_KEY.identifier().path,
        EntityType.Builder.of({ _, world -> EmptyPokeBallEntity(PokeBalls.POKE_BALL, world) }, MobCategory.MISC)
            .build(EMPTY_POKEBALL_KEY)
    )

    @JvmField
    val APRICORN_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("boat"))
    @JvmField
    val APRICORN_BOAT: EntityType<Boat> = this.create(
        APRICORN_BOAT_KEY.identifier().path,
        EntityType.Builder.of(boatFactory { CobblemonItems.APRICORN_BOAT }, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10)
            .build(APRICORN_BOAT_KEY)
    )

    @JvmField
    val APRICORN_CHEST_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("chest_boat"))
    @JvmField
    val APRICORN_CHEST_BOAT: EntityType<ChestBoat> = this.create(
        APRICORN_CHEST_BOAT_KEY.identifier().path,
        EntityType.Builder.of(chestBoatFactory { CobblemonItems.APRICORN_CHEST_BOAT }, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10)
            .build(APRICORN_CHEST_BOAT_KEY)
    )

    @JvmField
    val SACCHARINE_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("boat"))
    @JvmField
    val SACCHARINE_BOAT: EntityType<Boat> = this.create(
        SACCHARINE_BOAT_KEY.identifier().path,
        EntityType.Builder.of(boatFactory { CobblemonItems.SACCHARINE_BOAT }, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10)
            .build(SACCHARINE_BOAT_KEY)
    )

    @JvmField
    val SACCHARINE_CHEST_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("chest_boat"))
    @JvmField
    val SACCHARINE_CHEST_BOAT: EntityType<ChestBoat> = this.create(
        SACCHARINE_CHEST_BOAT_KEY.identifier().path,
        EntityType.Builder.of(chestBoatFactory { CobblemonItems.SACCHARINE_CHEST_BOAT }, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10)
            .build(SACCHARINE_CHEST_BOAT_KEY)
    )

    @JvmField
    val POKE_BOBBER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("poke_bobber"))
    @JvmField
    val POKE_BOBBER: EntityType<PokeRodFishingBobberEntity> = this.create(
            POKE_BOBBER_KEY.identifier().path,
            EntityType.Builder.of(::PokeRodFishingBobberEntity, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10)
                    .build(POKE_BOBBER_KEY)
    )

    @JvmField
    val GENERIC_BEDROCK_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("generic_bedrock"))
    @JvmField
    val GENERIC_BEDROCK_ENTITY: EntityType<GenericBedrockEntity> = this.create(
        GENERIC_BEDROCK_ENTITY_KEY.identifier().path,
        EntityType.Builder.of({ _, world -> GenericBedrockEntity(world) }, MobCategory.MISC)
            .build(GENERIC_BEDROCK_ENTITY_KEY)
    )

    @JvmField
    val NPC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, cobblemonResource("npc"))
    @JvmField
    val NPC: EntityType<NPCEntity> = create(
        NPC_KEY.identifier().path,
        EntityType.Builder.of({ _, world -> NPCEntity(world) }, MobCategory.CREATURE).build(NPC_KEY)
    )

    fun registerAttributes(consumer: (EntityType<out LivingEntity>, AttributeSupplier.Builder) -> Unit) {
        consumer(POKEMON, PokemonEntity.createAttributes())
        consumer(NPC, NPCEntity.createAttributes())
    }

    private fun boatFactory(supplier: Supplier<Item>): EntityType.EntityFactory<Boat> {
        return EntityType.EntityFactory { entityType: EntityType<Boat>, level: Level ->
            Boat(
                entityType,
                level,
                supplier
            )
        }
    }

    private fun chestBoatFactory(supplier: Supplier<Item>): EntityType.EntityFactory<ChestBoat> {
        return EntityType.EntityFactory { entityType: EntityType<ChestBoat>, level: Level ->
            ChestBoat(
                entityType,
                level,
                supplier
            )
        }
    }
}
