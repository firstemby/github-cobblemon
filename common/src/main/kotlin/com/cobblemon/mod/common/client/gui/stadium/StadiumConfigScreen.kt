/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.battles.stadium.StadiumSpectatorArea

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.client.editor.cinematic.EditorStadiumBridge
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumArena
import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.battles.stadium.StadiumBlockPlacement
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.battles.stadium.StadiumItemCatalog
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumButton
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumCheckbox
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumDropdown
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumEditBox
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumFormatLibrary
import com.cobblemon.mod.common.net.messages.server.stadium.RequestStadiumPlacementWandPacket
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumConfigResultPacket
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.Locale
import java.util.UUID

class StadiumConfigScreen(
    private val blockPos: BlockPos,
    data: StadiumBlockData,
    initialTab: Tab = Tab.BATTLE,
    initialArenaSubTab: ArenaSubTab = ArenaSubTab.PLACEMENTS,
    initialLayout: String = "singles",
    private val heldItems: Map<ResourceLocation, String> = StadiumItemCatalog.collect()
) : Screen(Component.translatable("cobblemon.stadium.ui.stadium_console")) {
    enum class Tab { BATTLE, ARENA }
    enum class ArenaSubTab { PLACEMENTS, CAMERA, MUSIC }
    private enum class Page(val key: String) {
        SETUP("setup"), FORMATS("formats"), ARENA("arena"), SCENE("scene"), LAUNCH("launch"), RULES("rules"), ADVANCED("advanced"), MAP("map");

        val label get() = I18n.get("cobblemon.stadium.ui.page.$key")
    }
    private data class Row(val offset: Int, val height: Int, val label: String, val widgets: List<AbstractWidget>, val note: Boolean = false, val controlOffset: Int = 13)

    private val draft = StadiumBlockData.fromNbt(data.toNbt())
    private val original = draft.toNbt()
    private var page = if (initialTab == Tab.ARENA) {
        if (initialArenaSubTab == ArenaSubTab.PLACEMENTS) Page.ARENA else Page.SCENE
    } else Page.FORMATS
    private var selectedFormat = draft.activeFormat
    private var layoutType = initialLayout.takeIf { it in StadiumConfiguration.layoutIds } ?: "singles"
    private var geometry = StadiumScreenLayout.fit(320, 240)
    private val rows = mutableListOf<Row>()
    private var rowCursor = 0
    private var scroll = 0
    private var status = ""
    private var participantA = ""
    private var participantB = ""
    private var closing = false
    private var editAfterSave = false
    private var savingAt = 0L
    private var savingRequestId: UUID? = null
    private var terrainMap: ArenaMapWidget? = null
    private var preciseArena = false
    private val invalidFields = mutableSetOf<String>()
    private val rawValues = mutableMapOf<String, String>()
    private var openDropdown: StadiumDropdown<*>? = null
    private var draggingScroll = false

    override fun init() {
        openDropdown?.close()
        openDropdown = null
        draggingScroll = false
        val formatEditor = page in listOf(Page.FORMATS, Page.RULES, Page.ADVANCED)
        geometry = StadiumScreenLayout.fit(width, height, formatEditor)
        terrainMap?.refreshTerrain()
        terrainMap = null
        clearWidgets()
        rows.clear()
        rowCursor = 0
        val tabs = listOf(Page.SETUP, Page.FORMATS, Page.ARENA, Page.SCENE, Page.LAUNCH)
        val navigationRail = geometry.sidebar && !formatEditor
        val tabWidth = if (navigationRail) 88 else (geometry.width - 24) / tabs.size
        tabs.forEachIndexed { index, tab ->
            val tabX = geometry.x + 12 + if (navigationRail) 0 else index * tabWidth
            val tabY = geometry.y + if (navigationRail) 48 + index * 28 else 38
            val caption = if (tab == Page.SCENE && tabWidth < 64) I18n.get("cobblemon.stadium.ui.scene") else tab.label
            addRenderableWidget(StadiumButton(tabX, tabY, tabWidth - 3, 22,
                Component.literal(caption), page == tab || page in listOf(Page.RULES, Page.ADVANCED) && tab == Page.FORMATS || page == Page.MAP && tab == Page.ARENA) {
                page = tab; scroll = 0; init()
            })
        }
        if (formatEditor && geometry.sidebar) {
            addRenderableWidget(StadiumFormatLibrary(geometry.x + 13, geometry.contentTop + 16, 86,
                geometry.contentHeight - 44, draft.formatConfigs, selectedFormat) { selectedFormat = it; scroll = 0; init() })
            addRenderableWidget(StadiumButton(geometry.x + 13, geometry.contentBottom - 24, 86, 22, Component.translatable("cobblemon.stadium.ui.format")) { addFormat(false) })
        }
        when (page) {
            Page.SETUP -> setupPage()
            Page.FORMATS -> formatsPage()
            Page.RULES -> rulesPage()
            Page.ADVANCED -> advancedPage()
            Page.ARENA -> arenaPage()
            Page.SCENE -> scenePage()
            Page.LAUNCH -> launchPage()
            Page.MAP -> mapPage()
        }
        val bottom = geometry.y + geometry.height - 30
        addRenderableWidget(StadiumButton(geometry.x + 12, bottom, 64, 22, Component.translatable("cobblemon.stadium.ui.close")) { onClose() })
        addRenderableWidget(StadiumButton(geometry.x + geometry.width - 111, bottom, 99, 22, Component.translatable("cobblemon.stadium.ui.save_close"), true) { save() })
        positionRows()
    }

    private fun setupPage() {
        note(I18n.get("cobblemon.stadium.ui.battle_stadium"), I18n.get("cobblemon.stadium.ui.choose_a_format_place_your_arena"))
        field(I18n.get("cobblemon.stadium.ui.stadium_name"), "name", draft.name, 48, hint = draft.displayName) { draft.name = it; true }
        dropdown(I18n.get("cobblemon.stadium.ui.active_format_2"), draft.formatConfigs.keys.toList(), draft.activeFormat, { draft.formatConfigs[it]?.displayName?.string ?: it }) { draft.activeFormat = it; init() }
        toggle(I18n.get("cobblemon.stadium.ui.enable_stadium_battles"), draft.isConfigured) { draft.isConfigured = it }
        val readiness = StadiumConfiguration.readiness(draft)
        note(if (readiness.isEmpty()) I18n.get("cobblemon.stadium.ui.ready_to_enable") else I18n.get("cobblemon.stadium.ui.setup_checklist"),
            if (readiness.isEmpty()) I18n.get("cobblemon.stadium.ui.arena_placements_and_active_format_are") else readiness.joinToString("\n") { it.string })
        action(I18n.get("cobblemon.stadium.ui.command_access"), I18n.get("cobblemon.stadium.ui.copy_launch_command")) {
            minecraft?.keyboardHandler?.clipboard = "/stadium battle ${draft.stadiumId} active <playerA> <playerB>"
            status = I18n.get("cobblemon.stadium.ui.command_copied_replace_the_participant_names")
        }
    }

    private fun formatsPage() {
        if (selectedFormat !in draft.formatConfigs) selectedFormat = draft.formatConfigs.keys.firstOrNull().orEmpty()
        if (!geometry.sidebar) {
            val selector = StadiumDropdown(geometry.contentLeft, 0, geometry.contentWidth - 30, 22, I18n.get("cobblemon.stadium.ui.your_formats_2"),
                draft.formatConfigs.keys.toList(), selectedFormat, { draft.formatConfigs[it]?.displayName?.string ?: it },
                { openDropdown?.close(); openDropdown = it }) { selectedFormat = it; scroll = 0; init() }
            val add = StadiumButton(geometry.contentLeft + geometry.contentWidth - 26, 0, 26, 22, Component.literal("+")) { addFormat(false) }
            addRow("", 26, listOf(selector, add), controlOffset = 2)
        }
        val config = draft.formatConfigs[selectedFormat] ?: return
        formatTabs()
        field(I18n.get("cobblemon.stadium.ui.format_name"), "format.$selectedFormat.name", config.formatName, 40, hint = config.displayName) { updateFormat { copy(formatName = it) }; true }
        val half = (geometry.contentWidth - 4) / 2
        addRow(I18n.get("cobblemon.stadium.ui.battle_style"), 40, listOf("singles", "doubles").mapIndexed { index, type ->
            StadiumButton(geometry.contentLeft + index * (half + 4), 0, half, 22, Component.translatable("cobblemon.stadium.layout.$type"), config.battleTypeName == type) {
                updateFormat { copy(battleTypeName = type, pokemonLimit = pokemonLimit.coerceAtLeast(if (type == "doubles") 2 else 1)) }
                rawValues.remove("format.$selectedFormat.party"); invalidFields.remove("format.$selectedFormat.party"); init()
            }
        })
        numberField(I18n.get("cobblemon.stadium.ui.team_size_6", if (config.battleTypeName == "doubles") 2 else 1), "party", config.pokemonLimit,
            (if (config.battleTypeName == "doubles") 2 else 1)..6) { updateFormat { copy(pokemonLimit = it) } }
        numberField(I18n.get("cobblemon.stadium.ui.maximum_level_0_100_0_no"), "level", config.levelCap.coerceAtLeast(0), 0..100) {
            updateFormat { copy(levelCap = if (it == 0) -1 else it) }
        }
        toggle(I18n.get("cobblemon.stadium.ui.unique_species"), "species_clause" in config.clauses) { checked ->
            updateFormat { copy(clauses = clauses.toMutableSet().also { if (checked) it.add("species_clause") else it.remove("species_clause") }) }
        }
        toggle(I18n.get("cobblemon.stadium.ui.unique_held_battle_items"), config.uniqueHeldItems) { updateFormat { copy(uniqueHeldItems = it) } }
        toggle(I18n.get("cobblemon.stadium.ui.allow_held_battle_items"), config.allowHeldItems) { updateFormat { copy(allowHeldItems = it) } }
        action(I18n.get("cobblemon.stadium.ui.default_for_this_stadium"), if (draft.activeFormat == selectedFormat) I18n.get("cobblemon.stadium.ui.active_format_2") else I18n.get("cobblemon.stadium.ui.make_active")) { draft.activeFormat = selectedFormat; init() }
        action(I18n.get("cobblemon.stadium.ui.remove_format"), I18n.get("cobblemon.stadium.ui.delete_this_format")) {
            minecraft?.setScreen(ConfirmScreen({ confirmed ->
                if (confirmed) {
                    draft.formatConfigs.remove(selectedFormat)
                    if (draft.activeFormat == selectedFormat) draft.activeFormat = draft.formatConfigs.keys.firstOrNull().orEmpty()
                    rawValues.keys.removeIf { it.startsWith("format.$selectedFormat.") }
                    invalidFields.removeIf { it.startsWith("format.$selectedFormat.") }
                }
                minecraft?.setScreen(this)
            }, Component.translatable("cobblemon.stadium.ui.delete", config.displayName), Component.translatable("cobblemon.stadium.ui.this_removes_the_format_from_your")))
        }
    }

    private fun formatTabs() {
        val tabs = listOf(Page.FORMATS to I18n.get("cobblemon.stadium.ui.battle_rules"), Page.RULES to I18n.get("cobblemon.stadium.ui.ban_lists_2"), Page.ADVANCED to I18n.get("cobblemon.stadium.ui.engine"))
        val itemWidth = (geometry.contentWidth - 8) / 3
        addRow("", 26, tabs.mapIndexed { index, (target, label) ->
            StadiumButton(geometry.contentLeft + index * (itemWidth + 4), 0, itemWidth, 22, Component.literal(label), page == target) {
                page = target; scroll = 0; init()
            }
        }, controlOffset = 2)
    }

    private fun addFormat(doubles: Boolean) {
        if (draft.formatConfigs.size >= StadiumConfiguration.MAX_FORMATS) { status = I18n.get("cobblemon.stadium.ui.maximum_number_of_formats_reached"); return }
        val id = generateSequence(1) { it + 1 }.map { "format_$it" }.first { it !in draft.formatConfigs }
        draft.formatConfigs[id] = StadiumConfiguration.template(doubles)
        selectedFormat = id
        if (draft.activeFormat.isEmpty()) draft.activeFormat = id
        init()
    }

    private fun updateFormat(change: StadiumFormatConfig.() -> StadiumFormatConfig) {
        draft.formatConfigs[selectedFormat]?.let { draft.formatConfigs[selectedFormat] = it.change() }
    }

    private fun rulesPage() {
        val config = draft.formatConfigs[selectedFormat] ?: return
        formatTabs()
        note(I18n.get("cobblemon.stadium.ui.ban_lists"), I18n.get("cobblemon.stadium.ui.browse_inspect_and_select_restrictions_pok"))
        StadiumBanCatalog.Category.entries.forEach { category ->
            val entries = category.entries(config)
            action(category.label, I18n.get("cobblemon.stadium.ui.choose_selected", entries.size)) {
                minecraft?.setScreen(StadiumBanPickerScreen(this, config, category, heldItems) { updated ->
                    updateFormat { copy(bannedPokemon = updated.bannedPokemon, bannedMoves = updated.bannedMoves,
                        bannedAbilities = updated.bannedAbilities, bannedItems = updated.bannedItems) }
                })
            }
        }
    }

    private fun advancedPage() {
        val config = draft.formatConfigs[selectedFormat] ?: return
        formatTabs()
        note(I18n.get("cobblemon.stadium.ui.match_flow"), I18n.get("cobblemon.stadium.ui.team_preview_lets_each_trainer_order"))
        toggle(I18n.get("cobblemon.stadium.ui.team_preview_selection"), config.teamPreview) { updateFormat { copy(teamPreview = it) } }
        dropdown(I18n.get("cobblemon.stadium.ui.turn_timer"), (listOf(-1, 30, 60, 90, 150, 300) + config.moveTimer).distinct().sorted(), config.moveTimer, { if (it == -1) I18n.get("cobblemon.stadium.ui.off") else I18n.get("cobblemon.stadium.ui.seconds", it) }) { updateFormat { copy(moveTimer = it) } }
        dropdown(I18n.get("cobblemon.stadium.ui.match_timer"), (listOf(-1, 10, 20, 30, 60) + config.matchTimerMinutes).distinct().sorted(), config.matchTimerMinutes, { if (it == -1) I18n.get("cobblemon.stadium.ui.off") else I18n.get("cobblemon.stadium.ui.minutes", it) }) { updateFormat { copy(matchTimerMinutes = it) } }
        StadiumConfiguration.clauses.filter { it != "species_clause" }.forEach { clause ->
            toggle(I18n.get("cobblemon.stadium.clause.$clause"), clause in config.clauses) { checked ->
                updateFormat { copy(clauses = clauses.toMutableSet().also { if (checked) it.add(clause) else it.remove(clause) }) }
            }
        }
        note(I18n.get("cobblemon.stadium.ui.clauses"), I18n.get("cobblemon.stadium.ui.extra_clauses_above_still_require_engine"))
        StadiumConfiguration.gimmicks.forEach { gimmick ->
            toggle(I18n.get("cobblemon.stadium.ui.allow_mechanic", I18n.get("cobblemon.stadium.mechanic.$gimmick")), gimmick in config.allowedGimmicks) { checked ->
                updateFormat { copy(allowedGimmicks = allowedGimmicks.toMutableSet().also { if (checked) it.add(gimmick) else it.remove(gimmick) }) }
            }
        }
    }

    private fun numberField(label: String, key: String, value: Int, range: IntRange, change: (Int) -> Unit) {
        field(label, "format.$selectedFormat.$key", value.toString(), 3, range) { raw ->
            val number = raw.toIntOrNull()
            if (number == null || number !in range) false else { change(number); true }
        }
    }

    private fun arenaPage() {
        val arena = draft.arena
        dropdown(I18n.get("cobblemon.stadium.ui.arena_layout"), StadiumConfiguration.layoutIds, layoutType, { I18n.get("cobblemon.stadium.layout.$it") }) { layoutType = it; init() }
        note(I18n.get("cobblemon.stadium.ui.world_setup"), I18n.get("cobblemon.stadium.ui.use_the_wand_to_select_two"))
        fun requestWand(mode: String, index: Int = 0) {
            storeLayout(draft.layout(layoutType) ?: StadiumBlockLayout.createDefault())
            save(action = UpdateStadiumBlockPacket.Action.WAND,
                wand = RequestStadiumPlacementWandPacket(blockPos, layoutType, 1, mode, index))
        }
        buttons(I18n.get("cobblemon.stadium.ui.setup_wand"), listOf(I18n.get("cobblemon.stadium.ui.guided_setup") to { requestWand("setup") }, I18n.get("cobblemon.stadium.ui.select_arena_corners") to { requestWand("arena") }))
        note(I18n.get("cobblemon.stadium.ui.spectator_bleachers"), I18n.get("cobblemon.stadium.ui.mark_the_lowest_corner_and_opposite"))
        if (draft.spectatorAreas.size < StadiumSpectatorArea.MAX_AREAS) {
            action(I18n.get("cobblemon.stadium.ui.bleacher_zones_8", draft.spectatorAreas.size), I18n.get("cobblemon.stadium.ui.add_zone_with_wand")) { requestWand("bleachers", draft.spectatorAreas.size) }
        }
        draft.spectatorAreas.forEachIndexed { index, _ ->
            buttons(I18n.get("cobblemon.stadium.ui.bleachers", index + 1), listOf(I18n.get("cobblemon.stadium.ui.reselect") to { requestWand("bleachers", index) },
                I18n.get("cobblemon.stadium.ui.remove") to { draft.spectatorAreas.removeAt(index); init() }))
        }
        if (arena == null) {
            note(I18n.get("cobblemon.stadium.ui.arena_area"), I18n.get("cobblemon.stadium.ui.define_the_arena_before_placing_trainers"))
            action(I18n.get("cobblemon.stadium.ui.arena_size"), I18n.get("cobblemon.stadium.ui.define_around_placements")) {
                val points = StadiumConfiguration.layoutIds.mapNotNull(draft::layout).flatMap {
                    it.side1.actors + it.side1.pokemon + it.side2.actors + it.side2.pokemon
                }.map { it.position }
                val center = if (points.isEmpty()) Vec3.atCenterOf(blockPos) else Vec3(
                    (points.minOf { it.x } + points.maxOf { it.x }) / 2, points.map { it.y }.average(),
                    (points.minOf { it.z } + points.maxOf { it.z }) / 2)
                draft.arena = StadiumArena(center,
                    if (points.isEmpty()) 16.0 else (points.maxOf { it.x } - points.minOf { it.x } + 4).coerceIn(2.0, 128.0),
                    if (points.isEmpty()) 12.0 else (points.maxOf { it.z } - points.minOf { it.z } + 4).coerceIn(2.0, 128.0))
                init()
            }
        } else {
            note(I18n.get("cobblemon.stadium.ui.arena_2"), I18n.get("cobblemon.stadium.ui.blocks_select_corners_with_the_wand", number(arena.width), number(arena.depth)))
            action(I18n.get("cobblemon.stadium.ui.fine_adjustments"), if (preciseArena) I18n.get("cobblemon.stadium.ui.hide_coordinates") else I18n.get("cobblemon.stadium.ui.show_coordinates")) { preciseArena = !preciseArena; init() }
        }
        if (arena != null && preciseArena) {
            listOf(I18n.get("cobblemon.stadium.ui.center_x") to arena.center.x, I18n.get("cobblemon.stadium.ui.floor_y") to arena.center.y, I18n.get("cobblemon.stadium.ui.center_z") to arena.center.z,
                I18n.get("cobblemon.stadium.ui.width_x") to arena.width, I18n.get("cobblemon.stadium.ui.depth_z") to arena.depth).forEachIndexed { index, (label, value) ->
                field(I18n.get("cobblemon.stadium.ui.arena", label), "arena.$index", number(value), 16) { raw ->
                    val number = raw.toDoubleOrNull()
                    val current = draft.arena!!
                    if (number == null || !number.isFinite() || (index >= 3 && number !in 2.0..128.0)) false else {
                        draft.arena = when (index) {
                            0 -> current.copy(center = Vec3(number, current.center.y, current.center.z))
                            1 -> current.copy(center = Vec3(current.center.x, number, current.center.z))
                            2 -> current.copy(center = Vec3(current.center.x, current.center.y, number))
                            3 -> current.copy(width = number)
                            else -> current.copy(depth = number)
                        }; true
                    }
                }
            }
        }
        action(I18n.get("cobblemon.stadium.ui.terrain_preview"), I18n.get("cobblemon.stadium.ui.open_arena_map")) { page = Page.MAP; scroll = 0; init() }
        note(I18n.get("cobblemon.stadium.ui.world_placements"), I18n.get("cobblemon.stadium.ui.place_both_sides_inside_the_arena"))
        val layout = draft.layout(layoutType) ?: StadiumBlockLayout.createDefault()
        if (layoutType == "custom") {
            note(I18n.get("cobblemon.stadium.ui.custom_cast"), I18n.get("cobblemon.stadium.ui.prepare_larger_cinematics_here_battles_currently"))
            listOf(I18n.get("cobblemon.stadium.ui.trainers_per_side") to true, I18n.get("cobblemon.stadium.ui.pok_mon_per_side") to false).forEach { (label, trainers) ->
                dropdown(label, (1..StadiumConfiguration.MAX_LAYOUT_SLOTS).toList(),
                    if (trainers) layout.trainerSlots else layout.pokemonSlots, Int::toString) { count ->
                    if (listOf(layout.side1, layout.side2).any { (if (trainers) it.actors else it.pokemon).size > count }) {
                        status = I18n.get("cobblemon.stadium.ui.clear_the_extra_placements_before_reducing")
                    } else {
                        if (trainers) layout.trainerSlots = count else layout.pokemonSlots = count
                        storeLayout(layout); init()
                    }
                }
            }
        }
        if (arena == null) return
        listOf(layout.side1, layout.side2).forEachIndexed { sideIndex, side ->
            listOf("actors" to side.actors, "pokemon" to side.pokemon).forEach { (type, placements) ->
                val count = if (layoutType == "custom") { if (type == "actors") layout.trainerSlots else layout.pokemonSlots }
                    else draft.slots(layoutType, type)
                repeat(count) { index ->
                    val label = I18n.get(if (type == "actors") "cobblemon.stadium.ui.trainer_slot" else "cobblemon.stadium.ui.pokemon_slot", sideIndex + 1, index + 1)
                    val position = placements.getOrNull(index)?.position
                    note(label, position?.let { I18n.get("cobblemon.stadium.ui.coordinates", number(it.x), number(it.y), number(it.z)) } ?: I18n.get("cobblemon.stadium.ui.not_placed"))
                    buttons("", listOf(I18n.get("cobblemon.stadium.ui.stand_here") to {
                        val player = minecraft?.player
                        if (player != null && (draft.arena?.contains(player.position()) != true ||
                                !StadiumConfiguration.validPosition(player.position(), Vec3.atLowerCornerOf(blockPos)))) {
                            status = I18n.get("cobblemon.stadium.ui.stand_inside_the_arena_and_within")
                        } else if (player != null && index <= placements.size) {
                            val placement = StadiumBlockPlacement(player.position())
                            if (index == placements.size) placements.add(placement) else placements[index] = placement
                            storeLayout(layout)
                            init()
                        } else status = I18n.get("cobblemon.stadium.ui.place_the_previous_slot_first")
                    }, I18n.get("cobblemon.stadium.ui.move_marker") to {
                        if (index > placements.size) status = I18n.get("cobblemon.stadium.ui.place_the_previous_slot_first")
                        else {
                            storeLayout(layout)
                            save(action = UpdateStadiumBlockPacket.Action.WAND,
                                wand = RequestStadiumPlacementWandPacket(blockPos, layoutType, sideIndex + 1, type, index))
                        }
                    }, I18n.get("cobblemon.stadium.ui.clear") to {
                        if (index in placements.indices) placements.removeAt(index)
                        storeLayout(layout)
                        init()
                    }))
                }
            }
        }
    }

    private fun storeLayout(layout: StadiumBlockLayout) {
        draft.setLayout(layoutType, layout)
    }

    private fun mapPage() {
        action(I18n.get("cobblemon.stadium.ui.arena_terrain_map"), I18n.get("cobblemon.stadium.ui.back_to_placements")) { page = Page.ARENA; scroll = 0; init() }
        note(I18n.get("cobblemon.stadium.ui.select_area"), I18n.get("cobblemon.stadium.ui.click_two_opposite_corners_to_set"))
        val mapSize = geometry.mapSizeAfter(rowCursor)
        val layout = draft.layout(layoutType)
        val map = ArenaMapWidget(geometry.contentLeft + (geometry.contentWidth - mapSize) / 2,
            geometry.contentTop + rowCursor + 2, mapSize, mapSize, blockPos, layout, draft.arena)
        map.onAreaSelected = { selected ->
            if (selected.valid(Vec3.atLowerCornerOf(blockPos))) {
                draft.arena = selected
                (0..4).forEach { rawValues.remove("arena.$it"); invalidFields.remove("arena.$it") }
                status = I18n.get("cobblemon.stadium.ui.arena_set_to_blocks_save_to", number(selected.width), number(selected.depth))
            } else status = I18n.get("cobblemon.stadium.ui.choose_an_area_2_128_blocks")
            init()
        }
        terrainMap = map
        addRow("", mapSize + 4, listOf(map), controlOffset = 2)
    }

    private fun scenePage() {
        dropdown(I18n.get("cobblemon.stadium.ui.cinematic_preview_layout"), StadiumConfiguration.layoutIds, draft.cinematicLayout,
            { I18n.get("cobblemon.stadium.layout.$it") }) { draft.cinematicLayout = it; init() }
        note(I18n.get("cobblemon.stadium.ui.cinematic_engine"), I18n.get("cobblemon.stadium.ui.choose_an_optional_scene_with_one"))
        field(I18n.get("cobblemon.stadium.ui.scene_resource_id_optional"), "scene", draft.cinematicScene?.toString().orEmpty(), 128) { value ->
            val id = if (value.isBlank()) null else ResourceLocation.tryParse(value)
            if (value.isNotBlank() && id == null) false else { draft.cinematicScene = id; true }
        }
        buttons(I18n.get("cobblemon.stadium.ui.scene_template"), listOf(I18n.get("cobblemon.stadium.ui.adaptive_stadium") to {
            draft.cinematicScene = ResourceLocation.parse("cobblemon:stadium_dynamic")
            rawValues.remove("scene"); invalidFields.remove("scene"); init()
        }, I18n.get("cobblemon.stadium.ui.no_cinematic") to { draft.cinematicScene = null; rawValues.remove("scene"); invalidFields.remove("scene"); init() }))
        action(I18n.get("cobblemon.stadium.ui.cinematic_authoring"), I18n.get("cobblemon.stadium.ui.save_edit_cinematic")) {
            if (minecraft?.player?.hasPermissions(2) != true) status = I18n.get("cobblemon.stadium.ui.cinematic_editing_requires_operator_permission")
            else if (SceneEditorOverlay.activeScene?.isDirty == true) status = I18n.get("cobblemon.stadium.ui.save_the_current_cinematic_editor_scene")
            else if (draft.cinematicScene == null) status = I18n.get("cobblemon.stadium.ui.choose_a_cinematic_first")
            else { editAfterSave = true; if (!save()) editAfterSave = false }
        }
        note(I18n.get("cobblemon.stadium.ui.adaptive_framing"), I18n.get("cobblemon.stadium.ui.the_arena_orbit_uses_the_area"))
        listOf("X", "Y", "Z").forEachIndexed { index, axis ->
            val vector = draft.cameraOriginOffset
            field(I18n.get("cobblemon.stadium.ui.scene_origin_offset", axis), "offset$axis", number(listOf(vector.x, vector.y, vector.z)[index]), 16) { raw ->
                val parsed = raw.toDoubleOrNull()
                if (parsed == null || !parsed.isFinite()) false else {
                    val current = draft.cameraOriginOffset
                    draft.cameraOriginOffset = when (index) {
                        0 -> Vec3(parsed, current.y, current.z)
                        1 -> Vec3(current.x, parsed, current.z)
                        else -> Vec3(current.x, current.y, parsed)
                    }; true
                }
            }
        }
        note(I18n.get("cobblemon.stadium.ui.scene_contract"), I18n.get("cobblemon.stadium.ui.the_scene_receives_user_and_target"))
    }

    private fun launchPage() {
        note(I18n.get("cobblemon.stadium.ui.start_a_match"), I18n.get("cobblemon.stadium.ui.use_nearby_player_names_or_npc"))
        if (participantA.isEmpty()) participantA = minecraft?.player?.gameProfile?.name.orEmpty()
        field(I18n.get("cobblemon.stadium.ui.side_1_player_name_or_npc"), "participantA", participantA, 64) { participantA = it; true }
        field(I18n.get("cobblemon.stadium.ui.side_2_player_name_or_npc"), "participantB", participantB, 64) { participantB = it; true }
        note(I18n.get("cobblemon.stadium.ui.active_format"), draft.formatConfigs[draft.activeFormat]?.displayName?.string ?: I18n.get("cobblemon.stadium.ui.no_format_selected"))
        action(I18n.get("cobblemon.stadium.ui.launch"), I18n.get("cobblemon.stadium.ui.save_start_battle")) {
            if (!draft.isConfigured) status = I18n.get("cobblemon.stadium.ui.enable_the_stadium_on_the_stadium")
            else if (participantA.isBlank() || participantB.isBlank() || participantA == participantB) status = I18n.get("cobblemon.stadium.ui.choose_two_different_participants")
            else save(action = UpdateStadiumBlockPacket.Action.START)
        }
        note(I18n.get("cobblemon.stadium.ui.server_authority"), I18n.get("cobblemon.stadium.ui.only_the_owner_or_an_operator"))
    }

    private fun field(label: String, key: String, value: String, maxLength: Int, range: IntRange? = null, hint: Component? = null, change: (String) -> Boolean) {
        val box = StadiumEditBox(font, geometry.contentLeft, geometry.contentTop + rowCursor + 13, geometry.contentWidth, Component.literal(label), range)
        box.setMaxLength(maxLength)
        hint?.let { box.setHint(it) }
        box.value = rawValues[key] ?: value
        box.setResponder {
            rawValues[key] = it
            if (change(it)) invalidFields.remove(key) else invalidFields.add(key)
            box.setTextColor(if (key in invalidFields) 0xAB3939 else StadiumSkin.INK)
        }
        box.setTextColor(if (key in invalidFields) 0xAB3939 else StadiumSkin.INK)
        addRow(label, 40, listOf(box))
    }

    private fun toggle(label: String, initial: Boolean, change: (Boolean) -> Unit) {
        addRow("", 26, listOf(StadiumCheckbox(geometry.contentLeft, geometry.contentTop + rowCursor + 2,
            geometry.contentWidth, label, initial, change)), controlOffset = 2)
    }

    private fun <T> dropdown(label: String, options: List<T>, current: T, display: (T) -> String, change: (T) -> Unit) {
        val widget = StadiumDropdown(geometry.contentLeft, geometry.contentTop + rowCursor + 13, geometry.contentWidth, 22,
            label, options, current, display, { opened -> openDropdown?.close(); openDropdown = opened }, change)
        addRow(label, 40, listOf(widget))
    }

    private fun action(label: String, text: String, action: () -> Unit) = rowButton(label, text) { action() }

    private fun rowButton(label: String, text: String, accent: Boolean = false, action: (StadiumButton) -> Unit) {
        val inline = font.width(label) <= geometry.contentWidth * 44 / 100
        val labelWidth = if (inline) geometry.contentWidth * 44 / 100 + 4 else 0
        val offset = if (inline) 2 else 13
        val button = StadiumButton(geometry.contentLeft + labelWidth, geometry.contentTop + rowCursor + offset,
            geometry.contentWidth - labelWidth, 22, Component.literal(text), accent) { action(it as StadiumButton) }
        button.tooltip = Tooltip.create(Component.literal("$label: $text"))
        addRow(label, offset + 26, listOf(button), controlOffset = offset)
    }

    private fun buttons(label: String, actions: List<Pair<String, () -> Unit>>) {
        val itemWidth = (geometry.contentWidth - (actions.size - 1) * 4) / actions.size
        val offset = if (label.isEmpty()) 2 else 13
        val widgets = actions.mapIndexed { index, (text, action) ->
            StadiumButton(geometry.contentLeft + index * (itemWidth + 4), geometry.contentTop + rowCursor + offset, itemWidth, 22, Component.literal(text)) { action() }
        }
        addRow(label, offset + 26, widgets, controlOffset = offset)
    }

    private fun note(title: String, text: String) = addRow("$title\n$text", 25 + font.split(Component.literal(text), geometry.contentWidth - 16).size * 11, emptyList(), true)

    private fun addRow(label: String, height: Int, widgets: List<AbstractWidget>, note: Boolean = false, controlOffset: Int = 13) {
        rows.add(Row(rowCursor, height, label, widgets, note, controlOffset))
        widgets.forEach { addRenderableWidget(it) }
        rowCursor += height + 3
    }

    private fun positionRows() {
        openDropdown?.close()
        scroll = scroll.coerceIn(0, (rowCursor - geometry.contentHeight).coerceAtLeast(0))
        rows.forEach { row ->
            row.widgets.forEach { widget ->
                widget.y = geometry.contentTop + row.offset + row.controlOffset - scroll
                widget.visible = geometry.visible(widget.y, widget.height)
                if (!widget.visible && widget.isFocused) { widget.isFocused = false; focused = null }
            }
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { return it.popupScroll(verticalAmount) }
        children().filterIsInstance<StadiumFormatLibrary>().firstOrNull { it.isMouseOver(mouseX, mouseY) }?.let {
            return it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        if (mouseX >= geometry.x && mouseX <= geometry.x + geometry.width && mouseY >= geometry.contentTop && mouseY <= geometry.contentBottom) {
            scroll -= (verticalAmount * 22).toInt(); positionRows(); return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { return it.popupClick(mouseX, mouseY, button) }
        if (button == 0 && rowCursor > geometry.contentHeight && mouseX >= geometry.x + geometry.width - 14 &&
            mouseX < geometry.x + geometry.width - 4 && mouseY >= geometry.contentTop && mouseY < geometry.contentBottom) {
            draggingScroll = true
            scrollToMouse(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun scrollToMouse(mouseY: Double) {
        val thumb = (geometry.contentHeight * geometry.contentHeight / rowCursor).coerceAtLeast(10)
        val fraction = ((mouseY - geometry.contentTop - thumb / 2.0) / (geometry.contentHeight - thumb).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        scroll = (fraction * (rowCursor - geometry.contentHeight)).toInt()
        positionRows()
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScroll && button == 0) { scrollToMouse(mouseY); return true }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (draggingScroll && button == 0) { draggingScroll = false; return true }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { if (it.keyPressed(keyCode, scanCode, modifiers)) return true }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll += (geometry.contentHeight - 24).coerceAtLeast(22) * if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) 1 else -1
            positionRows()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        openDropdown?.takeIf { it.isOpen }?.let { return it.charTyped(codePoint, modifiers) }
        return super.charTyped(codePoint, modifiers)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xA0101D28.toInt())
        val (x, y, w, h) = geometry
        StadiumSkin.frame(graphics, x, y, w, h)
        StadiumSkin.emblem(graphics, x + 13, y + 15)
        graphics.drawString(font, I18n.get("cobblemon.stadium.ui.stadium_console"), x + 36, y + 17, StadiumSkin.INK, false)
        val state = if (draft.isConfigured) I18n.get("cobblemon.stadium.ui.enabled") else I18n.get("cobblemon.stadium.ui.draft")
        val stateWidth = font.width(state) + 18
        StadiumSkin.panel(graphics, if (draft.isConfigured) StadiumSkin.Surface.SELECTED else StadiumSkin.Surface.CONTROL,
            x + w - stateWidth - 12, y + 10, stateWidth, 20, 3)
        graphics.drawString(font, state, x + w - stateWidth - 3, y + 16, if (draft.isConfigured) StadiumSkin.TEXT else StadiumSkin.INK, false)
        StadiumSkin.panel(graphics, StadiumSkin.Surface.DISPLAY, geometry.contentLeft - 8, geometry.contentTop - 8,
            geometry.contentWidth + 16, geometry.contentHeight + 16, 5)
        if (geometry.formatEditor && geometry.sidebar) graphics.drawString(font, I18n.get("cobblemon.stadium.ui.your_formats"), x + 14, geometry.contentTop + 2, StadiumSkin.MUTED, false)
        graphics.enableScissor(geometry.contentLeft, geometry.contentTop, geometry.contentLeft + geometry.contentWidth, geometry.contentBottom)
        try {
            rows.forEach { row ->
                val rowY = geometry.contentTop + row.offset - scroll
                if (rowY + row.height >= geometry.contentTop && rowY < geometry.contentBottom) {
                    if (row.note) {
                        graphics.drawString(font, font.plainSubstrByWidth(row.label.substringBefore('\n'), geometry.contentWidth - 16), geometry.contentLeft + 8, rowY + 5, StadiumSkin.INK, false)
                        font.split(Component.literal(row.label.substringAfter('\n')), geometry.contentWidth - 16).forEachIndexed { index, line ->
                            graphics.drawString(font, line, geometry.contentLeft + 8, rowY + 19 + index * 11, StadiumSkin.MUTED, false)
                        }
                    } else if (row.widgets.any { it.visible }) {
                        val labelWidth = if (row.controlOffset == 2 && row.label.isNotEmpty()) geometry.contentWidth * 44 / 100 else geometry.contentWidth
                        graphics.drawString(font, font.plainSubstrByWidth(row.label, labelWidth), geometry.contentLeft, rowY + if (row.controlOffset == 2) 9 else 1, StadiumSkin.INK, false)
                    }
                }
            }
        } finally {
            graphics.disableScissor()
        }
        if (rowCursor > geometry.contentHeight) {
            val trackX = x + w - 11
            val thumb = (geometry.contentHeight * geometry.contentHeight / rowCursor).coerceAtLeast(10)
            val thumbY = geometry.contentTop + (geometry.contentHeight - thumb) * scroll / (rowCursor - geometry.contentHeight)
            StadiumSkin.panel(graphics, StadiumSkin.Surface.CONTROL, trackX, geometry.contentTop, 4, geometry.contentHeight, 1)
            StadiumSkin.panel(graphics, StadiumSkin.Surface.SELECTED, trackX, thumbY, 4, thumb, 1)
        }
        val message = status.ifEmpty {
            when {
                invalidFields.isNotEmpty() -> I18n.get("cobblemon.stadium.ui.correct_the_highlighted_fields_before_saving_2")
                page == Page.MAP -> I18n.get("cobblemon.stadium.ui.blue_cyan_side_1_red_orange")
                draft.toNbt() != original -> I18n.get("cobblemon.stadium.ui.unsaved_changes")
                else -> I18n.get("cobblemon.stadium.ui.changes_apply_when_you_save")
            }
        }
        graphics.drawString(font, font.plainSubstrByWidth(message, w - 24), x + 12, y + h - 42, StadiumSkin.INK, false)
        val menuOpen = openDropdown?.isOpen == true
        super.render(graphics, if (menuOpen) -1 else mouseX, if (menuOpen) -1 else mouseY, partialTick)
        openDropdown?.renderPopup(graphics, mouseX, mouseY)
    }

    private fun save(action: UpdateStadiumBlockPacket.Action = UpdateStadiumBlockPacket.Action.SAVE, wand: RequestStadiumPlacementWandPacket? = null): Boolean {
        if (savingAt != 0L) return false
        val errors = StadiumConfiguration.errors(draft, Vec3.atLowerCornerOf(blockPos)) +
            if (draft.isConfigured) StadiumConfiguration.readiness(draft) else emptyList()
        if (invalidFields.isNotEmpty() || errors.isNotEmpty()) {
            status = if (invalidFields.isNotEmpty()) I18n.get("cobblemon.stadium.ui.correct_the_highlighted_fields_before_saving") else errors.first().string; return false
        }
        try {
            val requestId = UUID.randomUUID()
            CobblemonNetwork.sendToServer(UpdateStadiumBlockPacket(blockPos, draft.toNbt(), action, participantA, participantB, wand, requestId))
            savingRequestId = requestId
        } catch (exception: Exception) {
            status = I18n.get("cobblemon.stadium.ui.save_not_sent")
            return false
        }
        savingAt = System.currentTimeMillis()
        status = I18n.get("cobblemon.stadium.ui.saving")
        children().filterIsInstance<AbstractWidget>().forEach { it.active = false }
        return true
    }

    fun acceptResult(packet: StadiumConfigResultPacket) {
        if (packet.blockPos != blockPos || packet.requestId != savingRequestId) return
        savingAt = 0
        savingRequestId = null
        status = packet.message.string
        if (packet.success) {
            closing = true; onClose()
            if (editAfterSave) EditorStadiumBridge.open(blockPos, draft.stadiumId)
        } else { editAfterSave = false; init() }
    }

    override fun onClose() {
        if (savingAt != 0L) return
        if (!closing && (draft.toNbt() != original || invalidFields.isNotEmpty())) {
            minecraft?.setScreen(ConfirmScreen({ discard ->
                if (discard) minecraft?.setScreen(null) else minecraft?.setScreen(this)
            }, Component.translatable("cobblemon.stadium.ui.discard_stadium_changes"), Component.translatable("cobblemon.stadium.ui.unsaved_configuration_changes_will_be_lost")))
        } else super.onClose()
    }

    override fun isPauseScreen() = false
    override fun removed() {
        terrainMap?.refreshTerrain()
        super.removed()
    }
    private fun number(value: Double) = "%.2f".format(Locale.ROOT, value)

    override fun tick() {
        super.tick()
        if (savingAt != 0L && System.currentTimeMillis() - savingAt > 15000) {
            savingAt = 0
            savingRequestId = null
            editAfterSave = false
            status = I18n.get("cobblemon.stadium.ui.no_server_reply_your_draft_is")
            init()
        }
    }
}
