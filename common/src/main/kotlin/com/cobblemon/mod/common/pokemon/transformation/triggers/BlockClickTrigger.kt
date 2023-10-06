/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.triggers

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.registry.BlockIdentifierCondition
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/**
 * Represents a [ContextTrigger] with [RegistryLikeCondition] of type [Block] context.
 * These are triggered upon interaction with any [Block] that matches the given context.
 *
 * @property requiredContext The [RegistryLikeCondition] of type [Block] expected to match.
 * @author Licious
 * @since October 31st, 2022
 */
open class BlockClickTrigger(
    override val requiredContext: RegistryLikeCondition<Block> =
        BlockIdentifierCondition(ResourceLocation.fromNamespaceAndPath("minecraft", "dirt")),
) : ContextTrigger<BlockClickTrigger.BlockInteractionContext, RegistryLikeCondition<Block>> {

    override fun testContext(pokemon: Pokemon, context: BlockInteractionContext): Boolean {
        return this.requiredContext.fits(context.block, context.world.registryAccess().registryOrThrow(Registries.BLOCK))
    }

    data class BlockInteractionContext(
        val block: Block,
        val world: Level
    )

    companion object {
        const val ADAPTER_VARIANT = "block_click"
    }
}