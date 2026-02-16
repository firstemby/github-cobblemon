/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.api.abilities.AbilityTemplate
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokedex.widgets.InfoTextScrollWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

class PokeEditAbilityTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft
) : SoundlessWidget(pX, pY, 139, 163, lang("ui.pokeedit.abilities")) {
    companion object {
        private val arrowLeft = cobblemonResource("textures/gui/pokedex/info_arrow_left.png")
        private val arrowRight = cobblemonResource("textures/gui/pokedex/info_arrow_right.png")
    }

    private val description = object : InfoTextScrollWidget(pX, pY + 60) {}

    private val leftButton = ScaledButton(
        pX + 2.5F,
        pY + 30F,
        7,
        10,
        arrowLeft,
        clickAction = { switchAbility(false) }
    ).also { addWidget(it) }

    private val rightButton = ScaledButton(
        pX + 133F,
        pY + 30F,
        7,
        10,
        arrowRight,
        clickAction = { switchAbility(true) }
    ).also { addWidget(it) }

    private var abilitiesList: List<AbilityTemplate> = emptyList()

    init {
        refreshAbilities()
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        refreshAbilities()
        if (abilitiesList.isEmpty()) return

        val ability = abilitiesList[draft.abilityIndex.coerceIn(0, abilitiesList.lastIndex)]

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Component.translatable("cobblemon.ui.pokedex.info.ability", ability.displayName.asTranslated()).bold(),
            x = x + 9,
            y = y + 30,
            shadow = true
        )

        description.setText(listOf(ability.description))
        description.render(context, mouseX, mouseY, delta)

        leftButton.render(context, mouseX, mouseY, delta)
        rightButton.render(context, mouseX, mouseY, delta)
    }

    private fun refreshAbilities() {
        val form = draft.form
        abilitiesList = form.abilities.map { it.template }
        if (draft.abilityIndex !in abilitiesList.indices) {
            draft.abilityIndex = 0
        }
    }

    private fun switchAbility(nextIndex: Boolean) {
        if (abilitiesList.isEmpty()) return
        val size = abilitiesList.size
        draft.abilityIndex = if (nextIndex) {
            (draft.abilityIndex + 1) % size
        } else {
            (draft.abilityIndex - 1 + size) % size
        }
    }
}
