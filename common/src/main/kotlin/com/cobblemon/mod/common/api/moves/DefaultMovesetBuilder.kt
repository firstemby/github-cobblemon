/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * This is all you need tbh. A moveset builder that picks moves from up to 4 slots of move selectors.
 *
 * @author Hiroku
 * @since December 5th, 2025
 */
class DefaultMovesetBuilder : MovesetBuilder {
    companion object {
        val TYPE = cobblemonResource("default")
    }

    override val type: ResourceLocation = TYPE
    override var id: ResourceLocation = cobblemonResource("temp") // replaced on load.

    val slot1: List<MoveSelector> = listOf()
    val slot2: List<MoveSelector> = listOf()
    val slot3: List<MoveSelector> = listOf()
    val slot4: List<MoveSelector> = listOf()

    override fun build(form: FormData, level: Int): MoveSet {
        val chosenMoves = mutableSetOf<MoveTemplate>()
        tryAddMoveFromSlot(form, level, slot1, chosenMoves)
        tryAddMoveFromSlot(form, level, slot2, chosenMoves)
        tryAddMoveFromSlot(form, level, slot3, chosenMoves)
        tryAddMoveFromSlot(form, level, slot4, chosenMoves)
        if (chosenMoves.isEmpty()) {
            chosenMoves.add(Moves.getExceptional())
        }
        val moveset = MoveSet()
        chosenMoves.map(MoveTemplate::create).forEach(moveset::add)
        return moveset
    }

    fun tryAddMoveFromSlot(form: FormData, level: Int, selectors: List<MoveSelector>, chosenMoves: MutableSet<MoveTemplate>) {
        for (selector in selectors) {
            val move = selector(form, form.moves, level, chosenMoves)
            if (move != null) {
                chosenMoves.add(move)
                return
            }
        }
    }
}