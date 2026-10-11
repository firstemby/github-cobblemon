/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.emi

import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent

class CobblemonEmiIngredient<A: EmiIngredient>(val base: EmiIngredient, val displayed: A): EmiIngredient {
    override fun copy(): EmiIngredient {
        return CobblemonEmiIngredient(base.copy(), displayed.copy())
    }

    override fun getAmount(): Long = base.amount
    override fun setAmount(amount: Long): EmiIngredient {
        base.amount = amount
        return this
    }

    override fun getChance(): Float = base.chance
    override fun setChance(chance: Float): EmiIngredient {
        base.chance = chance
        return this
    }

    override fun getEmiStacks(): List<EmiStack> = base.emiStacks
    override fun getTooltip(): List<ClientTooltipComponent> = displayed.tooltip

    override fun render(draw: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        displayed.render(draw, x, y, delta, flags)
    }
}