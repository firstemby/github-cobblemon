/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client

import com.cobblemon.mod.common.client.render.item.CobblemonItemTintSources
import com.cobblemon.mod.common.client.render.item.CobblemonModelPredicateRegistry
import com.cobblemon.mod.common.client.render.item.PokemonItemSpecialModelRenderer
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties
import net.minecraft.client.renderer.special.SpecialModelRenderers
import net.minecraft.resources.Identifier

object CobblemonFabricItemModelRegistrations {
    fun register() {
        register(
            ConditionalItemModelProperties::class.java,
            CobblemonModelPredicateRegistry.ROD_CAST,
            CobblemonModelPredicateRegistry.PokerodCastProperty.MAP_CODEC,
        )
        register(
            SelectItemModelProperties::class.java,
            CobblemonModelPredicateRegistry.PONIGIRI_OVERLAY,
            CobblemonModelPredicateRegistry.PonigiriOverlayProperty.TYPE,
        )
        register(
            SelectItemModelProperties::class.java,
            CobblemonModelPredicateRegistry.APRIJUICE_QUALITY,
            CobblemonModelPredicateRegistry.AprijuiceQualityProperty.TYPE,
        )
        register(
            ItemTintSources::class.java,
            CobblemonItemTintSources.FOOD_COLOUR_MIX,
            CobblemonItemTintSources.FoodColourMixTintSource.MAP_CODEC,
        )
        register(
            ItemTintSources::class.java,
            CobblemonItemTintSources.FOOD_COLOUR_ENTRY,
            CobblemonItemTintSources.FoodColourEntryTintSource.MAP_CODEC,
        )
        register(
            ItemTintSources::class.java,
            CobblemonItemTintSources.RIDE_BOOST_MIX,
            CobblemonItemTintSources.RideBoostMixTintSource.MAP_CODEC,
        )
        register(
            SpecialModelRenderers::class.java,
            cobblemonResource("pokemon"),
            PokemonItemSpecialModelRenderer.Unbaked.MAP_CODEC,
        )
    }

    private fun register(owner: Class<*>, id: Identifier, codec: Any) {
        try {
            val field = owner.getDeclaredField("ID_MAPPER")
            field.isAccessible = true
            val mapper = field.get(null)
            val put = mapper.javaClass.getDeclaredMethod("put", Any::class.java, Any::class.java)
            put.isAccessible = true
            put.invoke(mapper, id, codec)
        } catch (exception: ReflectiveOperationException) {
            throw IllegalStateException("Failed to register Cobblemon item model entry $id on ${owner.name}", exception)
        }
    }
}
