/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.client.pot.CookingQuality
import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

object CobblemonModelPredicateRegistry {
    val ROD_CAST = cobblemonResource("cast")
    val PONIGIRI_OVERLAY = cobblemonResource("ponigiri_overlay")
    val APRIJUICE_QUALITY = cobblemonResource("aprijuice_quality")

    fun registerPredicates() {
        // Legacy client setup hook kept for parity with other loaders.
        // 1.21.11 NeoForge now registers these through item model property events.
    }

    object PokerodCastProperty : ConditionalItemModelProperty {
        val MAP_CODEC: MapCodec<PokerodCastProperty> = MapCodec.unit(this)

        override fun get(
            stack: ItemStack,
            world: ClientLevel?,
            entity: LivingEntity?,
            seed: Int,
            displayContext: ItemDisplayContext,
        ): Boolean {
            if (entity !is Player || entity.fishing !is PokeRodFishingBobberEntity) {
                return false
            }

            val rodId = entity.fishing!!.entityData.get(PokeRodFishingBobberEntity.POKEROD_ID)
            val isMainHand = stack == entity.mainHandItem
            var isOffHand = stack == entity.offhandItem

            val mainHandItem = entity.mainHandItem.item
            val isFishingWithMainHand = mainHandItem is PokerodItem && rodId == mainHandItem.pokeRodId.toString()
            if (isFishingWithMainHand) {
                isOffHand = false
            }

            return (isMainHand && isFishingWithMainHand) || isOffHand
        }

        override fun type() = MAP_CODEC
    }

    object PonigiriOverlayProperty : SelectItemModelProperty<Identifier> {
        val TYPE = SelectItemModelProperty.Type.create(MapCodec.unit(this), Identifier.CODEC)

        override fun get(
            stack: ItemStack,
            world: ClientLevel?,
            entity: LivingEntity?,
            seed: Int,
            displayContext: ItemDisplayContext,
        ): Identifier? {
            val ingredientId = stack.get(CobblemonItemComponents.INGREDIENT)?.ingredientIds?.firstOrNull() ?: return null
            return if (ingredientId == cobblemonResource("rice")) null else ingredientId
        }

        override fun valueCodec() = Identifier.CODEC

        override fun type() = TYPE
    }

    object AprijuiceQualityProperty : SelectItemModelProperty<String> {
        val TYPE = SelectItemModelProperty.Type.create(MapCodec.unit(this), Codec.STRING)

        override fun get(
            stack: ItemStack,
            world: ClientLevel?,
            entity: LivingEntity?,
            seed: Int,
            displayContext: ItemDisplayContext,
        ): String? {
            val rideBoostsComponent = stack.get(CobblemonItemComponents.RIDE_BOOST) ?: return null
            if (rideBoostsComponent.boosts.isEmpty()) {
                return null
            }

            return when (rideBoostsComponent.getQuality()) {
                CookingQuality.MEDIUM -> "medium"
                CookingQuality.HIGH -> "high"
                CookingQuality.LOW -> null
            }
        }

        override fun valueCodec() = Codec.STRING

        override fun type() = TYPE
    }
}
