/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.ExitButton
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.TAB_ICON_SIZE
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditEVIVTab
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditGeneralTab
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditMovesTab
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditSpeciesList
import com.cobblemon.mod.common.client.gui.pokedex.widgets.SearchWidget
import com.cobblemon.mod.common.client.gui.summary.SummaryButton
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.pokeedit.PokeEditAction
import com.cobblemon.mod.common.net.messages.server.pokeedit.PokeEditActionPacket
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeatureProvider
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.pokemon.properties.AspectPropertyType
import com.cobblemon.mod.common.pokemon.OriginalTrainerType
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class PokeEditGUI private constructor() : Screen(Component.translatable("cobblemon.ui.pokeedit.title")) {
    companion object {
        private val screenBackground = cobblemonResource("textures/gui/pokedex/pokedex_screen.png")
        private val baseBackground = cobblemonResource("textures/gui/pokedex/pokedex_base_red.png")

        private val tabSelectArrow = cobblemonResource("textures/gui/pokedex/select_arrow.png")
        private val actionButtonResource = cobblemonResource("textures/gui/summary/summary_evolve_button.png")
        private val tabIcons = arrayOf(
            cobblemonResource("textures/gui/pokedex/tab_info.png"),
            cobblemonResource("textures/gui/pokedex/tab_stats.png"),
            cobblemonResource("textures/gui/pokedex/tab_moves.png"),
            cobblemonResource("textures/gui/pokedex/tab_species.png"),
        )

        const val TAB_GENERAL = 0
        const val TAB_EV_IV = 1
        const val TAB_MOVES = 2
        const val TAB_ASPECTS = 3

        fun open() {
            Minecraft.getInstance().setScreen(PokeEditGUI())
        }
    }

    private val allSpecies = PokemonSpecies.species.sortedBy { it.nationalPokedexNumber }
    private var filteredSpecies = allSpecies

    private lateinit var searchWidget: SearchWidget
    private lateinit var speciesList: PokeEditSpeciesList

    private val tabButtons: MutableList<ScaledButton> = mutableListOf()
    private var tabIndex = TAB_GENERAL
    private lateinit var tabElement: AbstractWidget

    private lateinit var createButton: SummaryButton
    private lateinit var spawnButton: SummaryButton
    private lateinit var exitButton: ExitButton

    private val draft = PokeEditDraft(allSpecies.firstOrNull() ?: PokemonSpecies.random())

    override fun renderBlurredBackground(delta: Float) {}
    override fun renderMenuBackground(context: GuiGraphics) {}
    override fun isPauseScreen() = false

    override fun init() {
        super.init()
        clearWidgets()

        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2

        if (::searchWidget.isInitialized) removeWidget(searchWidget)
        searchWidget = SearchWidget(x + 26, y + 28, 128, 11, update = ::updateFilters)
        addRenderableWidget(searchWidget)

        if (::speciesList.isInitialized) removeWidget(speciesList)
        speciesList = PokeEditSpeciesList(x + 26, y + 39, ::setSelectedSpecies)
        addRenderableWidget(speciesList)

        setUpTabs()
        setUpActionButtons()
        updateFilters(true)
        displayTab(tabIndex, recreate = true)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrices = context.pose()
        renderBackground(context, mouseX, mouseY, delta)

        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2

        blitk(
            matrixStack = matrices,
            texture = baseBackground,
            x = x,
            y = y,
            width = BASE_WIDTH,
            height = BASE_HEIGHT
        )

        blitk(
            matrixStack = matrices,
            texture = screenBackground,
            x = x,
            y = y,
            width = BASE_WIDTH,
            height = BASE_HEIGHT
        )

        drawScaledText(
            context = context,
            text = lang("ui.pokeedit.title"),
            x = x + 192,
            y = y + 15,
            shadow = true
        )

        super.render(context, mouseX, mouseY, delta)

        tabButtons.forEach { it.render(context, mouseX, mouseY, delta) }

        // Draw the tab arrow above overlay widgets so it stays visible.
        blitk(
            matrixStack = matrices,
            texture = tabSelectArrow,
            x = (x + 198 + (25 * tabIndex)) / SCALE,
            y = (y + 177) / SCALE,
            width = 12,
            height = 6,
            scale = SCALE
        )
    }

    private fun updateFilters(init: Boolean = false) {
        val search = searchWidget.value.trim()
        filteredSpecies = if (search.isEmpty()) {
            allSpecies
        } else {
            allSpecies.filter { species ->
                species.translatedName.string.contains(search, true)
                    || species.resourceIdentifier.path.contains(search, true)
            }
        }

        speciesList.setEntries(filteredSpecies)

        if (init || draft.species !in filteredSpecies) {
            filteredSpecies.firstOrNull()?.let { setSelectedSpecies(it) }
        }
    }

    private fun setSelectedSpecies(species: com.cobblemon.mod.common.pokemon.Species) {
        if (draft.species == species) return
        draft.species = species
        draft.form = species.standardForm
        draft.gender = PokeEditDraft.defaultGenderFor(draft.form)
        draft.shiny = false
        draft.abilityIndex = 0
        val aspectOptions = PokeEditAspectsTab.collectAspectOptions(
            species,
            draft.form,
            draft.gender,
            draft.shiny
        )
        draft.forcedAspects.retainAll(aspectOptions.toggles)
        draft.flagFeatures.retainAll(aspectOptions.flagKeys)
        draft.choiceFeatures.entries.removeIf { (key, choice) ->
            val group = aspectOptions.choiceGroups.firstOrNull { it.key == key } ?: return@removeIf true
            choice !in group.choices
        }
        val cosmetic = aspectOptions.cosmeticChoices.firstOrNull { it.itemId.toString() == draft.cosmeticItem }
        draft.cosmeticItem = cosmetic?.itemId?.toString()
        draft.cosmeticAspects = cosmetic?.aspects?.toSet() ?: emptySet()
        displayTab(tabIndex, recreate = true)
    }

    private fun setUpTabs() {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2

        tabButtons.clear()
        for (i in tabIcons.indices) {
            tabButtons.add(
                ScaledButton(
                    x + 197F + (i * 25F),
                    y + 181.5F,
                    TAB_ICON_SIZE,
                    TAB_ICON_SIZE,
                    resource = tabIcons[i],
                    clickAction = { displayTab(i) }
                )
            )
        }
        tabButtons.forEach(::addRenderableWidget)
    }

    private fun setUpActionButtons() {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        val actionY = y + 200F
        val exitX = x + 312F
        val spawnX = exitX - 56F
        val createX = spawnX - 56F

        if (::createButton.isInitialized) removeWidget(createButton)
        createButton = SummaryButton(
            buttonX = createX,
            buttonY = actionY,
            buttonWidth = 54,
            buttonHeight = 15,
            clickAction = { sendPokeEditAction(PokeEditAction.CREATE) },
            text = lang("ui.pokeedit.give"),
            resource = actionButtonResource
        )
        addRenderableWidget(createButton)

        if (::spawnButton.isInitialized) removeWidget(spawnButton)
        spawnButton = SummaryButton(
            buttonX = spawnX,
            buttonY = actionY,
            buttonWidth = 54,
            buttonHeight = 15,
            clickAction = { sendPokeEditAction(PokeEditAction.SPAWN) },
            text = lang("ui.pokeedit.spawn"),
            resource = actionButtonResource
        )
        addRenderableWidget(spawnButton)

        if (::exitButton.isInitialized) removeWidget(exitButton)
        exitButton = ExitButton(pX = exitX.toInt(), pY = y + 200) {
            Minecraft.getInstance().setScreen(null)
        }
        addRenderableWidget(exitButton)
    }

    private fun displayTab(newIndex: Int, recreate: Boolean = false) {
        if (tabIndex == newIndex && ::tabElement.isInitialized && !recreate) {
            return
        }

        tabIndex = newIndex
        if (::tabElement.isInitialized) removeWidget(tabElement)

        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        val tabX = x + 180
        val tabY = y + 28

        tabElement = when (newIndex) {
            TAB_GENERAL -> PokeEditGeneralTab(tabX, tabY, draft) { displayTab(tabIndex, recreate = true) }
            TAB_EV_IV -> PokeEditEVIVTab(tabX, tabY, draft)
            TAB_MOVES -> PokeEditMovesTab(tabX, tabY, draft)
            TAB_ASPECTS -> PokeEditAspectsTab(tabX, tabY, draft)
            else -> PokeEditGeneralTab(tabX, tabY, draft) { displayTab(tabIndex, recreate = true) }
        }

        addRenderableWidget(tabElement)
        bringControlsToFront()
    }

    private fun bringControlsToFront() {
        if (::createButton.isInitialized) {
            removeWidget(createButton)
            addRenderableWidget(createButton)
        }
        if (::spawnButton.isInitialized) {
            removeWidget(spawnButton)
            addRenderableWidget(spawnButton)
        }
        if (::exitButton.isInitialized) {
            removeWidget(exitButton)
            addRenderableWidget(exitButton)
        }
        if (tabButtons.isEmpty()) return
        tabButtons.forEach { removeWidget(it) }
        tabButtons.forEach(::addRenderableWidget)
    }

    private fun sendPokeEditAction(action: PokeEditAction) {
        val properties = buildDraftProperties()
        val moveNames = draft.activeMoves.map { it?.name ?: "" }
        val cosmeticItem = draft.cosmeticItem?.trim().orEmpty()
        PokeEditActionPacket(action, properties.asString(), moveNames, cosmeticItem).sendToServer()
        Minecraft.getInstance().setScreen(null)
    }

    private fun buildDraftProperties(): PokemonProperties {
        val props = PokemonProperties()
        props.species = draft.species.resourceIdentifier.toString()
        props.form = draft.form.formOnlyShowdownId()
        props.shiny = draft.shiny
        props.gender = draft.gender
        props.level = draft.level
        val ot = draft.originalTrainer.trim()
        if (ot.isBlank()) {
            props.originalTrainerType = OriginalTrainerType.NONE
        } else {
            props.originalTrainerType = OriginalTrainerType.NPC
            props.originalTrainer = ot
        }
        props.heldItem = draft.heldItem?.takeIf { it.isNotBlank() }
        props.ivs = draft.ivs
        props.evs = draft.evs
        val abilities = draft.form.abilities.map { it.template }
        props.ability = abilities.getOrNull(draft.abilityIndex)?.name
        val customProperties = mutableListOf<com.cobblemon.mod.common.api.properties.CustomPokemonProperty>()
        draft.forcedAspects.mapNotNullTo(customProperties) { aspect -> AspectPropertyType.fromString(aspect) }
        draft.flagFeatures.forEach { key ->
            val provider = SpeciesFeatures.getFeature(key) as? FlagSpeciesFeatureProvider ?: return@forEach
            provider.fromString("true")?.let { customProperties.add(it) }
        }
        draft.choiceFeatures.forEach { (key, choice) ->
            val provider = SpeciesFeatures.getFeature(key) as? ChoiceSpeciesFeatureProvider ?: return@forEach
            provider.fromString(choice)?.let { customProperties.add(it) }
        }
        props.customProperties = customProperties
        props.updateAspects()
        return props
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (focused !is EditBox && (minecraft?.options?.keyInventory?.matches(keyCode, scanCode) == true)) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return try {
            super.mouseClicked(mouseX, mouseY, button)
        } catch (_: ConcurrentModificationException) {
            false
        }
    }
}
