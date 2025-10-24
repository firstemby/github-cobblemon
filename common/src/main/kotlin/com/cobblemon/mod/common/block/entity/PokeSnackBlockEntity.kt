/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.getColourMixFromFlavours
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.fishing.SpawnBait.Effect
import com.cobblemon.mod.common.api.fishing.SpawnBaitEffects
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.item.components.BaitEffectsComponent
import com.cobblemon.mod.common.item.components.FlavourComponent
import com.cobblemon.mod.common.item.components.IngredientComponent
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import kotlin.collections.orEmpty

open class PokeSnackBlockEntity(pos: BlockPos, state: BlockState) : TintBlockEntity(CobblemonBlockEntities.POKE_SNACK, pos, state) {

    companion object {
        const val SPAWNS_PER_BITE = 4
    }

    var amountSpawned: Int = 0
    var flavourComponent: FlavourComponent? = null
    var baitEffectsComponent: BaitEffectsComponent? = null
    var ingredientComponent: IngredientComponent? = null

    fun initializeFromItemStack(itemStack: ItemStack) {
        flavourComponent = itemStack.get(CobblemonItemComponents.FLAVOUR)
        ingredientComponent = itemStack.get(CobblemonItemComponents.INGREDIENT)
        if (isLure()) baitEffectsComponent = itemStack.get(CobblemonItemComponents.BAIT_EFFECTS)

        flavourComponent?.let {
            getColourMixFromFlavours(it.getDominantFlavours())?.let {
                setTint(it)
            }
        }
    }

    fun isLure(): Boolean {
        val block = blockState.block
        if (block is PokeSnackBlock) {
            return block.isLure
        }
        return false
    }

    fun toItemStack(): ItemStack {
        val stack = ItemStack(this.blockState.block)

        if (isLure() && baitEffectsComponent != null) {
            stack.set(CobblemonItemComponents.BAIT_EFFECTS, baitEffectsComponent)
        }

        if (flavourComponent != null) {
            stack.set(CobblemonItemComponents.FLAVOUR, flavourComponent)
        }

        if (ingredientComponent != null) {
            stack.set(CobblemonItemComponents.INGREDIENT, ingredientComponent)
        }

        return stack
    }

    /**
     * Combine all the [SpawnBait.Effect] values from the [baitEffectsComponent] data.
     */
    fun getBaitEffects(): List<Effect> {
        return baitEffectsComponent?.effects?.mapNotNull(SpawnBaitEffects::getFromIdentifier)?.flatMap { it.effects }.orEmpty()
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        tag.putInt(DataKeys.AMOUNT_SPAWNED, amountSpawned)

        flavourComponent?.let { component ->
            CobblemonItemComponents.FLAVOUR.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                    tag.put(DataKeys.FLAVOUR, encodedTag)
                }
        }

        baitEffectsComponent?.let { component ->
            CobblemonItemComponents.BAIT_EFFECTS.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                    tag.put(DataKeys.BAIT_EFFECTS, encodedTag)
                }
        }

        ingredientComponent?.let { component ->
            CobblemonItemComponents.INGREDIENT.codec()!!
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .ifPresent { encodedTag ->
                    tag.put(DataKeys.INGREDIENTS, encodedTag)
                }
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        amountSpawned = tag.getInt(DataKeys.AMOUNT_SPAWNED)

        if (tag.contains(DataKeys.FLAVOUR)) {
            CobblemonItemComponents.FLAVOUR.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.FLAVOUR))
                ?.result()
                ?.ifPresent { component ->
                    flavourComponent = component
                }
        }

        if (tag.contains(DataKeys.BAIT_EFFECTS)) {
            CobblemonItemComponents.BAIT_EFFECTS.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.BAIT_EFFECTS))
                ?.result()
                ?.ifPresent { component ->
                    baitEffectsComponent = component
                }
        }

        if (tag.contains(DataKeys.INGREDIENTS)) {
            CobblemonItemComponents.INGREDIENT.codec()
                ?.parse(NbtOps.INSTANCE, tag.get(DataKeys.INGREDIENTS))
                ?.result()
                ?.ifPresent { component ->
                    ingredientComponent = component
                }
        }
    }
}
