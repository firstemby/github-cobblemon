/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.block.PotComponent
import com.cobblemon.mod.common.item.components.BaitEffectsComponent
import com.cobblemon.mod.common.item.components.FlavourComponent
import com.cobblemon.mod.common.item.components.FoodColourComponent
import com.cobblemon.mod.common.item.components.HeldItemCapableComponent
import com.cobblemon.mod.common.item.components.PokemonItemComponent
import com.cobblemon.mod.common.item.components.RodBaitComponent
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

    val POT_DATA: DataComponentType<PotComponent> = create("cooking_pot_item", DataComponentType.builder<PotComponent>()
        .persistent(PotComponent.CODEC)
        .networkSynchronized(PotComponent.PACKET_CODEC)
        .build())

    val BAIT_EFFECTS: DataComponentType<BaitEffectsComponent> = create("bait_effects", DataComponentType.builder<BaitEffectsComponent>()
        .persistent(BaitEffectsComponent.CODEC)
        .networkSynchronized(BaitEffectsComponent.PACKET_CODEC)
        .build())

    val FLAVOUR: DataComponentType<FlavourComponent> = create("flavour", DataComponentType.builder<FlavourComponent>()
        .persistent(FlavourComponent.CODEC)
        .networkSynchronized(FlavourComponent.PACKET_CODEC)
        .build())

    val FOOD_COLOUR: DataComponentType<FoodColourComponent> = create("food_colour", DataComponentType.builder<FoodColourComponent>()
        .persistent(FoodColourComponent.CODEC)
        .networkSynchronized(FoodColourComponent.PACKET_CODEC)
        .build())


    fun register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:pokemon_item"), POKEMON_ITEM)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:bait"), BAIT)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:cooking_pot_item"), POT_DATA)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:bait_effects"), BAIT_EFFECTS)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:flavour"), FLAVOUR)
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.parse("cobblemon:food_colour"), FOOD_COLOUR)
    }

    override val registry = BuiltInRegistries.DATA_COMPONENT_TYPE
    override val resourceKey = Registries.DATA_COMPONENT_TYPE

}