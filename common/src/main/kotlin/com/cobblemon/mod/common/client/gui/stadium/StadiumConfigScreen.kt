/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.client.gui.stadium.widget.FormatEntry
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumCycleSelectorWidget
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumFormatListWidget
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumOverlayWidget
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumSlotEntryWidget
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumTabWidget
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumToggleWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.battles.stadium.StadiumBlockPlacement
import com.cobblemon.mod.common.net.messages.server.stadium.RequestStadiumPlacementWandPacket
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

class StadiumConfigScreen(
    private val blockPos: BlockPos,
    private val data: StadiumBlockData,
    private val initialTab: Tab = Tab.BATTLE,
    private val initialArenaSubTab: ArenaSubTab = ArenaSubTab.PLACEMENTS
) : Screen(Component.translatable("cobblemon.stadium_config.title")) {

    companion object {
        const val BASE_WIDTH = 420
        const val BASE_HEIGHT = 240
        private const val PANEL_BG = 0xFF1A1A2E.toInt()
        private const val TITLE_BAR_BG = 0xFF252540.toInt()
        private const val CONTENT_BG = 0xFF252540.toInt()
        private const val BORDER_COLOR = 0xFF5C5C7A.toInt()
        private const val DIVIDER_COLOR = 0xFF3A3A5C.toInt()
        private const val LABEL_COLOR = 0xA0A0B8
        private const val TITLE_COLOR = 0xFFFFFF
        private const val ACCENT_COLOR = 0xFF6688FF.toInt()
        private val bgTexture = cobblemonResource("textures/gui/stadium/stadium_config_base.png")

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

        // Detail panel layout constants
        private const val DETAIL_BG = 0xFF1E1E36.toInt()
        private const val DETAIL_BORDER = 0xFF3A3A5C.toInt()
        private const val ROW_BG = 0xFF2A2A44.toInt()
        private const val ROW_HOVER_BG = 0xFF333355.toInt()
        private const val BADGE_GREEN = 0xFF226622.toInt()
        private const val BADGE_RED = 0xFF662222.toInt()
        private const val CHIP_BLUE = 0xFF334466.toInt()
        private const val CHIP_GREEN = 0xFF2A4A2A.toInt()
        private const val VALUE_COLOR = 0xDDDDFF
        private const val CATEGORY_ROW_HEIGHT = 16
        private const val CATEGORY_ARROW_COLOR = 0x6688FF
    }

    enum class Tab { BATTLE, ARENA }
    enum class ArenaSubTab { PLACEMENTS, CAMERA, MUSIC }

    // Tab state
    private var activeTab: Tab = initialTab
    private var activeArenaSubTab: ArenaSubTab = initialArenaSubTab

    // Editing state - mutable copies of data fields
    private val selectedDefaultFormats = data.selectedDefaultFormats.toMutableSet()
    private val formatConfigs = data.formatConfigs.toMutableMap().mapValues { (_, v) -> v.copy() }.toMutableMap()
    private var activeFormat = data.activeFormat

    // Battle tab selection state
    var selectedFormatId: String? = null

    // Custom format editing state
    private var customEnabled = false
    private var editingFormatKey: String? = null
    private var editingConfig = createDefaultFormatConfig()

    // Category row bounds for click detection (populated during render)
    private data class CategoryRowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val category: String)
    private var categoryRowBounds = mutableListOf<CategoryRowBounds>()

    // Arena config cached values
    private var viewingLayoutType: String = "singles"
    private var editingSinglesLayout: StadiumBlockLayout = data.singlesLayout?.deepCopy() ?: StadiumBlockLayout.createDefault()
    private var editingDoublesLayout: StadiumBlockLayout = data.doublesLayout?.deepCopy() ?: StadiumBlockLayout.createDefault()
    private var cachedCameraTimeline: String = data.cameraTimeline?.toString() ?: ""
    private var cachedCameraTimelineSideB: String = data.cameraTimelineSideB?.toString() ?: ""
    private var cachedCamOffset: Vec3 = data.cameraOriginOffset
    private var cachedIntroMusic: String = data.introMusic?.toString() ?: ""
    private var cachedIntroVolume: Float = data.introMusicVolume
    private var cachedIntroPitch: Float = data.introMusicPitch
    private var cachedLoopMusic: String = data.loopMusic?.toString() ?: ""
    private var cachedLoopVolume: Float = data.loopMusicVolume
    private var cachedLoopPitch: Float = data.loopMusicPitch
    private var configured = data.isConfigured

    // Arena widget references
    private var arenaMapWidget: ArenaMapWidget? = null
    private var slotListWidget: PlacementSlotListWidget? = null

    // Field references for caching values when switching screens
    private var editFields = mutableMapOf<String, EditBox>()

    // Overlay for popup modals (null = no overlay)
    var activeOverlay: StadiumOverlayWidget? = null

    // Detail panel scroll state
    private var detailScrollOffset = 0
    private var detailContentHeight = 0

    override fun init() {
        super.init()

        val panelX = (width - BASE_WIDTH) / 2
        val panelY = (height - BASE_HEIGHT) / 2

        // Cache existing field values before clearing
        cacheFieldValues()
        clearWidgets()
        editFields.clear()

        // Tab bar - two tabs at the top of the panel
        val tabY = panelY + 2
        val tabStartX = panelX + 10

        addRenderableWidget(StadiumTabWidget(
            pX = tabStartX,
            pY = tabY,
            label = Component.translatable("cobblemon.stadium_config.tab.battle"),
            isActive = { activeTab == Tab.BATTLE },
            onPress = {
                if (activeTab != Tab.BATTLE) {
                    cacheFieldValues()
                    activeTab = Tab.BATTLE
                    refreshWidgets()
                }
            }
        ))

        addRenderableWidget(StadiumTabWidget(
            pX = tabStartX + StadiumTabWidget.WIDTH.toInt() + 4,
            pY = tabY,
            label = Component.translatable("cobblemon.stadium_config.tab.arena"),
            isActive = { activeTab == Tab.ARENA },
            onPress = {
                if (activeTab != Tab.ARENA) {
                    cacheFieldValues()
                    activeTab = Tab.ARENA
                    refreshWidgets()
                }
            }
        ))

        // Content area below tab bar
        when (activeTab) {
            Tab.BATTLE -> initBattleTab(panelX, panelY)
            Tab.ARENA -> initArenaTab(panelX, panelY)
        }

        // Bottom bar buttons
        val bottomY = panelY + BASE_HEIGHT - 24
        val btnWidth = 80
        val btnSpacing = 8

        // Cancel button (left side of bottom bar)
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.cancel")) {
            onClose()
        }.bounds(panelX + btnSpacing, bottomY, btnWidth, 18).build())

        // Save & Close button (right side of bottom bar)
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium_config.save_close")) {
            cacheFieldValues()
            save()
            onClose()
        }.bounds(panelX + BASE_WIDTH - btnWidth - btnSpacing, bottomY, btnWidth, 18).build())
    }

    // ========== BATTLE TAB ==========

    private fun initBattleTab(panelX: Int, panelY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 8
        val listWidth = 155
        val listHeight = BASE_HEIGHT - StadiumTabWidget.HEIGHT.toInt() - 42

        // Build format entries for the list widget
        val entries = mutableListOf<FormatEntry>()
        for ((id, displayName) in DEFAULT_FORMATS) {
            entries.add(FormatEntry(
                id = id,
                displayName = displayName,
                isPreset = true,
                isEnabled = id in selectedDefaultFormats
            ))
        }
        for ((key, config) in formatConfigs) {
            entries.add(FormatEntry(
                id = key,
                displayName = config.formatName.ifEmpty { key },
                isPreset = false,
                isEnabled = true
            ))
        }

        val formatList = StadiumFormatListWidget(
            x = panelX + 8,
            y = contentY,
            width = listWidth,
            height = listHeight,
            entries = entries,
            onSelect = { entry ->
                selectedFormatId = entry.id
                detailScrollOffset = 0
                refreshWidgets()
            },
            onToggle = { entry ->
                if (entry.isPreset) {
                    if (entry.isEnabled) selectedDefaultFormats.add(entry.id)
                    else selectedDefaultFormats.remove(entry.id)
                }
            },
            onAddCustom = {
                val key = "custom_${System.currentTimeMillis()}"
                val newConfig = createDefaultFormatConfig()
                formatConfigs[key] = newConfig
                selectedFormatId = key
                refreshWidgets()
            }
        )
        formatList.selectedId = selectedFormatId
        addRenderableWidget(formatList)

        // Right panel: detail view based on selected format
        val detailX = panelX + 8 + listWidth + 8
        val detailWidth = BASE_WIDTH - listWidth - 32
        val detailY = contentY

        val selected = selectedFormatId
        if (selected != null) {
            val isPreset = DEFAULT_FORMATS.any { it.first == selected }
            if (!isPreset && formatConfigs.containsKey(selected)) {
                // Editable custom format detail panel
                initCustomFormatDetail(detailX, detailY, detailWidth, selected)
            }
            // Preset detail panel is rendered purely in renderBattleDetailPanel()
        }
    }

    private fun initCustomFormatDetail(detailX: Int, detailY: Int, detailWidth: Int, formatKey: String) {
        val config = formatConfigs[formatKey] ?: return
        val fieldWidth = detailWidth - 90
        val panelY = (height - BASE_HEIGHT) / 2
        val detailBottom = panelY + BASE_HEIGHT - 28
        var rowY = detailY + 20 - detailScrollOffset

        // Helper: add widget and set visibility based on whether it overlaps the visible area
        fun <T : net.minecraft.client.gui.components.AbstractWidget> addClippedWidget(widget: T, widgetY: Int, widgetHeight: Int): T {
            widget.visible = (widgetY + widgetHeight > detailY) && (widgetY < detailBottom)
            return addRenderableWidget(widget)
        }

        // Name edit box
        val nameField = EditBox(
            Minecraft.getInstance().font,
            detailX + 80, rowY, fieldWidth, 14,
            Component.literal("Name")
        )
        nameField.setMaxLength(40)
        nameField.value = config.formatName
        nameField.setResponder { newName ->
            formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(formatName = newName)
        }
        addClippedWidget(nameField, rowY, 14)
        editFields["customName"] = nameField
        rowY += 16

        // Battle Type cycle selector
        val battleTypeIndex = BATTLE_TYPES.indexOf(config.battleTypeName).coerceAtLeast(0)
        addClippedWidget(StadiumCycleSelectorWidget(
            x = detailX + 80, y = rowY, width = fieldWidth,
            options = BATTLE_TYPES,
            displayMapper = { it.replaceFirstChar { c -> c.uppercase() } },
            initialIndex = battleTypeIndex,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(battleTypeName = value)
            }
        ), rowY, 14)
        rowY += 16

        // Team Size (1..6)
        val teamSizeOptions = (1..6).toList()
        val teamSizeIndex = (config.pokemonLimit - 1).coerceIn(0, 5)
        addClippedWidget(StadiumCycleSelectorWidget(
            x = detailX + 80, y = rowY, width = fieldWidth,
            options = teamSizeOptions,
            displayMapper = { "$it" },
            initialIndex = teamSizeIndex,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(pokemonLimit = value)
            }
        ), rowY, 14)
        rowY += 16

        // Level Cap (1..100, -1 = None)
        val levelCapOptions = listOf(-1) + (1..100).toList()
        val levelCapIndex = if (config.levelCap == -1) 0 else levelCapOptions.indexOf(config.levelCap).coerceAtLeast(0)
        addClippedWidget(StadiumCycleSelectorWidget(
            x = detailX + 80, y = rowY, width = fieldWidth,
            options = levelCapOptions,
            displayMapper = { if (it == -1) "None" else "$it" },
            initialIndex = levelCapIndex,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(levelCap = value)
            }
        ), rowY, 14)
        rowY += 16

        // Team Preview toggle
        addClippedWidget(StadiumToggleWidget(
            x = detailX + 80, y = rowY + 1,
            value = config.teamPreview,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(teamPreview = value)
            }
        ), rowY + 1, 12)
        rowY += 16

        // Turn Timer (15s..300s in 15s steps, -1 = None)
        val turnTimerOptions = listOf(-1) + (1..20).map { it * 15 }
        val turnTimerIndex = if (config.moveTimer == -1) 0 else {
            val idx = turnTimerOptions.indexOf(config.moveTimer)
            if (idx >= 0) idx else 0
        }
        addClippedWidget(StadiumCycleSelectorWidget(
            x = detailX + 80, y = rowY, width = fieldWidth,
            options = turnTimerOptions,
            displayMapper = { if (it == -1) "None" else "${it}s" },
            initialIndex = turnTimerIndex,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(moveTimer = value)
            }
        ), rowY, 14)
        rowY += 16

        // Match Timer (5m..120m in 5m steps, -1 = None)
        val matchTimerOptions = listOf(-1) + (1..24).map { it * 5 }
        val matchTimerIndex = if (config.matchTimerMinutes == -1) 0 else {
            val idx = matchTimerOptions.indexOf(config.matchTimerMinutes)
            if (idx >= 0) idx else 0
        }
        addClippedWidget(StadiumCycleSelectorWidget(
            x = detailX + 80, y = rowY, width = fieldWidth,
            options = matchTimerOptions,
            displayMapper = { if (it == -1) "None" else "${it}m" },
            initialIndex = matchTimerIndex,
            onChange = { value ->
                formatConfigs[formatKey] = formatConfigs[formatKey]!!.copy(matchTimerMinutes = value)
            }
        ), rowY, 14)
        rowY += 18

        // Delete button
        val deleteY = rowY + CATEGORY_ROW_HEIGHT * 6 + 8
        val deleteBtn = Button.builder(Component.literal("Delete Format")) {
            formatConfigs.remove(formatKey)
            selectedFormatId = null
            detailScrollOffset = 0
            refreshWidgets()
        }.bounds(detailX, deleteY, 80, 16).build()
        addClippedWidget(deleteBtn, deleteY, 16)

        // Track total content height for scroll clamping
        // Content starts at detailY, ends at bottom of delete button
        detailContentHeight = (deleteY + 16) - detailY + detailScrollOffset
    }

    private fun getPresetDetails(formatId: String): StadiumFormatConfig {
        return when (formatId) {
            "anything_goes" -> StadiumFormatConfig(
                "Anything Goes", "singles", mutableSetOf(), -1, false, 6, -1, -1,
                mutableSetOf(), mutableSetOf(), mutableSetOf(), mutableSetOf(),
                mutableSetOf("mega_evolution", "z_moves", "dynamax", "terastallization")
            )
            "singles" -> StadiumFormatConfig(
                "Singles", "singles", mutableSetOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause"), 100, false, 6, 150, -1,
                mutableSetOf(), mutableSetOf(), mutableSetOf(), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            "vgc_doubles" -> StadiumFormatConfig(
                "VGC Doubles", "doubles", mutableSetOf("species_clause"), 50, true, 6, 60, 30,
                mutableSetOf(), mutableSetOf(), mutableSetOf(), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            "ou_singles" -> StadiumFormatConfig(
                "OU Singles", "singles", mutableSetOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause"), 100, true, 6, 150, -1,
                mutableSetOf("mewtwo", "rayquaza", "kyogre", "groudon"), mutableSetOf("baton_pass"), mutableSetOf("moody", "shadow_tag", "arena_trap"), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            "ubers" -> StadiumFormatConfig(
                "Ubers", "singles", mutableSetOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause"), 100, true, 6, 150, -1,
                mutableSetOf(), mutableSetOf(), mutableSetOf(), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            "uu_singles" -> StadiumFormatConfig(
                "UU Singles", "singles", mutableSetOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause"), 100, true, 6, 150, -1,
                mutableSetOf("mewtwo", "rayquaza", "kyogre", "groudon", "dialga", "palkia", "giratina", "reshiram", "zekrom", "xerneas", "yveltal", "zacian"),
                mutableSetOf("baton_pass"), mutableSetOf("moody", "shadow_tag", "arena_trap", "speed_boost"), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            "little_cup" -> StadiumFormatConfig(
                "Little Cup", "singles", mutableSetOf("species_clause", "sleep_clause", "evasion_clause", "ohko_clause", "moody_clause", "endless_battle_clause"), 5, true, 6, 150, -1,
                mutableSetOf(), mutableSetOf(), mutableSetOf(), mutableSetOf(),
                mutableSetOf("terastallization")
            )
            else -> createDefaultFormatConfig()
        }
    }

    // ========== ARENA TAB ==========

    private fun initArenaTab(panelX: Int, panelY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 6
        val subTabY = contentY + 4

        // Sub-tab bar: Placements | Camera | Music
        val subTabStartX = panelX + 8
        val subTabWidth = 70
        val subTabHeight = 13

        val subTabs = listOf(
            ArenaSubTab.PLACEMENTS to "Placements",
            ArenaSubTab.CAMERA to "Camera",
            ArenaSubTab.MUSIC to "Music"
        )

        subTabs.forEachIndexed { index, (subTab, label) ->
            val tabX = subTabStartX + index * (subTabWidth + 3)
            addRenderableWidget(Button.builder(Component.literal(label)) {
                if (activeArenaSubTab != subTab) {
                    cacheFieldValues()
                    activeArenaSubTab = subTab
                    refreshWidgets()
                }
            }.bounds(tabX, subTabY, subTabWidth, subTabHeight).build())
        }

        // Layout toggle button (Singles/Doubles) at right end of sub-tab bar
        val toggleLabel = if (viewingLayoutType == "singles") "Singles" else "Doubles"
        val toggleX = panelX + BASE_WIDTH - 80
        addRenderableWidget(Button.builder(Component.literal(toggleLabel)) {
            viewingLayoutType = if (viewingLayoutType == "singles") "doubles" else "singles"
            arenaMapWidget?.refreshTerrain()
            refreshWidgets()
        }.bounds(toggleX, subTabY, 68, subTabHeight).build())

        val contentTop = subTabY + subTabHeight + 6

        when (activeArenaSubTab) {
            ArenaSubTab.PLACEMENTS -> initPlacementsSubTab(panelX, panelY, contentTop)
            ArenaSubTab.CAMERA -> initCameraSubTab(panelX, panelY, contentTop)
            ArenaSubTab.MUSIC -> initMusicSubTab(panelX, panelY, contentTop)
        }
    }

    private fun initPlacementsSubTab(panelX: Int, panelY: Int, contentTop: Int) {
        val activeLayout = if (viewingLayoutType == "singles") editingSinglesLayout else editingDoublesLayout
        val bottomY = panelY + BASE_HEIGHT - 28
        val availableHeight = bottomY - contentTop - 4

        // Left side: ArenaMapWidget (~150x150)
        val mapSize = 150.coerceAtMost(availableHeight)
        val mapX = panelX + 10
        val mapY = contentTop
        val mapWidget = ArenaMapWidget(
            x = mapX,
            y = mapY,
            width = mapSize,
            height = mapSize,
            origin = blockPos,
            layout = activeLayout
        )
        arenaMapWidget = mapWidget
        addRenderableWidget(mapWidget)

        // Right side: PlacementSlotListWidget
        val slotListX = mapX + mapSize + 8
        val slotListWidth = panelX + BASE_WIDTH - slotListX - 10
        val slotListHeight = availableHeight

        val slotList = PlacementSlotListWidget(
            x = slotListX,
            y = contentTop,
            width = slotListWidth,
            height = slotListHeight,
            stadiumBlockPos = blockPos,
            layoutType = viewingLayoutType,
            layout = activeLayout,
            onRequestWand = { layoutType, sideIndex, slotType, slotIndex ->
                CobblemonNetwork.sendToServer(
                    RequestStadiumPlacementWandPacket(blockPos, layoutType, sideIndex, slotType, slotIndex)
                )
                onClose()
            },
            onClearSlot = { sideIndex, slotType, slotIndex ->
                val layout = if (viewingLayoutType == "singles") editingSinglesLayout else editingDoublesLayout
                val side = if (sideIndex == 1) layout.side1 else layout.side2
                val list = if (slotType == "actors") side.actors else side.pokemon
                if (slotIndex in list.indices) {
                    list.removeAt(slotIndex)
                    refreshWidgets()
                }
            },
            onAddSlot = { sideIndex, slotType ->
                val layout = if (viewingLayoutType == "singles") editingSinglesLayout else editingDoublesLayout
                val side = if (sideIndex == 1) layout.side1 else layout.side2
                val list = if (slotType == "actors") side.actors else side.pokemon
                list.add(StadiumBlockPlacement(position = Vec3(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())))
                refreshWidgets()
            },
            rebuildScreen = { refreshWidgets() }
        )
        slotListWidget = slotList
        addRenderableWidget(slotList)
        slotList.buildSlotWidgets { widget -> addRenderableWidget(widget) }

        // Wire bidirectional selection: map click -> slot list, slot click -> map
        mapWidget.onMarkerSelected = { index -> slotList.selectSlot(index) }
        slotList.onSlotSelected = { index -> mapWidget.selectMarker(index) }
    }

    private fun initCameraSubTab(panelX: Int, panelY: Int, contentTop: Int) {
        val labelWidth = 130
        val fieldX = panelX + labelWidth
        val fieldWidth = BASE_WIDTH - labelWidth - 16
        var rowY = contentTop + 2

        // Timeline Side A
        val timelineAField = EditBox(
            Minecraft.getInstance().font,
            fieldX, rowY, fieldWidth, 14,
            Component.literal("Timeline Side A")
        )
        timelineAField.setMaxLength(128)
        timelineAField.value = cachedCameraTimeline
        addRenderableWidget(timelineAField)
        editFields["cameraTimeline"] = timelineAField
        rowY += 20

        // Timeline Side B
        val timelineBField = EditBox(
            Minecraft.getInstance().font,
            fieldX, rowY, fieldWidth, 14,
            Component.literal("Timeline Side B")
        )
        timelineBField.setMaxLength(128)
        timelineBField.value = cachedCameraTimelineSideB
        timelineBField.setHint(Component.literal("Mirrors Side A if empty"))
        addRenderableWidget(timelineBField)
        editFields["cameraTimelineSideB"] = timelineBField
        rowY += 20

        // Origin Offset X / Y / Z
        val xyzWidth = (fieldWidth - 6) / 3
        val offXField = EditBox(
            Minecraft.getInstance().font,
            fieldX, rowY, xyzWidth, 14,
            Component.literal("X")
        )
        offXField.setMaxLength(16)
        offXField.value = formatDouble(cachedCamOffset.x)
        addRenderableWidget(offXField)
        editFields["camOffX"] = offXField

        val offYField = EditBox(
            Minecraft.getInstance().font,
            fieldX + xyzWidth + 3, rowY, xyzWidth, 14,
            Component.literal("Y")
        )
        offYField.setMaxLength(16)
        offYField.value = formatDouble(cachedCamOffset.y)
        addRenderableWidget(offYField)
        editFields["camOffY"] = offYField

        val offZField = EditBox(
            Minecraft.getInstance().font,
            fieldX + (xyzWidth + 3) * 2, rowY, xyzWidth, 14,
            Component.literal("Z")
        )
        offZField.setMaxLength(16)
        offZField.value = formatDouble(cachedCamOffset.z)
        addRenderableWidget(offZField)
        editFields["camOffZ"] = offZField
    }

    private fun initMusicSubTab(panelX: Int, panelY: Int, contentTop: Int) {
        val labelWidth = 90
        val fieldX = panelX + labelWidth
        val fullFieldWidth = BASE_WIDTH - labelWidth - 16
        val cycleSelectorWidth = 70
        var rowY = contentTop + 2

        val volumeOptions = (0..20).map { it * 0.1f }
        val volumeDisplayMapper: (Float) -> String = { "%.1f".format(it) }

        // --- Intro Music ---
        // Resource ID
        val introMusicField = EditBox(
            Minecraft.getInstance().font,
            fieldX, rowY, fullFieldWidth, 14,
            Component.literal("Intro Resource ID")
        )
        introMusicField.setMaxLength(128)
        introMusicField.value = cachedIntroMusic
        addRenderableWidget(introMusicField)
        editFields["introMusic"] = introMusicField
        rowY += 20

        // Volume cycle selector
        val introVolIndex = (cachedIntroVolume * 10).toInt().coerceIn(0, 20)
        addRenderableWidget(StadiumCycleSelectorWidget(
            x = fieldX, y = rowY, width = cycleSelectorWidth,
            options = volumeOptions,
            displayMapper = volumeDisplayMapper,
            initialIndex = introVolIndex,
            onChange = { value -> cachedIntroVolume = value }
        ))

        // Pitch cycle selector
        val introPitchIndex = (cachedIntroPitch * 10).toInt().coerceIn(0, 20)
        addRenderableWidget(StadiumCycleSelectorWidget(
            x = fieldX + cycleSelectorWidth + 40, y = rowY, width = cycleSelectorWidth,
            options = volumeOptions,
            displayMapper = volumeDisplayMapper,
            initialIndex = introPitchIndex,
            onChange = { value -> cachedIntroPitch = value }
        ))
        rowY += 24

        // Divider between sections
        rowY += 4

        // --- Loop Music ---
        // Resource ID
        val loopMusicField = EditBox(
            Minecraft.getInstance().font,
            fieldX, rowY, fullFieldWidth, 14,
            Component.literal("Loop Resource ID")
        )
        loopMusicField.setMaxLength(128)
        loopMusicField.value = cachedLoopMusic
        addRenderableWidget(loopMusicField)
        editFields["loopMusic"] = loopMusicField
        rowY += 20

        // Volume cycle selector
        val loopVolIndex = (cachedLoopVolume * 10).toInt().coerceIn(0, 20)
        addRenderableWidget(StadiumCycleSelectorWidget(
            x = fieldX, y = rowY, width = cycleSelectorWidth,
            options = volumeOptions,
            displayMapper = volumeDisplayMapper,
            initialIndex = loopVolIndex,
            onChange = { value -> cachedLoopVolume = value }
        ))

        // Pitch cycle selector
        val loopPitchIndex = (cachedLoopPitch * 10).toInt().coerceIn(0, 20)
        addRenderableWidget(StadiumCycleSelectorWidget(
            x = fieldX + cycleSelectorWidth + 40, y = rowY, width = cycleSelectorWidth,
            options = volumeOptions,
            displayMapper = volumeDisplayMapper,
            initialIndex = loopPitchIndex,
            onChange = { value -> cachedLoopPitch = value }
        ))
    }

    // ========== ARENA SUB-TAB RENDERING ==========

    private fun renderPlacementsSubTab(guiGraphics: GuiGraphics, panelX: Int, panelY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 6
        val subTabHeight = 13
        val contentTop = contentY + 4 + subTabHeight + 6

        // Layout type label above map
        val layoutLabel = if (viewingLayoutType == "singles") "Singles Layout" else "Doubles Layout"
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(layoutLabel),
            x = panelX + 10,
            y = contentTop - 10,
            scale = 0.75f,
            colour = ACCENT_COLOR,
            shadow = true
        )
    }

    private fun renderCameraSubTab(guiGraphics: GuiGraphics, panelX: Int, panelY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 6
        val subTabHeight = 13
        val contentTop = contentY + 4 + subTabHeight + 6
        val labelX = panelX + 8
        var rowY = contentTop + 2

        drawScaledText(
            context = guiGraphics,
            text = Component.literal("TIMELINE (SIDE A)"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        rowY += 20

        drawScaledText(
            context = guiGraphics,
            text = Component.literal("TIMELINE (SIDE B)"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        rowY += 20

        drawScaledText(
            context = guiGraphics,
            text = Component.literal("ORIGIN OFFSET (X, Y, Z)"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
    }

    private fun renderMusicSubTab(guiGraphics: GuiGraphics, panelX: Int, panelY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 6
        val subTabHeight = 13
        val contentTop = contentY + 4 + subTabHeight + 6
        val labelX = panelX + 8
        val fieldX = panelX + 90
        val cycleSelectorWidth = 70
        var rowY = contentTop + 2

        // Intro Music header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("INTRO MUSIC"),
            x = labelX,
            y = rowY + 3,
            scale = 0.8f,
            colour = ACCENT_COLOR,
            shadow = true
        )
        rowY += 20

        // Resource ID label
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Resource ID"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        rowY += 20

        // Volume label
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Volume"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        // Pitch label (offset past cycle selector)
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Pitch"),
            x = fieldX + cycleSelectorWidth + 4,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        rowY += 24

        // Divider
        rowY += 4
        guiGraphics.fill(panelX + 8, rowY, panelX + BASE_WIDTH - 8, rowY + 1, DIVIDER_COLOR)
        rowY += 8

        // Loop Music header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("LOOP MUSIC"),
            x = labelX,
            y = rowY + 3,
            scale = 0.8f,
            colour = ACCENT_COLOR,
            shadow = true
        )
        rowY += 20

        // Resource ID label
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Resource ID"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        rowY += 20

        // Volume label
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Volume"),
            x = labelX,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
        // Pitch label
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Pitch"),
            x = fieldX + cycleSelectorWidth + 4,
            y = rowY + 3,
            scale = 0.75f,
            colour = LABEL_COLOR,
            shadow = true
        )
    }

    // ========== RENDERING ==========

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dimmed background
        guiGraphics.fill(0, 0, width, height, 0x80000000.toInt())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        val panelX = (width - BASE_WIDTH) / 2
        val panelY = (height - BASE_HEIGHT) / 2

        // Panel background via blitk
        blitk(
            matrixStack = guiGraphics.pose(),
            texture = bgTexture,
            x = panelX,
            y = panelY,
            width = BASE_WIDTH,
            height = BASE_HEIGHT
        )

        // Title bar background
        guiGraphics.fill(panelX, panelY, panelX + BASE_WIDTH, panelY + StadiumTabWidget.HEIGHT.toInt() + 6, TITLE_BAR_BG)
        guiGraphics.renderOutline(panelX, panelY, BASE_WIDTH, BASE_HEIGHT, BORDER_COLOR)

        // Stadium name in title bar (right side)
        drawScaledText(
            context = guiGraphics,
            text = Component.translatable("cobblemon.stadium_config.title"),
            x = panelX + BASE_WIDTH - 8,
            y = panelY + 5,
            scale = 0.8f,
            colour = TITLE_COLOR,
            shadow = true
        )

        // Configured status badge
        val statusText = if (configured) "ON" else "OFF"
        val statusColor = if (configured) 0x44FF44 else 0xFF4444
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(statusText),
            x = panelX + BASE_WIDTH - 50,
            y = panelY + 5,
            scale = 0.7f,
            colour = statusColor,
            shadow = true
        )

        // Content area background
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 6
        guiGraphics.fill(panelX + 1, contentY, panelX + BASE_WIDTH - 1, panelY + BASE_HEIGHT - 28, CONTENT_BG)

        // Divider above bottom bar
        guiGraphics.fill(panelX + 4, panelY + BASE_HEIGHT - 28, panelX + BASE_WIDTH - 4, panelY + BASE_HEIGHT - 27, DIVIDER_COLOR)

        // Render all widgets (backgrounds, edit boxes, buttons, etc.)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Tab-specific labels/text rendered on top of widget backgrounds
        // Skip when overlay is active so overlay occludes panel content
        if (activeOverlay == null) {
            when (activeTab) {
                Tab.BATTLE -> renderBattleDetailPanel(guiGraphics, panelX, panelY, mouseX, mouseY)
                Tab.ARENA -> {
                    when (activeArenaSubTab) {
                        ArenaSubTab.PLACEMENTS -> renderPlacementsSubTab(guiGraphics, panelX, panelY)
                        ArenaSubTab.CAMERA -> renderCameraSubTab(guiGraphics, panelX, panelY)
                        ArenaSubTab.MUSIC -> renderMusicSubTab(guiGraphics, panelX, panelY)
                    }
                }
            }
        }

        // Render overlay on top of everything
        activeOverlay?.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    // ========== BATTLE DETAIL RENDERING ==========

    private fun renderBattleDetailPanel(guiGraphics: GuiGraphics, panelX: Int, panelY: Int, mouseX: Int, mouseY: Int) {
        val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 8
        val listWidth = 155
        val detailX = panelX + 8 + listWidth + 8
        val detailWidth = BASE_WIDTH - listWidth - 32
        val detailY = contentY

        val selected = selectedFormatId ?: return

        // Detail panel background
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, panelY + BASE_HEIGHT - 28, DETAIL_BG)
        guiGraphics.renderOutline(detailX, detailY, detailWidth, panelY + BASE_HEIGHT - 28 - detailY, DETAIL_BORDER)

        val isPreset = DEFAULT_FORMATS.any { it.first == selected }
        val detailBottom = panelY + BASE_HEIGHT - 28
        guiGraphics.enableScissor(detailX, detailY, detailX + detailWidth, detailBottom)
        if (isPreset) {
            renderPresetDetail(guiGraphics, detailX, detailY, detailWidth, selected, mouseX, mouseY)
        } else if (formatConfigs.containsKey(selected)) {
            renderCustomFormatLabels(guiGraphics, detailX, detailY, detailWidth, selected, mouseX, mouseY)
        }
        guiGraphics.disableScissor()
    }

    private fun renderPresetDetail(guiGraphics: GuiGraphics, detailX: Int, detailY: Int, detailWidth: Int, formatId: String, mouseX: Int, mouseY: Int) {
        val config = getPresetDetails(formatId)
        var rowY = detailY + 6

        // Format name header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(config.formatName),
            x = detailX + 8,
            y = rowY,
            colour = TITLE_COLOR,
            shadow = true
        )

        // Enabled/Disabled badge
        val isEnabled = formatId in selectedDefaultFormats
        val badgeText = if (isEnabled) "ENABLED" else "DISABLED"
        val badgeColor = if (isEnabled) BADGE_GREEN else BADGE_RED
        val badgeWidth = Minecraft.getInstance().font.width(badgeText) + 8
        val badgeX = detailX + detailWidth - badgeWidth - 8
        guiGraphics.fill(badgeX, rowY - 1, badgeX + badgeWidth, rowY + 10, badgeColor)
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(badgeText),
            x = badgeX + 4,
            y = rowY + 1,
            scale = 0.8f,
            colour = 0xFFFFFF,
            shadow = true
        )
        rowY += 18

        // Divider
        guiGraphics.fill(detailX + 4, rowY, detailX + detailWidth - 4, rowY + 1, DETAIL_BORDER)
        rowY += 6

        // 2-column settings grid
        val labelX = detailX + 8
        val valueX = detailX + 90
        val col2LabelX = detailX + detailWidth / 2 + 4
        val col2ValueX = detailX + detailWidth / 2 + 80

        // Row 1: Battle Type, Team Size
        drawSettingLabel(guiGraphics, "Battle Type", labelX, rowY)
        drawSettingValue(guiGraphics, config.battleTypeName.replaceFirstChar { it.uppercase() }, valueX, rowY)
        drawSettingLabel(guiGraphics, "Team Size", col2LabelX, rowY)
        drawSettingValue(guiGraphics, "${config.pokemonLimit}", col2ValueX, rowY)
        rowY += 14

        // Row 2: Level Cap, Team Preview
        drawSettingLabel(guiGraphics, "Level Cap", labelX, rowY)
        drawSettingValue(guiGraphics, if (config.levelCap == -1) "None" else "${config.levelCap}", valueX, rowY)
        drawSettingLabel(guiGraphics, "Preview", col2LabelX, rowY)
        drawSettingValue(guiGraphics, if (config.teamPreview) "Yes" else "No", col2ValueX, rowY)
        rowY += 14

        // Row 3: Turn Timer, Match Timer
        drawSettingLabel(guiGraphics, "Turn Timer", labelX, rowY)
        drawSettingValue(guiGraphics, if (config.moveTimer == -1) "None" else "${config.moveTimer}s", valueX, rowY)
        drawSettingLabel(guiGraphics, "Match Timer", col2LabelX, rowY)
        drawSettingValue(guiGraphics, if (config.matchTimerMinutes == -1) "None" else "${config.matchTimerMinutes}m", col2ValueX, rowY)
        rowY += 20

        // Clauses chips
        if (config.clauses.isNotEmpty()) {
            drawScaledText(
                context = guiGraphics,
                text = Component.literal("Clauses"),
                x = labelX,
                y = rowY,
                scale = 0.7f,
                colour = LABEL_COLOR,
                shadow = true
            )
            rowY += 10
            rowY = renderChips(guiGraphics, config.clauses.toList(), detailX + 8, rowY, detailWidth - 16, CHIP_BLUE)
            rowY += 6
        }

        // Gimmick chips
        if (config.allowedGimmicks.isNotEmpty()) {
            drawScaledText(
                context = guiGraphics,
                text = Component.literal("Gimmicks"),
                x = labelX,
                y = rowY,
                scale = 0.7f,
                colour = LABEL_COLOR,
                shadow = true
            )
            rowY += 10
            renderChips(guiGraphics, config.allowedGimmicks.toList(), detailX + 8, rowY, detailWidth - 16, CHIP_GREEN)
        }
    }

    private fun renderCustomFormatLabels(guiGraphics: GuiGraphics, detailX: Int, detailY: Int, detailWidth: Int, formatKey: String, mouseX: Int, mouseY: Int) {
        val config = formatConfigs[formatKey] ?: return
        var rowY = detailY + 4 - detailScrollOffset

        // Header
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Edit Format"),
            x = detailX + 8,
            y = rowY,
            scale = 0.8f,
            colour = ACCENT_COLOR,
            shadow = true
        )
        rowY += 16

        // Labels for each setting row (widgets are positioned by initCustomFormatDetail)
        val labels = listOf("Name", "Battle Type", "Team Size", "Level Cap", "Preview", "Turn Timer", "Match Timer")
        for (label in labels) {
            drawScaledText(
                context = guiGraphics,
                text = Component.literal(label),
                x = detailX + 8,
                y = rowY + 3,
                scale = 0.8f,
                colour = LABEL_COLOR,
                shadow = true
            )
            rowY += 16
        }

        // Rules & Restrictions section
        rowY += 2
        guiGraphics.fill(detailX + 4, rowY, detailX + detailWidth - 4, rowY + 1, DETAIL_BORDER)
        rowY += 2
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Rules & Restrictions"),
            x = detailX + 8,
            y = rowY,
            scale = 0.8f,
            colour = ACCENT_COLOR,
            shadow = true
        )
        rowY += 14

        // Rebuild category row bounds for click detection
        categoryRowBounds.clear()
        val categories = listOf(
            "Clauses" to config.clauses.size,
            "Banned Pokemon" to config.bannedPokemon.size,
            "Banned Moves" to config.bannedMoves.size,
            "Banned Abilities" to config.bannedAbilities.size,
            "Banned Items" to config.bannedItems.size,
            "Gimmicks" to config.allowedGimmicks.size
        )

        for ((name, count) in categories) {
            val isHovered = mouseX in (detailX + 4)..(detailX + detailWidth - 4) &&
                mouseY in rowY..(rowY + CATEGORY_ROW_HEIGHT - 2)
            val bg = if (isHovered) ROW_HOVER_BG else ROW_BG
            guiGraphics.fill(detailX + 4, rowY, detailX + detailWidth - 4, rowY + CATEGORY_ROW_HEIGHT - 2, bg)

            drawScaledText(
                context = guiGraphics,
                text = Component.literal(name),
                x = detailX + 10,
                y = rowY + 3,
                scale = 0.8f,
                colour = if (isHovered) 0xFFFFFF else 0xA0A0B8,
                shadow = true
            )

            // Count badge
            drawScaledText(
                context = guiGraphics,
                text = Component.literal("($count)"),
                x = detailX + detailWidth - 30,
                y = rowY + 3,
                scale = 0.7f,
                colour = LABEL_COLOR,
                shadow = true
            )

            // Arrow
            drawScaledText(
                context = guiGraphics,
                text = Component.literal("\u25B8"),
                x = detailX + detailWidth - 14,
                y = rowY + 3,
                colour = CATEGORY_ARROW_COLOR,
                shadow = true
            )

            categoryRowBounds.add(CategoryRowBounds(detailX + 4, rowY, detailWidth - 8, CATEGORY_ROW_HEIGHT - 2, name))
            rowY += CATEGORY_ROW_HEIGHT
        }
    }

    private fun drawSettingLabel(guiGraphics: GuiGraphics, label: String, x: Int, y: Int) {
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(label),
            x = x,
            y = y,
            scale = 0.8f,
            colour = LABEL_COLOR,
            shadow = true
        )
    }

    private fun drawSettingValue(guiGraphics: GuiGraphics, value: String, x: Int, y: Int) {
        drawScaledText(
            context = guiGraphics,
            text = Component.literal(value),
            x = x,
            y = y,
            scale = 0.8f,
            colour = VALUE_COLOR,
            shadow = true
        )
    }

    private fun renderChips(guiGraphics: GuiGraphics, items: List<String>, startX: Int, startY: Int, maxWidth: Int, chipColor: Int): Int {
        var chipX = startX
        var chipY = startY
        val font = Minecraft.getInstance().font
        for (item in items) {
            val label = formatName(item)
            val chipWidth = (font.width(label) * 0.8f).toInt() + 8
            if (chipX + chipWidth > startX + maxWidth && chipX != startX) {
                chipX = startX
                chipY += 14
            }
            guiGraphics.fill(chipX, chipY, chipX + chipWidth, chipY + 12, chipColor)
            drawScaledText(
                context = guiGraphics,
                text = Component.literal(label),
                x = chipX + 4,
                y = chipY + 2,
                scale = 0.8f,
                colour = 0xCCCCFF,
                shadow = true
            )
            chipX += chipWidth + 3
        }
        return chipY + 12
    }

    private fun openCategoryOverlay(category: String) {
        val formatKey = selectedFormatId ?: return
        val config = formatConfigs[formatKey] ?: return

        val overlayWidth = 240
        val overlayHeight = 200
        val overlayX = (width - overlayWidth) / 2
        val overlayY = (height - overlayHeight) / 2

        val items: List<String>
        val selectedSet: MutableSet<String>
        val title: String
        val tagColor: Int

        when (category) {
            "Clauses" -> {
                items = CLAUSES
                selectedSet = config.clauses
                title = "Edit Clauses"
                tagColor = CHIP_BLUE
            }
            "Gimmicks" -> {
                items = GIMMICKS
                selectedSet = config.allowedGimmicks
                title = "Edit Gimmicks"
                tagColor = CHIP_GREEN
            }
            "Banned Pokemon" -> {
                items = PokemonSpecies.species.map { it.showdownId() }.sorted()
                selectedSet = config.bannedPokemon
                title = "Banned Pokemon"
                tagColor = BADGE_RED
            }
            "Banned Moves" -> {
                items = Moves.names().sorted()
                selectedSet = config.bannedMoves
                title = "Banned Moves"
                tagColor = BADGE_RED
            }
            "Banned Abilities" -> {
                items = Abilities.all().map { it.name }.sorted()
                selectedSet = config.bannedAbilities
                title = "Banned Abilities"
                tagColor = BADGE_RED
            }
            "Banned Items" -> {
                items = BuiltInRegistries.ITEM.keySet().map { it.path }.sorted()
                selectedSet = config.bannedItems
                title = "Banned Items"
                tagColor = BADGE_RED
            }
            else -> return
        }

        activeOverlay = StadiumOverlayWidget(
            x = overlayX,
            y = overlayY,
            width = overlayWidth,
            height = overlayHeight,
            title = title,
            allItems = items,
            selectedItems = selectedSet,
            tagColor = tagColor,
            onClose = {
                activeOverlay = null
                refreshWidgets()
            },
            addWidget = { widget -> addRenderableWidget(widget) }
        )
    }

    // ========== INPUT ROUTING ==========

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        activeOverlay?.let {
            it.mouseClicked(mouseX, mouseY, button)
            return true
        }

        // Check category row clicks when Battle tab is active and a custom format is selected
        if (activeTab == Tab.BATTLE && selectedFormatId != null && formatConfigs.containsKey(selectedFormatId)) {
            for (row in categoryRowBounds) {
                if (mouseX.toInt() in row.x..(row.x + row.width) && mouseY.toInt() in row.y..(row.y + row.height)) {
                    openCategoryOverlay(row.category)
                    return true
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        activeOverlay?.let {
            if (it.keyPressed(keyCode, scanCode, modifiers)) return true
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(char: Char, modifiers: Int): Boolean {
        activeOverlay?.let {
            if (it.charTyped(char, modifiers)) return true
            return true
        }
        return super.charTyped(char, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        activeOverlay?.let {
            if (it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
            return true
        }

        // Detail panel scroll for custom format editing
        if (activeTab == Tab.BATTLE && selectedFormatId != null && formatConfigs.containsKey(selectedFormatId)) {
            val panelX = (width - BASE_WIDTH) / 2
            val panelY = (height - BASE_HEIGHT) / 2
            val contentY = panelY + StadiumTabWidget.HEIGHT.toInt() + 8
            val listWidth = 155
            val detailX = panelX + 8 + listWidth + 8
            val detailWidth = BASE_WIDTH - listWidth - 32
            val detailBottom = panelY + BASE_HEIGHT - 28
            val detailHeight = detailBottom - contentY

            if (mouseX.toInt() in detailX..(detailX + detailWidth) && mouseY.toInt() in contentY..detailBottom) {
                val scrollAmount = (verticalAmount * 12).toInt()
                val maxScroll = (detailContentHeight - detailHeight).coerceAtLeast(0)
                detailScrollOffset = (detailScrollOffset - scrollAmount).coerceIn(0, maxScroll)
                refreshWidgets()
                return true
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    // ========== FIELD CACHING ==========

    private fun cacheFieldValues() {
        editFields["cameraTimeline"]?.let { cachedCameraTimeline = it.value }
        editFields["cameraTimelineSideB"]?.let { cachedCameraTimelineSideB = it.value }
        editFields["camOffX"]?.let {
            val vec = parseVec3FromFields("camOff")
            if (vec != null) cachedCamOffset = vec
        }
        editFields["introMusic"]?.let { cachedIntroMusic = it.value }
        // introVolume and introPitch are updated directly by StadiumCycleSelectorWidget.onChange
        editFields["loopMusic"]?.let { cachedLoopMusic = it.value }
        // loopVolume and loopPitch are updated directly by StadiumCycleSelectorWidget.onChange
    }

    private fun parseVec3FromFields(prefix: String): Vec3? {
        val x = editFields["${prefix}X"]?.value?.toDoubleOrNull() ?: return null
        val y = editFields["${prefix}Y"]?.value?.toDoubleOrNull() ?: return null
        val z = editFields["${prefix}Z"]?.value?.toDoubleOrNull() ?: return null
        return Vec3(x, y, z)
    }

    // ========== SAVE ==========

    private fun save() {
        val cameraTimeline = cachedCameraTimeline.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.cameraTimeline }
        }
        val cameraTimelineSideB = cachedCameraTimelineSideB.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.cameraTimelineSideB }
        }
        val introMusic = cachedIntroMusic.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.introMusic }
        }
        val loopMusic = cachedLoopMusic.takeIf { it.isNotEmpty() }?.let {
            try { ResourceLocation.parse(it) } catch (_: Exception) { data.loopMusic }
        }

        // Only save layouts that have actual placements
        val singlesLayout = editingSinglesLayout.takeIf {
            it.side1.actors.isNotEmpty() || it.side2.actors.isNotEmpty() ||
            it.side1.pokemon.isNotEmpty() || it.side2.pokemon.isNotEmpty()
        }
        val doublesLayout = editingDoublesLayout.takeIf {
            it.side1.actors.isNotEmpty() || it.side2.actors.isNotEmpty() ||
            it.side1.pokemon.isNotEmpty() || it.side2.pokemon.isNotEmpty()
        }

        val updatedData = data.copy(
            formatConfigs = formatConfigs.toMutableMap(),
            activeFormat = activeFormat,
            selectedDefaultFormats = selectedDefaultFormats.toMutableSet(),
            singlesLayout = singlesLayout,
            doublesLayout = doublesLayout,
            cameraTimeline = cameraTimeline,
            cameraTimelineSideB = cameraTimelineSideB,
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
            allowedGimmicks = mutableSetOf()
        )
    }

    private fun formatDouble(value: Double?): String = value?.let { "%.2f".format(it) } ?: ""

    private fun formatName(id: String): String = id.replace("_", " ").replaceFirstChar { it.uppercase() }

    private fun refreshWidgets() {
        init()
    }

    override fun isPauseScreen(): Boolean = false
}
