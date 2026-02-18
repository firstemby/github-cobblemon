/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class StadiumConfigScreen(
    private val blockPos: BlockPos,
    private val data: StadiumBlockData
) : Screen(Component.translatable("cobblemon.stadium_config.title")) {

    companion object {
        // Panel styling
        private const val PANEL_BG = 0xFF2D2D44.toInt()
        private const val PANEL_BORDER = 0xFF5C5C7A.toInt()
        private const val LABEL_COLOR = 0xA0A0B8
        private const val TITLE_COLOR = 0xFFFFFF
        private const val DIVIDER_COLOR = 0xFF4A4A66.toInt()

        // Default format options
        val DEFAULT_FORMATS = listOf(
            "anything_goes" to "Anything Goes",
            "singles" to "Singles",
            "vgc_doubles" to "VGC Doubles",
            "ou_singles" to "OU Singles",
            "ubers" to "Ubers",
            "uu_singles" to "UU Singles",
            "little_cup" to "Little Cup"
        )

        // Battle type options
        val BATTLE_TYPES = listOf("singles", "doubles", "triples", "multi")

        // Clause options
        val CLAUSES = listOf(
            "sleep_clause", "species_clause", "evasion_clause", "ohko_clause",
            "moody_clause", "endless_battle_clause", "accuracy_clause"
        )

        // Gimmick options
        val GIMMICKS = listOf("mega_evolution", "z_moves", "dynamax", "terastallization")

        // Sample ban lists (hardcoded for now)
        val SAMPLE_POKEMON = listOf(
            "mewtwo", "mew", "lugia", "ho-oh", "kyogre", "groudon", "rayquaza",
            "dialga", "palkia", "giratina", "arceus", "zekrom", "reshiram",
            "xerneas", "yveltal", "zygarde", "solgaleo", "lunala", "necrozma",
            "zacian", "zamazenta", "eternatus", "calyrex", "koraidon", "miraidon"
        )
        val SAMPLE_MOVES = listOf(
            "baton_pass", "swagger", "double_team", "minimize", "fissure",
            "horn_drill", "guillotine", "sheer_cold", "dark_void", "sketch"
        )
        val SAMPLE_ABILITIES = listOf(
            "moody", "shadow_tag", "arena_trap", "power_construct",
            "battle_bond", "sand_veil", "snow_cloak"
        )
        val SAMPLE_ITEMS = listOf(
            "kings_rock", "razor_fang", "bright_powder", "lax_incense",
            "soul_dew", "gengarite", "kangaskhanite", "salamencite"
        )
    }

    // Navigation
    enum class ScreenType { HOME, BATTLE_CONFIG, ADVANCED_CONFIG, FORMAT_BUILDER, ARENA_CONFIG }
    private var currentScreen = ScreenType.HOME
    private val screenStack = mutableListOf<ScreenType>()

    // Editing state - mutable copies of data fields
    private val selectedDefaultFormats = data.selectedDefaultFormats.toMutableSet()
    private val formatConfigs = data.formatConfigs.toMutableMap().mapValues { (_, v) -> v.copy() }.toMutableMap()
    private var activeFormat = data.activeFormat

    // Custom format editing state
    private var customEnabled = false
    private var editingFormatKey: String? = null
    private var editingConfig = createDefaultFormatConfig()

    // Advanced config sub-panel
    enum class SubPanel { BANNED_POKEMON, BATTLE_CLAUSES, BANNED_MOVES, BATTLE_GIMMICK, BANNED_ABILITIES, LEVEL_CAP, BANNED_ITEMS }
    private var activeSubPanel: SubPanel? = null
    private var searchText = ""

    // Arena config cached values
    private var cachedPlayer1Pos: Vec3? = data.player1Pos
    private var cachedPlayer2Pos: Vec3? = data.player2Pos
    private var cachedCorner1: BlockPos? = data.corner1
    private var cachedCorner2: BlockPos? = data.corner2
    private var cachedCameraTimeline: String = data.cameraTimeline?.toString() ?: ""
    private var cachedCamOffset: Vec3 = data.cameraOriginOffset
    private var cachedIntroMusic: String = data.introMusic?.toString() ?: ""
    private var cachedIntroVolume: Float = data.introMusicVolume
    private var cachedIntroPitch: Float = data.introMusicPitch
    private var cachedLoopMusic: String = data.loopMusic?.toString() ?: ""
    private var cachedLoopVolume: Float = data.loopMusicVolume
    private var cachedLoopPitch: Float = data.loopMusicPitch
    private var configured = data.isConfigured

    // Field references for caching values when switching screens
    private var editFields = mutableMapOf<String, EditBox>()

    // Scroll state for lists
    private var scrollOffset = 0
    private val maxVisibleItems = 6

    override fun init() {
        super.init()
        cacheFieldValues()
        clearWidgets()
        editFields.clear()
        scrollOffset = 0

        when (currentScreen) {
            ScreenType.HOME -> initHomeScreen()
            ScreenType.BATTLE_CONFIG -> initBattleConfigScreen()
            ScreenType.ADVANCED_CONFIG -> initAdvancedConfigScreen()
            ScreenType.FORMAT_BUILDER -> initFormatBuilderScreen()
            ScreenType.ARENA_CONFIG -> initArenaConfigScreen()
        }

        // Save & Close button (always visible, bottom-right)
        val saveWidth = 90
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.save_close")) {
            cacheFieldValues()
            save()
            onClose()
        }.bounds(width - saveWidth - 10, height - 30, saveWidth, 20).build())
    }

    // ========== NAVIGATION ==========

    private fun navigateTo(screen: ScreenType) {
        cacheFieldValues()
        screenStack.add(currentScreen)
        currentScreen = screen
        rebuildWidgets()
    }

    private fun navigateBack() {
        cacheFieldValues()
        currentScreen = screenStack.removeLastOrNull() ?: ScreenType.HOME
        rebuildWidgets()
    }

    // ========== HOME SCREEN ==========

    private fun initHomeScreen() {
        val centerX = width / 2
        val centerY = height / 2
        val btnWidth = 200
        val btnHeight = 20

        // Battle Configuration button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.home.battle_config")) {
            navigateTo(ScreenType.BATTLE_CONFIG)
        }.bounds(centerX - btnWidth / 2, centerY - 20, btnWidth, btnHeight).build())

        // Arena Configuration button (disabled if no formats configured)
        val hasFormats = selectedDefaultFormats.isNotEmpty() || formatConfigs.isNotEmpty()
        val arenaButton = Button.builder(Component.translatable("cobblemon.stadium_config.home.arena_config")) {
            navigateTo(ScreenType.ARENA_CONFIG)
        }.bounds(centerX - btnWidth / 2, centerY + 10, btnWidth, btnHeight).build()
        arenaButton.active = hasFormats
        addRenderableWidget(arenaButton)
    }

    // ========== BATTLE CONFIG SCREEN ==========

    private fun initBattleConfigScreen() {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2
        var y = 50

        // Back button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.back")) {
            navigateBack()
        }.bounds(panelX, y, 50, 16).build())
        y += 30

        // Default Formats - checkbox list
        val checkboxWidth = panelWidth - 20
        val checkboxX = panelX + 10
        val formatsToShow = DEFAULT_FORMATS.drop(scrollOffset).take(maxVisibleItems)
        for ((id, displayName) in formatsToShow) {
            val isSelected = id in selectedDefaultFormats
            val label = (if (isSelected) "\u2611 " else "\u2610 ") + displayName
            addRenderableWidget(Button.builder(Component.literal(label)) {
                if (id in selectedDefaultFormats) selectedDefaultFormats.remove(id)
                else selectedDefaultFormats.add(id)
                rebuildWidgets()
            }.bounds(checkboxX, y, checkboxWidth, 16).build())
            y += 18
        }

        // Scroll buttons if needed
        if (DEFAULT_FORMATS.size > maxVisibleItems) {
            if (scrollOffset > 0) {
                addRenderableWidget(Button.builder(Component.literal("\u25B2")) {
                    scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
                    rebuildWidgets()
                }.bounds(panelX + panelWidth - 20, 78, 16, 16).build())
            }
            if (scrollOffset + maxVisibleItems < DEFAULT_FORMATS.size) {
                addRenderableWidget(Button.builder(Component.literal("\u25BC")) {
                    scrollOffset = (scrollOffset + 1).coerceAtMost(DEFAULT_FORMATS.size - maxVisibleItems)
                    rebuildWidgets()
                }.bounds(panelX + panelWidth - 20, y - 18, 16, 16).build())
            }
        }
        y += 6

        // Custom format checkbox
        val customLabel = (if (customEnabled) "\u2611 " else "\u2610 ") +
            Component.translatable("cobblemon.stadium_config.battle.custom").string
        addRenderableWidget(Button.builder(Component.literal(customLabel)) {
            customEnabled = !customEnabled
            rebuildWidgets()
        }.bounds(checkboxX, y, checkboxWidth, 16).build())
        y += 20

        // Advanced Config button (only when custom enabled)
        val advancedButton = Button.builder(Component.translatable("cobblemon.stadium_config.battle.advanced_config")) {
            editingFormatKey = null
            editingConfig = createDefaultFormatConfig()
            navigateTo(ScreenType.ADVANCED_CONFIG)
        }.bounds(checkboxX, y, checkboxWidth, 20).build()
        advancedButton.active = customEnabled
        addRenderableWidget(advancedButton)
        y += 30

        // Saved Custom Formats section
        if (formatConfigs.isNotEmpty()) {
            y += 10
            for ((key, config) in formatConfigs) {
                val entryLabel = config.formatName.ifEmpty { key }
                addRenderableWidget(Button.builder(Component.literal(entryLabel)) {
                    // no-op, just a label
                }.bounds(checkboxX, y, checkboxWidth - 50, 16).build())
                addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.battle.edit")) {
                    editingFormatKey = key
                    editingConfig = config.copy(
                        clauses = config.clauses.toMutableSet(),
                        bannedPokemon = config.bannedPokemon.toMutableSet(),
                        bannedMoves = config.bannedMoves.toMutableSet(),
                        bannedAbilities = config.bannedAbilities.toMutableSet(),
                        bannedItems = config.bannedItems.toMutableSet(),
                        allowedGimmicks = config.allowedGimmicks.toMutableSet(),
                        side1PokemonPositions = config.side1PokemonPositions.toMutableList(),
                        side2PokemonPositions = config.side2PokemonPositions.toMutableList()
                    )
                    navigateTo(ScreenType.ADVANCED_CONFIG)
                }.bounds(checkboxX + checkboxWidth - 45, y, 45, 16).build())
                y += 20
            }
        }
    }

    // ========== ADVANCED CONFIG SCREEN ==========

    private fun initAdvancedConfigScreen() {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2
        var y = 50

        // Back button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.back")) {
            navigateBack()
        }.bounds(panelX, y, 50, 16).build())
        y += 30

        // Grid of category buttons (2 columns)
        val colWidth = (panelWidth - 10) / 2
        val btnHeight = 20
        val btnSpacing = 4

        // Battle Format (full width, accent color)
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.advanced.battle_format")) {
            navigateTo(ScreenType.FORMAT_BUILDER)
        }.bounds(panelX, y, panelWidth, btnHeight).build())
        y += btnHeight + btnSpacing

        // 2-column grid
        val gridButtons = listOf(
            "cobblemon.stadium_config.advanced.banned_pokemon" to SubPanel.BANNED_POKEMON,
            "cobblemon.stadium_config.advanced.battle_clauses" to SubPanel.BATTLE_CLAUSES,
            "cobblemon.stadium_config.advanced.banned_moves" to SubPanel.BANNED_MOVES,
            "cobblemon.stadium_config.advanced.battle_gimmick" to SubPanel.BATTLE_GIMMICK,
            "cobblemon.stadium_config.advanced.banned_abilities" to SubPanel.BANNED_ABILITIES,
            "cobblemon.stadium_config.advanced.level_cap" to SubPanel.LEVEL_CAP,
            "cobblemon.stadium_config.advanced.banned_items" to SubPanel.BANNED_ITEMS
        )

        for (i in gridButtons.indices step 2) {
            val (label1, panel1) = gridButtons[i]
            addRenderableWidget(Button.builder(Component.translatable(label1)) {
                activeSubPanel = if (activeSubPanel == panel1) null else panel1
                searchText = ""
                scrollOffset = 0
                rebuildWidgets()
            }.bounds(panelX, y, colWidth, btnHeight).build())

            if (i + 1 < gridButtons.size) {
                val (label2, panel2) = gridButtons[i + 1]
                addRenderableWidget(Button.builder(Component.translatable(label2)) {
                    activeSubPanel = if (activeSubPanel == panel2) null else panel2
                    searchText = ""
                    scrollOffset = 0
                    rebuildWidgets()
                }.bounds(panelX + colWidth + 10, y, colWidth, btnHeight).build())
            }
            y += btnHeight + btnSpacing
        }
        y += 10

        // Active sub-panel area
        activeSubPanel?.let { panel ->
            initSubPanel(panel, panelX, y, panelWidth)
        }

        // Bottom buttons: Reset + Save Format
        val bottomY = height - 55
        val halfWidth = (panelWidth - 10) / 2
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.advanced.reset")) {
            editingConfig = createDefaultFormatConfig()
            activeSubPanel = null
            rebuildWidgets()
        }.bounds(panelX, bottomY, halfWidth, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.advanced.save_format")) {
            val key = editingFormatKey ?: "custom_${System.currentTimeMillis()}"
            formatConfigs[key] = editingConfig.copy(
                clauses = editingConfig.clauses.toMutableSet(),
                bannedPokemon = editingConfig.bannedPokemon.toMutableSet(),
                bannedMoves = editingConfig.bannedMoves.toMutableSet(),
                bannedAbilities = editingConfig.bannedAbilities.toMutableSet(),
                bannedItems = editingConfig.bannedItems.toMutableSet(),
                allowedGimmicks = editingConfig.allowedGimmicks.toMutableSet(),
                side1PokemonPositions = editingConfig.side1PokemonPositions.toMutableList(),
                side2PokemonPositions = editingConfig.side2PokemonPositions.toMutableList()
            )
            navigateBack()
        }.bounds(panelX + halfWidth + 10, bottomY, halfWidth, 20).build())
    }

    private fun initSubPanel(panel: SubPanel, panelX: Int, startY: Int, panelWidth: Int) {
        var y = startY
        val contentWidth = panelWidth - 20
        val contentX = panelX + 10

        when (panel) {
            SubPanel.BANNED_POKEMON -> initBanListSubPanel(contentX, y, contentWidth, SAMPLE_POKEMON, editingConfig.bannedPokemon)
            SubPanel.BANNED_MOVES -> initBanListSubPanel(contentX, y, contentWidth, SAMPLE_MOVES, editingConfig.bannedMoves)
            SubPanel.BANNED_ABILITIES -> initBanListSubPanel(contentX, y, contentWidth, SAMPLE_ABILITIES, editingConfig.bannedAbilities)
            SubPanel.BANNED_ITEMS -> initBanListSubPanel(contentX, y, contentWidth, SAMPLE_ITEMS, editingConfig.bannedItems)
            SubPanel.BATTLE_CLAUSES -> {
                val visibleClauses = CLAUSES.drop(scrollOffset).take(maxVisibleItems)
                for (clause in visibleClauses) {
                    val isSelected = clause in editingConfig.clauses
                    val label = (if (isSelected) "\u2611 " else "\u2610 ") + clause.replace("_", " ").replaceFirstChar { it.uppercase() }
                    addRenderableWidget(Button.builder(Component.literal(label)) {
                        if (clause in editingConfig.clauses) editingConfig.clauses.remove(clause)
                        else editingConfig.clauses.add(clause)
                        rebuildWidgets()
                    }.bounds(contentX, y, contentWidth, 16).build())
                    y += 18
                }
                addScrollButtons(panelX, panelWidth, startY, y, CLAUSES.size)
            }
            SubPanel.BATTLE_GIMMICK -> {
                val colWidth = (contentWidth - 6) / 2
                for (i in GIMMICKS.indices step 2) {
                    val g1 = GIMMICKS[i]
                    val isSelected1 = g1 in editingConfig.allowedGimmicks
                    val label1 = (if (isSelected1) "\u2611 " else "\u2610 ") + g1.replace("_", " ").replaceFirstChar { it.uppercase() }
                    addRenderableWidget(Button.builder(Component.literal(label1)) {
                        if (g1 in editingConfig.allowedGimmicks) editingConfig.allowedGimmicks.remove(g1)
                        else editingConfig.allowedGimmicks.add(g1)
                        rebuildWidgets()
                    }.bounds(contentX, y, colWidth, 20).build())

                    if (i + 1 < GIMMICKS.size) {
                        val g2 = GIMMICKS[i + 1]
                        val isSelected2 = g2 in editingConfig.allowedGimmicks
                        val label2 = (if (isSelected2) "\u2611 " else "\u2610 ") + g2.replace("_", " ").replaceFirstChar { it.uppercase() }
                        addRenderableWidget(Button.builder(Component.literal(label2)) {
                            if (g2 in editingConfig.allowedGimmicks) editingConfig.allowedGimmicks.remove(g2)
                            else editingConfig.allowedGimmicks.add(g2)
                            rebuildWidgets()
                        }.bounds(contentX + colWidth + 6, y, colWidth, 20).build())
                    }
                    y += 24
                }
            }
            SubPanel.LEVEL_CAP -> {
                // Display current value with arrows
                addRenderableWidget(Button.builder(Component.literal("\u25C0")) {
                    val current = editingConfig.levelCap.let { if (it == -1) 100 else it }
                    editingConfig = editingConfig.copy(levelCap = (current - 1).coerceIn(1, 100))
                    rebuildWidgets()
                }.bounds(contentX, y, 20, 20).build())

                addRenderableWidget(Button.builder(Component.literal("\u25B6")) {
                    val current = editingConfig.levelCap.let { if (it == -1) 100 else it }
                    editingConfig = editingConfig.copy(levelCap = (current + 1).coerceIn(1, 100))
                    rebuildWidgets()
                }.bounds(contentX + contentWidth - 20, y, 20, 20).build())
            }
        }
    }

    private fun initBanListSubPanel(x: Int, startY: Int, width: Int, allItems: List<String>, selectedSet: MutableSet<String>) {
        var y = startY

        // Search field
        val searchField = EditBox(font, x, y, width, 16, Component.translatable("cobblemon.stadium_config.advanced.search"))
        searchField.value = searchText
        searchField.setResponder { searchText = it; scrollOffset = 0 }
        searchField.setMaxLength(50)
        addRenderableWidget(searchField)
        editFields["search"] = searchField
        y += 20

        // Filter items by search
        val filtered = if (searchText.isBlank()) allItems
        else allItems.filter { it.contains(searchText, ignoreCase = true) }

        val visibleItems = filtered.drop(scrollOffset).take(maxVisibleItems)
        for (item in visibleItems) {
            val isSelected = item in selectedSet
            val label = (if (isSelected) "\u2611 " else "\u2610 ") + item.replace("_", " ").replaceFirstChar { it.uppercase() }
            addRenderableWidget(Button.builder(Component.literal(label)) {
                if (item in selectedSet) selectedSet.remove(item)
                else selectedSet.add(item)
                rebuildWidgets()
            }.bounds(x, y, width, 16).build())
            y += 18
        }

        // Scroll buttons
        val panelX = x - 10
        val panelWidth = width + 20
        addScrollButtons(panelX, panelWidth, startY + 20, y, filtered.size)
    }

    private fun addScrollButtons(panelX: Int, panelWidth: Int, topY: Int, bottomY: Int, totalItems: Int) {
        if (totalItems <= maxVisibleItems) return
        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(Component.literal("\u25B2")) {
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
                rebuildWidgets()
            }.bounds(panelX + panelWidth - 20, topY, 16, 16).build())
        }
        if (scrollOffset + maxVisibleItems < totalItems) {
            addRenderableWidget(Button.builder(Component.literal("\u25BC")) {
                scrollOffset = (scrollOffset + 1).coerceAtMost(totalItems - maxVisibleItems)
                rebuildWidgets()
            }.bounds(panelX + panelWidth - 20, bottomY - 18, 16, 16).build())
        }
    }

    // ========== FORMAT BUILDER SCREEN ==========

    private fun initFormatBuilderScreen() {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2
        val contentWidth = panelWidth - 20
        val contentX = panelX + 10
        var y = 50

        // Back button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.back")) {
            navigateBack()
        }.bounds(panelX, y, 50, 16).build())
        y += 30

        // Battle Type cycle selector: ◀ [type] ▶
        val currentTypeIndex = BATTLE_TYPES.indexOf(editingConfig.battleTypeName).coerceAtLeast(0)
        addRenderableWidget(Button.builder(Component.literal("\u25C0")) {
            val newIndex = (currentTypeIndex - 1 + BATTLE_TYPES.size) % BATTLE_TYPES.size
            editingConfig = editingConfig.copy(battleTypeName = BATTLE_TYPES[newIndex])
            rebuildWidgets()
        }.bounds(contentX, y, 20, 20).build())

        addRenderableWidget(Button.builder(Component.literal("\u25B6")) {
            val newIndex = (currentTypeIndex + 1) % BATTLE_TYPES.size
            editingConfig = editingConfig.copy(battleTypeName = BATTLE_TYPES[newIndex])
            rebuildWidgets()
        }.bounds(contentX + contentWidth - 20, y, 20, 20).build())
        y += 30

        // Team Preview checkbox
        val tpLabel = (if (editingConfig.teamPreview) "\u2611 " else "\u2610 ") +
            Component.translatable("cobblemon.stadium_config.format.team_preview").string
        addRenderableWidget(Button.builder(Component.literal(tpLabel)) {
            editingConfig = editingConfig.copy(teamPreview = !editingConfig.teamPreview)
            rebuildWidgets()
        }.bounds(contentX, y, contentWidth, 20).build())
        y += 30

        // Pokemon Limit cycle: ◀ [N] ▶
        addRenderableWidget(Button.builder(Component.literal("\u25C0")) {
            editingConfig = editingConfig.copy(pokemonLimit = (editingConfig.pokemonLimit - 1).coerceIn(1, 6))
            rebuildWidgets()
        }.bounds(contentX, y, 20, 20).build())

        addRenderableWidget(Button.builder(Component.literal("\u25B6")) {
            editingConfig = editingConfig.copy(pokemonLimit = (editingConfig.pokemonLimit + 1).coerceIn(1, 6))
            rebuildWidgets()
        }.bounds(contentX + contentWidth - 20, y, 20, 20).build())
        y += 30

        // Turn Timer: ◀ [N] ▶
        val turnTimer = editingConfig.moveTimer.let { if (it == -1) 60 else it }
        addRenderableWidget(Button.builder(Component.literal("\u25C0")) {
            editingConfig = editingConfig.copy(moveTimer = (turnTimer - 15).coerceIn(15, 300))
            rebuildWidgets()
        }.bounds(contentX, y, 20, 20).build())

        addRenderableWidget(Button.builder(Component.literal("\u25B6")) {
            editingConfig = editingConfig.copy(moveTimer = (turnTimer + 15).coerceIn(15, 300))
            rebuildWidgets()
        }.bounds(contentX + contentWidth - 20, y, 20, 20).build())
        y += 30

        // Match Timer: ◀ [N] ▶
        val matchTimer = editingConfig.matchTimerMinutes.let { if (it == -1) 30 else it }
        addRenderableWidget(Button.builder(Component.literal("\u25C0")) {
            editingConfig = editingConfig.copy(matchTimerMinutes = (matchTimer - 5).coerceIn(5, 120))
            rebuildWidgets()
        }.bounds(contentX, y, 20, 20).build())

        addRenderableWidget(Button.builder(Component.literal("\u25B6")) {
            editingConfig = editingConfig.copy(matchTimerMinutes = (matchTimer + 5).coerceIn(5, 120))
            rebuildWidgets()
        }.bounds(contentX + contentWidth - 20, y, 20, 20).build())
        y += 30

        // Confirm button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.format.confirm")) {
            navigateBack()
        }.bounds(contentX, y, contentWidth, 20).build())
    }

    // ========== ARENA CONFIG SCREEN ==========

    private fun initArenaConfigScreen() {
        val centerX = width / 2
        val panelWidth = 300
        val panelX = centerX - panelWidth / 2
        val contentX = panelX + 10
        val contentWidth = panelWidth - 20
        var y = 50

        // Back button
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.back")) {
            navigateBack()
        }.bounds(panelX, y, 50, 16).build())
        y += 30

        val fieldWidth = 55
        val spacing = 4
        val tripletWidth = fieldWidth * 3 + spacing * 2
        val tripletX = contentX + (contentWidth - tripletWidth) / 2

        // Player 1 Position
        y += 14
        createVec3Fields("p1", tripletX, y, fieldWidth, spacing, cachedPlayer1Pos)
        addUseCurrentPosButton(contentX + contentWidth - 65, y, "p1")
        y += 24

        // Player 2 Position
        y += 14
        createVec3Fields("p2", tripletX, y, fieldWidth, spacing, cachedPlayer2Pos)
        addUseCurrentPosButton(contentX + contentWidth - 65, y, "p2")
        y += 24

        // Corner 1
        y += 14
        createBlockPosFields("c1", tripletX, y, fieldWidth, spacing, cachedCorner1)
        y += 24

        // Corner 2
        y += 14
        createBlockPosFields("c2", tripletX, y, fieldWidth, spacing, cachedCorner2)
        y += 30

        // Camera Timeline
        y += 14
        val camTimelineField = EditBox(font, contentX, y, contentWidth, 16, Component.empty())
        camTimelineField.value = cachedCameraTimeline
        camTimelineField.setMaxLength(256)
        addRenderableWidget(camTimelineField)
        editFields["cameraTimeline"] = camTimelineField
        y += 20

        // Camera Offset
        y += 14
        createVec3Fields("camOff", tripletX, y, fieldWidth, spacing, cachedCamOffset)
        y += 24

        // Intro Music
        y += 14
        val introField = EditBox(font, contentX, y, contentWidth, 16, Component.empty())
        introField.value = cachedIntroMusic
        introField.setMaxLength(256)
        addRenderableWidget(introField)
        editFields["introMusic"] = introField
        y += 20

        // Intro Volume + Pitch
        val halfWidth = (contentWidth - spacing) / 2
        y += 14
        createFloatField("introVol", contentX, y, halfWidth, cachedIntroVolume)
        createFloatField("introPitch", contentX + halfWidth + spacing, y, halfWidth, cachedIntroPitch)
        y += 20

        // Loop Music
        y += 14
        val loopField = EditBox(font, contentX, y, contentWidth, 16, Component.empty())
        loopField.value = cachedLoopMusic
        loopField.setMaxLength(256)
        addRenderableWidget(loopField)
        editFields["loopMusic"] = loopField
        y += 20

        // Loop Volume + Pitch
        y += 14
        createFloatField("loopVol", contentX, y, halfWidth, cachedLoopVolume)
        createFloatField("loopPitch", contentX + halfWidth + spacing, y, halfWidth, cachedLoopPitch)
        y += 24

        // Configured toggle
        addRenderableWidget(Button.builder(
            Component.translatable(
                "cobblemon.stadium_config.arena.configured"
            ).append(": ").append(
                Component.translatable(if (configured) "cobblemon.stadium_config.on" else "cobblemon.stadium_config.off")
            )
        ) {
            configured = !configured
            rebuildWidgets()
        }.bounds(contentX, y, contentWidth, 20).build())
    }

    private fun createVec3Fields(prefix: String, x: Int, y: Int, fieldWidth: Int, spacing: Int, vec: Vec3?) {
        val xField = EditBox(font, x, y, fieldWidth, 16, Component.empty())
        xField.value = formatDouble(vec?.x)
        xField.setMaxLength(12)
        addRenderableWidget(xField)
        editFields["${prefix}X"] = xField

        val yField = EditBox(font, x + fieldWidth + spacing, y, fieldWidth, 16, Component.empty())
        yField.value = formatDouble(vec?.y)
        yField.setMaxLength(12)
        addRenderableWidget(yField)
        editFields["${prefix}Y"] = yField

        val zField = EditBox(font, x + 2 * (fieldWidth + spacing), y, fieldWidth, 16, Component.empty())
        zField.value = formatDouble(vec?.z)
        zField.setMaxLength(12)
        addRenderableWidget(zField)
        editFields["${prefix}Z"] = zField
    }

    private fun createBlockPosFields(prefix: String, x: Int, y: Int, fieldWidth: Int, spacing: Int, pos: BlockPos?) {
        val xField = EditBox(font, x, y, fieldWidth, 16, Component.empty())
        xField.value = pos?.x?.toString() ?: ""
        xField.setMaxLength(10)
        addRenderableWidget(xField)
        editFields["${prefix}X"] = xField

        val yField = EditBox(font, x + fieldWidth + spacing, y, fieldWidth, 16, Component.empty())
        yField.value = pos?.y?.toString() ?: ""
        yField.setMaxLength(10)
        addRenderableWidget(yField)
        editFields["${prefix}Y"] = yField

        val zField = EditBox(font, x + 2 * (fieldWidth + spacing), y, fieldWidth, 16, Component.empty())
        zField.value = pos?.z?.toString() ?: ""
        zField.setMaxLength(10)
        addRenderableWidget(zField)
        editFields["${prefix}Z"] = zField
    }

    private fun createFloatField(key: String, x: Int, y: Int, width: Int, value: Float) {
        val field = EditBox(font, x, y, width, 16, Component.empty())
        field.value = value.toString()
        field.setMaxLength(8)
        addRenderableWidget(field)
        editFields[key] = field
    }

    private fun addUseCurrentPosButton(x: Int, y: Int, prefix: String) {
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.arena.use_current")) {
            val player = Minecraft.getInstance().player ?: return@builder
            editFields["${prefix}X"]?.value = "%.2f".format(player.x)
            editFields["${prefix}Y"]?.value = "%.2f".format(player.y)
            editFields["${prefix}Z"]?.value = "%.2f".format(player.z)
        }.bounds(x, y, 65, 16).build())
    }

    // ========== RENDERING ==========

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        when (currentScreen) {
            ScreenType.HOME -> renderHomeScreen(guiGraphics, mouseX, mouseY)
            ScreenType.BATTLE_CONFIG -> renderBattleConfigScreen(guiGraphics)
            ScreenType.ADVANCED_CONFIG -> renderAdvancedConfigScreen(guiGraphics)
            ScreenType.FORMAT_BUILDER -> renderFormatBuilderScreen(guiGraphics)
            ScreenType.ARENA_CONFIG -> renderArenaConfigScreen(guiGraphics)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderPanel(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, PANEL_BG)
        guiGraphics.renderOutline(x, y, w, h, PANEL_BORDER)
    }

    private fun renderDivider(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int) {
        guiGraphics.fill(x, y, x + width, y + 1, DIVIDER_COLOR)
    }

    private fun renderHomeScreen(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val centerX = width / 2
        val centerY = height / 2
        val panelWidth = 220
        val panelHeight = 100

        renderPanel(guiGraphics, centerX - panelWidth / 2, centerY - panelHeight / 2 - 10, panelWidth, panelHeight)

        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.home.title"),
            x = centerX,
            y = centerY - panelHeight / 2,
            scale = 1.2f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )

        // Tooltip for disabled arena button
        val hasFormats = selectedDefaultFormats.isNotEmpty() || formatConfigs.isNotEmpty()
        if (!hasFormats) {
            val arenaButtonY = centerY + 10
            val btnWidth = 200
            val btnX = centerX - btnWidth / 2
            if (mouseX in btnX..(btnX + btnWidth) && mouseY in arenaButtonY..(arenaButtonY + 20)) {
                guiGraphics.renderTooltip(font, Component.translatable("cobblemon.stadium_config.home.arena_disabled"), mouseX, mouseY)
            }
        }
    }

    private fun renderBattleConfigScreen(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2

        renderPanel(guiGraphics, panelX - 5, 40, panelWidth + 10, height - 80)

        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.battle.title"),
            x = centerX,
            y = 55,
            scale = 1.1f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )

        // Default Formats label
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.battle.default_formats"),
            x = panelX + 10,
            y = 70,
            colour = LABEL_COLOR,
            shadow = true
        )

        // Saved formats label (if any)
        if (formatConfigs.isNotEmpty()) {
            val savedY = 80 + (DEFAULT_FORMATS.take(maxVisibleItems).size * 18) + 46
            renderDivider(guiGraphics, panelX + 10, savedY - 10, panelWidth - 20)
            drawScaledText(
                context = guiGraphics,
                text = Component.translatable("cobblemon.stadium_config.battle.saved_formats"),
                x = panelX + 10,
                y = savedY,
                colour = LABEL_COLOR,
                shadow = true
            )
        }
    }

    private fun renderAdvancedConfigScreen(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2

        renderPanel(guiGraphics, panelX - 5, 40, panelWidth + 10, height - 80)

        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.advanced.title"),
            x = centerX,
            y = 55,
            scale = 1.1f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )

        // Level cap value display
        if (activeSubPanel == SubPanel.LEVEL_CAP) {
            val levelCapDisplay = editingConfig.levelCap.let { if (it == -1) "100" else it.toString() }
            drawScaledText(
                context = guiGraphics,
                text = Component.literal(levelCapDisplay),
                x = centerX,
                y = getSubPanelY() + 5,
                scale = 1.2f,
                colour = TITLE_COLOR,
                centered = true,
                shadow = true
            )
        }
    }

    private fun renderFormatBuilderScreen(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val panelWidth = 280
        val panelX = centerX - panelWidth / 2
        val contentX = panelX + 10
        val contentWidth = panelWidth - 20

        renderPanel(guiGraphics, panelX - 5, 40, panelWidth + 10, height - 80)

        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.format.title"),
            x = centerX,
            y = 55,
            scale = 1.1f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )

        // Battle type label + current value
        var y = 80
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.advanced.battle_format"),
            x = contentX,
            y = y - 12,
            colour = LABEL_COLOR,
            shadow = true
        )
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(editingConfig.battleTypeName.replaceFirstChar { it.uppercase() }),
            x = centerX,
            y = y + 5,
            scale = 1.1f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )
        y += 30

        // Team Preview label
        renderDivider(guiGraphics, contentX, y - 5, contentWidth)
        y += 30

        // Pokemon Limit label + value
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.format.pokemon_limit"),
            x = contentX,
            y = y - 12,
            colour = LABEL_COLOR,
            shadow = true
        )
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(editingConfig.pokemonLimit.toString()),
            x = centerX,
            y = y + 5,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )
        y += 30
        renderDivider(guiGraphics, contentX, y - 5, contentWidth)

        // Turn Timer label + value
        val turnTimer = editingConfig.moveTimer.let { if (it == -1) 60 else it }
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.format.turn_timer"),
            x = contentX,
            y = y - 2,
            colour = LABEL_COLOR,
            shadow = true
        )
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("${turnTimer}s"),
            x = centerX,
            y = y + 8,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )
        y += 30

        // Match Timer label + value
        val matchTimer = editingConfig.matchTimerMinutes.let { if (it == -1) 30 else it }
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.format.match_timer"),
            x = contentX,
            y = y - 2,
            colour = LABEL_COLOR,
            shadow = true
        )
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("${matchTimer}m"),
            x = centerX,
            y = y + 8,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )
    }

    private fun renderArenaConfigScreen(guiGraphics: GuiGraphics) {
        val centerX = width / 2
        val panelWidth = 300
        val panelX = centerX - panelWidth / 2
        val contentX = panelX + 10
        val contentWidth = panelWidth - 20

        renderPanel(guiGraphics, panelX - 5, 40, panelWidth + 10, height - 80)

        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.arena.title"),
            x = centerX,
            y = 55,
            scale = 1.1f,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )

        var y = 80

        // Position labels
        val posLabels = listOf(
            "cobblemon.stadium_config.arena.player1",
            "cobblemon.stadium_config.arena.player2",
            "cobblemon.stadium_config.arena.corner1",
            "cobblemon.stadium_config.arena.corner2"
        )
        for (label in posLabels) {
            drawScaledText(
                context = guiGraphics,
                text = Component.translatable(label),
                x = contentX,
                y = y,
                colour = LABEL_COLOR,
                shadow = true
            )
            y += 38
        }

        // Divider before media
        y += 2
        renderDivider(guiGraphics, contentX, y, contentWidth)
        y += 8

        // Media section labels
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.arena.media"),
            x = centerX,
            y = y,
            colour = TITLE_COLOR,
            centered = true,
            shadow = true
        )
        y += 16

        val mediaLabels = listOf(
            "cobblemon.stadium_config.arena.camera_timeline",
            "cobblemon.stadium_config.arena.camera_offset",
            "cobblemon.stadium_config.arena.intro_music",
            "cobblemon.stadium_config.arena.volume" to "cobblemon.stadium_config.arena.pitch",
            "cobblemon.stadium_config.arena.loop_music",
            "cobblemon.stadium_config.arena.volume" to "cobblemon.stadium_config.arena.pitch"
        )

        for (entry in mediaLabels) {
            when (entry) {
                is String -> {
                    drawScaledText(
                        context = guiGraphics,
                        text = Component.translatable(entry),
                        x = contentX,
                        y = y,
                        colour = LABEL_COLOR,
                        shadow = true
                    )
                    y += if (entry.contains("offset")) 38 else 34
                }
                is Pair<*, *> -> {
                    val halfWidth = (contentWidth - 4) / 2
                    drawScaledText(
                        context = guiGraphics,
                        text = Component.translatable(entry.first as String),
                        x = contentX,
                        y = y,
                        colour = LABEL_COLOR,
                        shadow = true
                    )
                    drawScaledText(
                        context = guiGraphics,
                        text = Component.translatable(entry.second as String),
                        x = contentX + halfWidth + 4,
                        y = y,
                        colour = LABEL_COLOR,
                        shadow = true
                    )
                    y += 34
                }
            }
        }
    }

    private fun getSubPanelY(): Int {
        // Calculate Y position where sub-panel starts in Advanced Config
        // Grid takes: 50 (back) + 30 (space) + 24 (battle format) + 4*24 (grid rows) = 200
        return 80 + 24 + 4 * 24 + 10
    }

    // ========== FIELD CACHING ==========

    private fun cacheFieldValues() {
        editFields["p1X"]?.let {
            cachedPlayer1Pos = parseVec3FromFields("p1")
        }
        editFields["p2X"]?.let {
            cachedPlayer2Pos = parseVec3FromFields("p2")
        }
        editFields["c1X"]?.let {
            cachedCorner1 = parseBlockPosFromFields("c1")
        }
        editFields["c2X"]?.let {
            cachedCorner2 = parseBlockPosFromFields("c2")
        }
        editFields["cameraTimeline"]?.let { cachedCameraTimeline = it.value }
        editFields["camOffX"]?.let {
            val vec = parseVec3FromFields("camOff")
            if (vec != null) cachedCamOffset = vec
        }
        editFields["introMusic"]?.let { cachedIntroMusic = it.value }
        editFields["introVol"]?.let { cachedIntroVolume = it.value.toFloatOrNull() ?: cachedIntroVolume }
        editFields["introPitch"]?.let { cachedIntroPitch = it.value.toFloatOrNull() ?: cachedIntroPitch }
        editFields["loopMusic"]?.let { cachedLoopMusic = it.value }
        editFields["loopVol"]?.let { cachedLoopVolume = it.value.toFloatOrNull() ?: cachedLoopVolume }
        editFields["loopPitch"]?.let { cachedLoopPitch = it.value.toFloatOrNull() ?: cachedLoopPitch }
    }

    private fun parseVec3FromFields(prefix: String): Vec3? {
        val x = editFields["${prefix}X"]?.value?.toDoubleOrNull() ?: return null
        val y = editFields["${prefix}Y"]?.value?.toDoubleOrNull() ?: return null
        val z = editFields["${prefix}Z"]?.value?.toDoubleOrNull() ?: return null
        return Vec3(x, y, z)
    }

    private fun parseBlockPosFromFields(prefix: String): BlockPos? {
        val x = editFields["${prefix}X"]?.value?.toIntOrNull() ?: return null
        val y = editFields["${prefix}Y"]?.value?.toIntOrNull() ?: return null
        val z = editFields["${prefix}Z"]?.value?.toIntOrNull() ?: return null
        return BlockPos(x, y, z)
    }

    // ========== SAVE ==========

    private fun save() {
        val cameraTimeline = cachedCameraTimeline.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.cameraTimeline }
        }
        val introMusic = cachedIntroMusic.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.introMusic }
        }
        val loopMusic = cachedLoopMusic.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.loopMusic }
        }

        val updatedData = data.copy(
            formatConfigs = formatConfigs.toMutableMap(),
            activeFormat = activeFormat,
            selectedDefaultFormats = selectedDefaultFormats.toMutableSet(),
            player1Pos = cachedPlayer1Pos,
            player2Pos = cachedPlayer2Pos,
            corner1 = cachedCorner1,
            corner2 = cachedCorner2,
            cameraTimeline = cameraTimeline,
            cameraOriginOffset = cachedCamOffset,
            introMusic = introMusic,
            introMusicVolume = cachedIntroVolume,
            introMusicPitch = cachedIntroPitch,
            loopMusic = loopMusic,
            loopMusicVolume = cachedLoopVolume,
            loopMusicPitch = cachedLoopPitch,
            isConfigured = configured
        )

        CobblemonNetwork.sendToServer(UpdateStadiumBlockPacket(blockPos, updatedData.toNbt()))
    }

    // ========== UTILITIES ==========

    private fun createDefaultFormatConfig(): StadiumFormatConfig {
        return StadiumFormatConfig(
            formatName = "custom",
            battleTypeName = "singles",
            clauses = mutableSetOf(),
            levelCap = -1,
            teamPreview = false,
            pokemonLimit = 6,
            moveTimer = -1,
            matchTimerMinutes = -1,
            bannedPokemon = mutableSetOf(),
            bannedMoves = mutableSetOf(),
            bannedAbilities = mutableSetOf(),
            bannedItems = mutableSetOf(),
            allowedGimmicks = mutableSetOf(),
            side1PokemonPositions = mutableListOf(),
            side2PokemonPositions = mutableListOf()
        )
    }

    private fun formatDouble(value: Double?): String = value?.let { "%.2f".format(it) } ?: ""

    override fun isPauseScreen(): Boolean = false
}
