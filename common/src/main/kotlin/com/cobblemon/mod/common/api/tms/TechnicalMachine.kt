/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.tms.obtain.DefaultObtainMethod
import com.cobblemon.mod.common.util.lang
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class TechnicalMachine(
    val moveName: MoveTemplate,
    val recipe: List<TechnicalMachineRecipe>?,
    val obtainMethods: List<ObtainMethod> = emptyList(),
    val type: String
) {
    companion object {
        /**
         * Filters all available [TechnicalMachines] based on three optional arguments
         *
         * @param search A [String] to check against move names
         * @param type An [ElementalType] to check for
         * @param pokemon A [Pokemon] to check the [Learnset] of
         * @return A [MutableSet] of [TechnicalMachine] that passed the filter
         */
        fun filterTms(
            search: String?,
            type: ElementalType?,
            pokemon: Pokemon?,
            player: Player? = null,
            includeUnlearned: Boolean = false
        ): MutableSet<TechnicalMachine> {
            val tms = TechnicalMachines.tmMap.values.toMutableSet()

            type?.let {
                val iterator = tms.iterator()
                while (iterator.hasNext()) {
                    val tm = iterator.next()
                    if (ElementalTypes.get(tm.type) != type) {
                        iterator.remove()
                    }
                }
            }

            pokemon?.let {
                val iterator = tms.iterator()
                while (iterator.hasNext()) {
                    val tm = iterator.next()
                    if (tm.moveName !in pokemon.form.moves.tmLearnableMoves()) {
                        iterator.remove()
                    }
                }
            }

            search?.let {
                val iterator = tms.iterator()
                while (iterator.hasNext()) {
                    val tm = iterator.next()
                    if (!tm.translatedMoveName().string.contains(search, ignoreCase = true)) {
                        iterator.remove()
                    }
                }
            }

            if (player != null && !includeUnlearned) {
                val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
                tms.retainAll { tm -> tm.isPassivelyObtained() || tm.id in learnedTMs }
            }

            return tms
        }
    }

    lateinit var id: ResourceLocation

    /**
     * Gets the recipe list limited to the provided amount to match the slot amount of the TM Machine.
     *
     * @param limit The amount of recipe item slots to take.
     * @return The recipe list limited to 3 ingredients.
     */
    fun getClampedRecipe(limit: Int = 3): List<TechnicalMachineRecipe>? = recipe?.take(limit)

    /**
     * Unlocks this [TechnicalMachine] for the player.
     *
     * @param player The [ServerPlayer] to give this [TechnicalMachine] to.
     * @return Whether the player was successfully granted the [TechnicalMachine]
     */
    fun unlock(player: ServerPlayer): Boolean {
        if (!obtainMethods.any { it is DefaultObtainMethod }) {
            player.sendSystemMessage(lang("tms.unlock_tm", moveName.displayName))
        }
        return true
    }

    fun isPassivelyObtained() = obtainMethods.any { it is DefaultObtainMethod }

    /**
     * Returns a [MutableComponent] of the translated move name of this [TechnicalMachine]
     *
     * @return This [TechnicalMachine]'s move name, translated
     */
    fun translatedMoveName(): MutableComponent = moveName.displayName

    fun createItemStack(): ItemStack {
        val stack = ItemStack(CobblemonItems.TECHNICAL_MACHINE)
        TMMoveComponent.setTMMove(stack, moveName)
        return stack
    }
}
