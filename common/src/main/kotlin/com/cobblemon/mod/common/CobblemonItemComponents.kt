/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.item.components.HeldItemCapableComponent
import com.cobblemon.mod.common.item.components.PokemonItemComponent
import com.cobblemon.mod.common.item.RodBaitComponent
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.platform.PlatformRegistry
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

object CobblemonItemComponents : PlatformRegistry<Registry<DataComponentType<*>>, ResourceKey<Registry<DataComponentType<*>>>, DataComponentType<*>>() {

     val POKEMON_ITEM: DataComponentType<PokemonItemComponent> = create("pokemon_item", DataComponentType.builder<PokemonItemComponent>()
        .persistent(PokemonItemComponent.CODEC)
        .networkSynchronized(PokemonItemComponent.PACKET_CODEC)
        .build())

    val HELD_ITEM_REP: DataComponentType<HeldItemCapableComponent> = DataComponentType.builder<HeldItemCapableComponent>()
        .persistent(HeldItemCapableComponent.CODEC)
        .networkSynchronized(HeldItemCapableComponent.PACKET_CODEC)
        .build()

    val BAIT: DataComponentType<RodBaitComponent> = create("bait", DataComponentType.builder<RodBaitComponent>()
        .persistent(RodBaitComponent.CODEC)
        .networkSynchronized(RodBaitComponent.PACKET_CODEC)
        .build())

    val EGG_DATA: DataComponentType<EggComponent> = create("egg", DataComponentType.builder<EggComponent>()
            .persistent(EggComponent.CODEC) // For saving/loading
            .networkSynchronized(EggComponent.PACKET_CODEC) // For network synchronization
            .build())




    fun register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:pokemon_item"), POKEMON_ITEM)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:bait"), BAIT)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:egg_data"), EGG_DATA)
    }

    override val registry = BuiltInRegistries.DATA_COMPONENT_TYPE
    override val resourceKey = Registries.DATA_COMPONENT_TYPE

}