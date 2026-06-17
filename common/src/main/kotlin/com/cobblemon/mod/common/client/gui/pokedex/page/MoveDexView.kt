/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonResources
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.MOVE_ICON_SIZE
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesAccuracyIconResource
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesEffectIconResource
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget.Companion.movesPowerIconResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.Lighting
import com.mojang.math.Axis
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * The MoveDex: a comprehensive, learnset-independent move browser. The left side lists every move the
 * player has discovered (undiscovered ones show as "???", like the per-Pokémon move list) with filter
 * and sort controls mirroring that list's. The right side has a battle-style viewport (a substitute
 * doll "using" the move on another), play/pause controls, and tabs:
 *  - Info: the move's type/category/power/accuracy/PP/effect/description, like the Pokédex move info.
 *  - Learnset: every species that can learn the move, by method (level/TM/egg/tutor); "???" for species
 *    the player hasn't seen.
 *
 * NOTE: faithful in-battle move particles/animations are a server-driven subsystem; this first pass
 * renders the dolls and play/pause scaffolding, with the action-effect particle playback to follow.
 */
class MoveDexView {
    companion object {
        private const val LIST_X = 18
        private const val LIST_W = 142
        private const val SEARCH_Y = 26
        private const val SEARCH_H = 10
        // Filter + sort share one Pokédex-style "category bar" row; the move list mirrors the Pokédex move
        // slots (move_slot.png), 13px tall, tightly stacked so the MoveDex shows a long list of all moves.
        private const val CONTROLS_Y = 38
        private const val CONTROLS_H = 10
        private const val LIST_TOP = 50
        private const val ROW_H = 13
        private const val VISIBLE_ROWS = 10
        private const val SCALE = 0.5F                  // matches PokedexGUIConstants.SCALE for icon/button blits
        private const val CATEGORY_BAR_W = 78           // filter portion width; sort buttons sit to its right (5 now)
        private const val SORT_BTN_SIZE = 20            // sort sheet is 20x40; rendered at SORT_BTN_SIZE*SCALE
        private const val SORT_BTN_SPACING = 12

        private const val DETAIL_X = 166
        private const val DETAIL_W = 160
        private const val HEADER_Y = 24
        private const val HEADER_H = 12
        private const val SPECIES_Y = 37
        private const val VIEW_Y = 50
        private const val VIEW_H = 74
        private const val CONTENT_TOP = 126      // content begins just below the viewport (VIEW_Y+VIEW_H=124)
        private const val INFO_PANEL_Y = 148     // info_move_background panel top (133x38) — lowered 20px per request
        private const val SUBTAB_Y = 128         // learnset method sub-filter buttons (near content top)
        private const val SUBTAB_H = 10
        private const val CONTENT_BOTTOM = 190   // content ends above the bottom tab row
        private const val TAB_ROW_Y = 196        // tabs follow the lowered panel; icons 196-204 (BASE_HEIGHT=207)
        private const val SPECIES_H = 11

        private val SUBSTITUTE = cobblemonResource("substitute")
        private val STADIUM_FLOOR = cobblemonResource("textures/gui/pokedex/movedex_stadium_floor.png")
        // Default actor (the Pokémon performing the move): Smeargle — a real species — rather than the substitute doll.
        private val DEFAULT_ACTOR = cobblemonResource("smeargle")
        // The move's target is a substitute doll (a battle defender stand-in), so it can play the move's real
        // `target` bedrock animation (throw/squash/recoil) exactly like a real defender.
        private val TARGET = SUBSTITUTE
        // Render scale + approximate hitbox dims (blocks) of the substitute target, used for aiming and the floor.
        // Matched to the substitute geo: its `top` locator sits ~16.5px (~1 block) up at scale 1.0, so it's ~1
        // block tall and ~0.9 wide. (Effects emit from its real geo locators now; these only drive the aim/floor.)
        private const val TARGET_SCALE = 1.0f
        private const val TARGET_HEIGHT = 1.0f
        private const val TARGET_WIDTH = 0.9f
        // Texture-V of each stand pad measured from the floor's centre (the PNG's A/B pads sit at V 0.75/0.25).
        // The floor's Z half-extent is derived from this so the pads always land exactly under the two dolls,
        // however far apart the per-move separation pushes them.
        private const val FLOOR_PAD_V = 0.25f

        // Info tabs reuse the MAIN Pokédex's tab-row look exactly: 16x32 two-state icon sheets (top half = idle,
        // bottom half = hover/active) drawn at half scale (8px on screen), with the select arrow over the active
        // tab. Same textures, same scale, same vOffset trick as PokedexGUI's ScaledButton tab row.
        private val TAB_INFO_ICON = cobblemonResource("textures/gui/pokedex/tab_info.png")
        private val TAB_LEARNSET_ICON = cobblemonResource("textures/gui/pokedex/tab_moves.png")
        private val TAB_SELECT_ARROW = cobblemonResource("textures/gui/pokedex/select_arrow.png")
        private const val TAB_ICON_NATIVE = 16   // texture is 16 wide x 32 tall (two stacked 16px states)
        private const val TAB_ICON_SCALE = 0.5f  // displayed 8x8 — identical to the main Pokédex tabs
        private const val TAB_ICON_DISP = 8       // TAB_ICON_NATIVE * TAB_ICON_SCALE (on-screen px)
        private const val TAB_ICON_GAP = 22       // same inter-tab spacing as the main Pokédex
        private const val TAB_ICON_X0 = 2         // left indent of the first tab icon within the detail panel

        // Move-info panel reuses the MAIN Pokédex's per-Pokémon move panel assets verbatim (info_move_background
        // border + the shared power/accuracy/effect icons), so the MoveDex's Move Information tab matches it.
        private val MOVE_INFO_BACKGROUND = cobblemonResource("textures/gui/pokedex/info_move_background.png")
        // Custom right-panel background skin for the MoveDex detail side.
        private val INFO_BG = cobblemonResource("textures/gui/pokedex/movedex_info_background.png")
        private const val INFO_SCALE = 0.5f       // shared Pokédex move-panel text/icon scale
        private const val INFO_TEXT_COLOUR = 0x606B6E // dark slate, as in the main Pokédex move panel

        // Pokédex move-list assets, reused so the MoveDex list matches the Pokédex info-tab move list.
        private val MOVE_SLOT = cobblemonResource("textures/gui/pokedex/move_slot.png")
        private val MOVE_SLOT_SELECT = cobblemonResource("textures/gui/pokedex/move_slot_select.png")
        private val MOVE_CATEGORY_BAR = cobblemonResource("textures/gui/pokedex/move_category_bar.png")
        private val FILTER_ICON = cobblemonResource("textures/gui/pokedex/category_icon.png")
        private const val ROW_TEXT_H = 9

        private const val PANEL_BG = 0xCC10303A.toInt()
        private const val PLATE = 0xFFE9F6F6.toInt()
        private const val PLATE_HOVER = 0xFFC8E8E8.toInt()
        private const val PLATE_SELECTED = 0xFFA9DCDC.toInt()
        private const val PREVIEW_BG = 0xC0101010.toInt()
        private const val TEXT_DARK = 0x33586B
        private const val TEXT_UNKNOWN = 0x8899A0

        // Isometric battle scene tuning. Scale is auto-fit per move; only the view angle + gap are constants.
        private const val SCENE_PITCH = -16f
        private const val SCENE_YAW = -42f
        private const val SCENE_SEPARATION = 2.5   // base half-gap (blocks) along the forward (Z) axis
        private const val SEP_BASE = 0.8           // separation = SEP_BASE + (actorWidth+targetWidth)*SEP_FACTOR
        private const val SEP_FACTOR = 0.85        // so bigger Pokémon (Wailord) get pushed further apart
        private const val DEFAULT_ZOOM = 26f       // scale (px per scene block); scroll adjusts, recentre keeps it framed

        private fun fallback(key: String, text: String) = Component.translatableWithFallback("cobblemon.ui.movedex.$key", text)
    }

    private enum class Filter(val label: String) { ALL("All"), PHYSICAL("Physical"), SPECIAL("Special"), STATUS("Status") }
    // `tex` is the button_sort_move_<tex>.png suffix; POWER reuses the numeric "level" icon, IMPLEMENTED is the
    // checkmark button (moves with a built action effect first).
    private enum class Sort(val tex: String) { NAME("name"), TYPE("type"), POWER("level"), DISCOVERED("discovered"), IMPLEMENTED("implemented") }
    private enum class Tab(val key: String, val label: String) { INFO("tab.info", "Info"), LEARNSET("tab.learnset", "Learnset") }

    private var filter = Filter.ALL
    private var sort = Sort.NAME
    private var searchQuery = ""
    private var searchFocused = false
    private var scrollRow = 0
    private var selected: MoveTemplate? = null
    private var activeTab = Tab.INFO
    private var contentScroll = 0
    private var learnMethodFilter: MoveDexData.LearnMethod? = null
    private var playing = false
    private var animTime = 0f          // seconds elapsed in the current move playthrough
    private var animDuration = 2f      // the move's action-effect cycle length in seconds
    private var lastMs = 0L            // last wall-clock millis, for true-speed tick advancement
    private var tickAccumulatorMs = 0f // leftover ms toward the next 20 Hz tick
    private var actorState = FloatingState()
    private val targetState = FloatingState()
    private val scene = MoveDexParticleScene()
    // The Pokémon performing the move — fixed to Smeargle (the actor can't be changed from the MoveDex).
    private var actorSpecies: ResourceLocation? = DEFAULT_ACTOR
    // Timed bedrock animation cues (names, time, isTarget): actor windup + target damage/recoil.
    private var animEvents: List<Triple<Set<String>, Float, Boolean>> = emptyList()
    private var nextAnimIndex = 0
    private val pendingAnims = ArrayList<Pair<Set<String>, Boolean>>() // due this frame; played after models render
    private var moveSounds: List<Pair<String, Float>> = emptyList() // (soundId, timeSeconds), sorted by time
    private var nextSoundIndex = 0                        // next not-yet-played sound in the cycle
    private var muted = false                             // speaker toggle
    private var debugLocators = false                     // draw locator/target-spot markers (toggle: L key or button)
    // Follow-camera state: when a move flings a doll upward (Seismic Toss), the camera pans up and eases the zoom
    // out just enough to keep the action framed, then settles back — all smoothed so normal moves look static.
    private var dollRiseY = 0f                            // last frame's max doll vertical rise (scene units)
    private var followZoomFactor = 1f                     // smoothed multiplicative zoom-out applied to sceneZoom
    private var followPanY = 0f                           // smoothed vertical pan offset (scene units, up = -Y)
    // Live scene camera: drag rotates (yaw/pitch); scroll sets the single zoom (= scale). Centering is auto.
    private var sceneYaw = SCENE_YAW
    private var scenePitch = SCENE_PITCH
    private var sceneZoom = DEFAULT_ZOOM
    private var sceneSeparation = SCENE_SEPARATION // half the user<->target gap; scales with the dolls' size
    private var sceneDragArmed = false // true only while a press began inside the viewport (gates drag-rotate)
    // Auto-centering: the scene-local centroid of dolls + the move's full particle volume (measured once per
    // move), so the camera pans to keep everything centred regardless of zoom/rotation.
    private val fitBox = FloatArray(6)
    private val fixedPivot = FloatArray(3)

    fun open() {
        MoveDexData.refreshDiscovered()
        reset()
    }

    fun reset() {
        filter = Filter.ALL
        sort = Sort.NAME
        searchQuery = ""
        searchFocused = false
        scrollRow = 0
        selected = null
        activeTab = Tab.INFO
        contentScroll = 0
        learnMethodFilter = null
        playing = false
        actorSpecies = DEFAULT_ACTOR
        // Always start at the default camera angle + zoom; move/species changes never touch them (only drag/scroll).
        sceneYaw = SCENE_YAW
        scenePitch = SCENE_PITCH
        sceneZoom = DEFAULT_ZOOM
    }

    /** The model identifier for the actor doll — the chosen species, or the Smeargle default. */
    private fun actorIdentifier(): ResourceLocation = actorSpecies ?: DEFAULT_ACTOR

    /** (height, width) hitbox of the chosen actor species in world blocks; substitute-ish default otherwise. */
    private fun actorHW(): Pair<Float, Float> {
        val s = actorSpecies?.let { PokemonSpecies.getByIdentifier(it) } ?: return 2.0f to 1.0f
        return s.hitbox.height to s.hitbox.width
    }

    /** (height, width) of the substitute target doll in world blocks, used for aiming and floor placement. */
    private fun targetHW(): Pair<Float, Float> = TARGET_HEIGHT to TARGET_WIDTH

    /** World render scale of the substitute target doll. */
    private fun targetScale(): Float = TARGET_SCALE

    /** Builds the particle scene for [move] with the current dolls' hitbox dims (used for locator placement). */
    private fun buildScene(move: MoveTemplate) {
        val (ah, aw) = actorHW(); val (th, tw) = targetHW()
        scene.build(move, (sceneSeparation * 2).toFloat(), ah, aw, th, tw)
    }

    /**
     * Builds the move's particle scene and measures the CENTROID of (dolls + the move's full particle volume),
     * pre-simulating the whole effect once. That centroid is what the camera pans to so everything stays nicely
     * centred at any zoom/rotation. Scale itself is the single manual [sceneZoom]. Leaves the scene freshly built.
     */
    /**
     * Kills everything in flight on both dolls before a fresh move starts/loops: any active + primary bedrock
     * animations, and any queued-but-unplayed animation cues. The particle scene's own [MoveDexParticleScene.clear]
     * (run by [buildScene]) kills the active emitters and particles. Together this guarantees a new move/cycle
     * never inherits a half-played animation or leftover storm from the previous one.
     */
    private fun resetDolls() {
        actorState.activeAnimations.clear(); actorState.primaryAnimation = null
        targetState.activeAnimations.clear(); targetState.primaryAnimation = null
        pendingAnims.clear()
    }

    private fun prepareMove(move: MoveTemplate) {
        resetDolls()
        val (ah, aw) = actorHW(); val (th, tw) = targetHW()
        // Push bigger Pokémon further apart so a massive mon (Wailord) doesn't overlap the target and the move's
        // particles have room to read. Computed BEFORE the auto-fit, which then zooms to fit the wider scene.
        sceneSeparation = SEP_BASE + (aw + tw) * SEP_FACTOR
        val sep = sceneSeparation.toFloat()
        // Seed with the two dolls' bounds. Dolls render in the same "up = -Y" frame as particles (feet at 0,
        // head toward -Y), so the box goes [-height, small] in Y — not symmetric.
        fitBox[0] = -max(aw, tw); fitBox[1] = -max(ah, th); fitBox[2] = -(sep + max(aw, tw) * 0.5f)
        fitBox[3] = max(aw, tw); fitBox[4] = 0.3f; fitBox[5] = sep + max(aw, tw) * 0.5f

        buildScene(move)
        scene.measureBounds(fitBox, (animDuration * 20f).toInt() + 40, sep, -sep)
        buildScene(move) // rebuild fresh for actual playback (measurement consumed it)

        fixedPivot[0] = (fitBox[0] + fitBox[3]) * 0.5f
        fixedPivot[1] = (fitBox[1] + fitBox[4]) * 0.5f
        fixedPivot[2] = (fitBox[2] + fitBox[5]) * 0.5f

        // Reset the follow-camera so the new move starts settled (it eases out/up only if this move flings a doll).
        dollRiseY = 0f; followZoomFactor = 1f; followPanY = 0f

        // Auto-fit the zoom to this move: project the 8 box corners through the view rotation and size so the
        // projected extent fits BOTH viewport dimensions. This is the single sceneZoom value (scroll overrides
        // it); it's what lets a tiny move (Charmander) and a tall one (Thunder) each fill the window sensibly.
        val r = Quaternionf().mul(Axis.XP.rotationDegrees(scenePitch)).mul(Axis.YP.rotationDegrees(sceneYaw))
        var minSX = Float.MAX_VALUE; var maxSX = -Float.MAX_VALUE
        var minSY = Float.MAX_VALUE; var maxSY = -Float.MAX_VALUE
        for (i in 0 until 8) {
            val v = Vector3f(
                if (i and 1 == 0) fitBox[0] else fitBox[3],
                if (i and 2 == 0) fitBox[1] else fitBox[4],
                if (i and 4 == 0) fitBox[2] else fitBox[5]
            )
            r.transform(v)
            if (v.x < minSX) minSX = v.x; if (v.x > maxSX) maxSX = v.x
            if (v.y < minSY) minSY = v.y; if (v.y > maxSY) maxSY = v.y
        }
        val projW = max(0.5f, maxSX - minSX)
        val projH = max(0.5f, maxSY - minSY)
        sceneZoom = min((DETAIL_W - 14f) / projW, (VIEW_H - 12f) / projH).coerceIn(4f, 80f)
        // DIAGNOSTIC (temporary): the measured fit box, pivot and resulting zoom. A wildly large box or off-centre
        // pivot here = the scene zooms to nothing / pans off-screen, which reads as "the whole viewport is blank".
        Cobblemon.LOGGER.info("MoveDex fit {}: box=[{}, {}, {} .. {}, {}, {}] pivot=[{}, {}, {}] projW={} projH={} zoom={}",
            move.name, fitBox[0], fitBox[1], fitBox[2], fitBox[3], fitBox[4], fitBox[5],
            fixedPivot[0], fixedPivot[1], fixedPivot[2], projW, projH, sceneZoom)
    }

    private fun moves(): List<MoveTemplate> {
        val filtered = Moves.all().filter { move ->
            (when (filter) {
                Filter.ALL -> true
                Filter.PHYSICAL -> move.damageCategory.name.equals("physical", true)
                Filter.SPECIAL -> move.damageCategory.name.equals("special", true)
                Filter.STATUS -> move.damageCategory.name.equals("status", true)
            }) && matchesSearch(move)
        }
        return when (sort) {
            Sort.NAME -> filtered.sortedBy { it.displayName.string }
            Sort.TYPE -> filtered.sortedWith(compareBy({ it.elementalType.name }, { it.displayName.string }))
            Sort.POWER -> filtered.sortedWith(compareByDescending<MoveTemplate> { it.power }.thenBy { it.displayName.string })
            Sort.DISCOVERED -> filtered.sortedWith(compareByDescending<MoveTemplate> { MoveDexData.isDiscovered(it) }.thenBy { it.displayName.string })
            Sort.IMPLEMENTED -> filtered.sortedWith(compareByDescending<MoveTemplate> { MoveDexAnimation.hasActionEffect(it) }.thenBy { it.displayName.string })
        }
    }

    /**
     * Keyword search across a move's name, elemental type, and damage category (e.g. "fire", "physical").
     * Undiscovered moves are excluded while searching, since their identity is hidden ("???").
     */
    private fun matchesSearch(move: MoveTemplate): Boolean {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return true
        if (!MoveDexData.isDiscovered(move)) return false
        return move.displayName.string.lowercase().contains(query) ||
            move.name.lowercase().contains(query) ||
            move.elementalType.name.lowercase().contains(query) ||
            move.damageCategory.name.lowercase().contains(query)
    }

    // ---- rendering ----

    fun render(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        renderSearchBar(context, x, y, mouseX, mouseY)
        renderControls(context, x, y, mouseX, mouseY)
        renderList(context, x, y, mouseX, mouseY)
        renderDetail(context, x, y, mouseX, mouseY, delta)
        renderMoveCount(context, x, y)
    }

    /** [discovered] / [total] moves, top-right — mirrors the main Pokédex's caught/total counter. */
    private fun renderMoveCount(context: GuiGraphics, x: Int, y: Int) {
        val text = Component.literal("${MoveDexData.discoveredCount()} / ${MoveDexData.totalCount()}").bold()
        drawScaledTextJustifiedRight(context = context, font = CobblemonResources.DEFAULT_LARGE, text = text, x = x + DETAIL_X + DETAIL_W - 4, y = y + 14, colour = INFO_TEXT_COLOUR)
    }

    private fun renderSearchBar(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val left = x + LIST_X
        val top = y + SEARCH_Y
        val hovered = mouseX in left..(left + LIST_W) && mouseY in top..(top + SEARCH_H)
        context.fill(left, top, left + LIST_W, top + SEARCH_H, if (searchFocused || hovered) PLATE_HOVER else PLATE)
        val text: MutableComponent = if (searchQuery.isEmpty() && !searchFocused) fallback("search", "Search")
            else Component.literal(searchQuery + if (searchFocused) "_" else "")
        drawScaledText(context = context, text = text, x = left + 3, y = top + 2, colour = TEXT_DARK, scale = 0.65F)
    }

    /** Click area of the filter "category bar" (clicking it cycles the damage-category filter). */
    private fun filterBarRect(x: Int, y: Int): IntArray = intArrayOf(x + LIST_X, y + CONTROLS_Y, CATEGORY_BAR_W, CONTROLS_H)

    /** Top-left (px) of each rendered 10x10 sort button, one per Sort entry, to the right of the filter bar. */
    private fun sortButtonRects(x: Int, y: Int): List<Pair<Int, Int>> {
        val base = x + LIST_X + CATEGORY_BAR_W + 2
        return Sort.entries.mapIndexed { i, _ -> (base + i * SORT_BTN_SPACING) to (y + CONTROLS_Y) }
    }

    private fun renderControls(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val left = x + LIST_X
        val top = y + CONTROLS_Y
        val pose = context.pose()

        // Filter "category bar" with the category icon + current filter label (click anywhere on it to cycle).
        blitk(matrixStack = pose, texture = MOVE_CATEGORY_BAR, x = left, y = top, width = CATEGORY_BAR_W, height = CONTROLS_H)
        blitk(matrixStack = pose, texture = FILTER_ICON, x = (left + 3) / SCALE, y = (top + 1.5f) / SCALE, width = 14, height = 14, scale = SCALE)
        drawScaledText(context = context, font = CobblemonResources.DEFAULT_LARGE, text = Component.literal(filter.label).bold(), x = left + 13, y = top + 1, shadow = true)

        // Sort buttons reuse the Pokédex two-state sheets (button_sort_move_<tex>.png; top = idle, bottom = active).
        val scaledSize = (SORT_BTN_SIZE * SCALE).toInt()
        sortButtonRects(x, y).forEachIndexed { i, (bx, by) ->
            val entry = Sort.entries[i]
            val active = entry == sort || (mouseX in bx..(bx + scaledSize) && mouseY in by..(by + scaledSize))
            blitk(
                matrixStack = pose,
                texture = cobblemonResource("textures/gui/pokedex/button_sort_move_${entry.tex}.png"),
                x = bx / SCALE,
                y = by / SCALE,
                width = SORT_BTN_SIZE,
                height = SORT_BTN_SIZE,
                vOffset = if (active) SORT_BTN_SIZE else 0,
                textureHeight = SORT_BTN_SIZE * 2,
                scale = SCALE
            )
        }
    }

    private fun renderList(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val list = moves()
        val maxScroll = max(0, list.size - VISIBLE_ROWS)
        if (scrollRow > maxScroll) scrollRow = maxScroll
        val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(list.size)
        val pose = context.pose()
        val left = x + LIST_X
        for (i in scrollRow until end) {
            val move = list[i]
            val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
            val discovered = MoveDexData.isDiscovered(move)
            val selectedRow = move === selected
            val hovered = mouseX in left..(left + LIST_W) && mouseY in rowY..(rowY + 12)

            // Pokédex move slot art + the select/hover overlay (top half = selected, bottom half = hover).
            blitk(matrixStack = pose, texture = MOVE_SLOT, x = left, y = rowY, width = LIST_W, height = 13)
            if (selectedRow || hovered) {
                blitk(
                    matrixStack = pose,
                    texture = MOVE_SLOT_SELECT,
                    x = left, y = rowY, width = LIST_W, height = 13,
                    textureHeight = 26, vOffset = if (hovered && !selectedRow) 13 else 0
                )
            }

            // Moves we haven't built an action effect for yet get a red ✕ at the FAR right (so it's obvious which
            // still need a particle animation, e.g. Dig). The power number stays, shifted left to make room.
            val noEffect = !MoveDexAnimation.hasActionEffect(move)
            val powerRightX = left + LIST_W - (if (noEffect) 11 else 4)
            if (discovered) {
                TypeIcon(x = left + 3, y = rowY + 2, type = move.elementalType, small = true).render(context)
                drawScaledText(context = context, font = CobblemonResources.DEFAULT_LARGE, text = move.displayName.copy().bold(), x = left + 14, y = rowY + 2, shadow = true)
                if (move.power > 0) drawScaledTextJustifiedRight(context = context, font = CobblemonResources.DEFAULT_LARGE, text = Component.literal(move.power.toInt().toString()).bold(), x = powerRightX, y = rowY + 2, shadow = true)
            } else {
                drawScaledText(context = context, font = CobblemonResources.DEFAULT_LARGE, text = Component.literal("???").bold(), x = left + 14, y = rowY + 2, shadow = true)
            }
            if (noEffect) drawScaledText(context = context, font = CobblemonResources.DEFAULT_LARGE, text = Component.literal("✕").bold(), x = left + LIST_W - 8, y = rowY + 2, colour = 0xFF5555, shadow = true)
        }
    }

    private fun tabIcon(tab: Tab) = when (tab) {
        Tab.INFO -> TAB_INFO_ICON
        Tab.LEARNSET -> TAB_LEARNSET_ICON
    }

    /** Screen X of tab [index]'s 8x8 icon within the detail panel anchored at [left]. */
    private fun tabIconX(left: Int, index: Int) = left + TAB_ICON_X0 + index * TAB_ICON_GAP

    private fun renderDetail(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val move = selected ?: return
        val left = x + DETAIL_X

        // Custom right-panel background skin (header bar + tab-area bars). Drawn first, behind all content.
        blitk(matrixStack = context.pose(), texture = INFO_BG, x = left, y = y + HEADER_Y, width = DETAIL_W, height = TAB_ROW_Y + TAB_ICON_DISP + 2 - HEADER_Y)
        // Move name — styled like the main Pokédex species name (DEFAULT_LARGE, dark, not type-coloured).
        drawScaledText(context = context, font = CobblemonResources.DEFAULT_LARGE, text = move.displayName.copy().bold(), x = left + 4, y = y + HEADER_Y + 3, colour = INFO_TEXT_COLOUR, scale = 0.75F)
        // Bigger elemental-type symbol for the selected move, on the right side of the header.
        TypeIcon(x = left + DETAIL_W - 21, y = y + HEADER_Y - 2, type = move.elementalType, small = false).render(context)

        // Battle-style viewport — no dark backdrop; the scene renders straight on the panel background texture.
        renderDolls(context, left, y, delta)
        renderPlayPause(context, left, y, mouseX, mouseY)
        renderMuteButton(context, left, y, mouseX, mouseY)
        renderLocatorButton(context, left, y, mouseX, mouseY)

        // Info tabs — drawn exactly like the main Pokédex's tab row (16x32 two-state icon at half scale, bottom
        // half on hover/active, with the select arrow above the active tab).
        Tab.entries.forEachIndexed { index, tab ->
            val ix = tabIconX(left, index)
            val active = tab == activeTab
            val hovered = mouseX in ix..(ix + TAB_ICON_DISP) && mouseY in (y + TAB_ROW_Y)..(y + TAB_ROW_Y + TAB_ICON_DISP)
            blitk(
                matrixStack = context.pose(),
                texture = tabIcon(tab),
                x = ix / TAB_ICON_SCALE,
                y = (y + TAB_ROW_Y) / TAB_ICON_SCALE,
                width = TAB_ICON_NATIVE,
                height = TAB_ICON_NATIVE,
                vOffset = if (hovered || active) TAB_ICON_NATIVE else 0,
                textureHeight = TAB_ICON_NATIVE * 2,
                scale = TAB_ICON_SCALE
            )
            if (active) {
                // 12x6 arrow (shown 6x3) centred over the 8px icon, just above it — same offset as the Pokédex.
                blitk(
                    matrixStack = context.pose(),
                    texture = TAB_SELECT_ARROW,
                    x = (ix + 1) / TAB_ICON_SCALE,
                    y = (y + TAB_ROW_Y - 4) / TAB_ICON_SCALE,
                    width = 12,
                    height = 6,
                    scale = TAB_ICON_SCALE
                )
            }
        }

        when (activeTab) {
            Tab.INFO -> renderInfo(context, left, y, move)
            Tab.LEARNSET -> renderLearnset(context, left, y, move, mouseX, mouseY)
        }
    }

    private fun renderDolls(context: GuiGraphics, left: Int, y: Int, delta: Float) {
        // Advance by REAL wall-clock time at a fixed 20 Hz (50 ms/tick), independent of any screen pause or
        // game-tick throttling, so the effect plays at exact battle speed. `delta` (the screen's partial-tick)
        // is ignored for timing; the leftover-ms fraction drives interpolation instead.
        val nowMs = Util.getMillis()
        if (lastMs == 0L) lastMs = nowMs
        val elapsedMs = (nowMs - lastMs).coerceIn(0L, 250L)
        lastMs = nowMs

        var steps = 0
        if (playing) {
            tickAccumulatorMs += elapsedMs.toFloat()
            while (tickAccumulatorMs >= 50f && steps < 10) {
                tickAccumulatorMs -= 50f
                steps++
            }
            animTime += steps / 20f
            // Play the move's sound cues as their timeline times arrive (advance the index even when muted so
            // unmuting mid-cycle doesn't replay past sounds).
            while (nextSoundIndex < moveSounds.size && moveSounds[nextSoundIndex].second <= animTime) {
                if (!muted) playMoveSound(moveSounds[nextSoundIndex].first)
                nextSoundIndex++
            }
            // Queue any animation cues now due (actor windup / target hit-recoil); played after the models render
            // (addFirstAnimation needs currentModel set).
            while (nextAnimIndex < animEvents.size && animEvents[nextAnimIndex].second <= animTime) {
                val ev = animEvents[nextAnimIndex]
                pendingAnims.add(ev.first to ev.third)
                nextAnimIndex++
            }
            // Loop only once EVERYTHING has played — particles gone (scene emptied), every animation/sound cue
            // fired, AND both dolls' bedrock animations have finished — so a move with trailing cues or a long
            // doll animation after its last particle (common on longer moves) isn't cut off and restarted early.
            // This skips only the empty trailing battle pauses. animDuration is the hard fallback cap.
            val cuesRemain = nextAnimIndex < animEvents.size || nextSoundIndex < moveSounds.size
            val dollAnimating = actorState.primaryAnimation != null || actorState.activeAnimations.isNotEmpty() ||
                targetState.primaryAnimation != null || targetState.activeAnimations.isNotEmpty()
            if (animTime >= animDuration || (animTime > 0.5f && scene.isEmpty && !cuesRemain && !dollAnimating)) {
                animTime = 0f
                tickAccumulatorMs = 0f
                nextSoundIndex = 0
                nextAnimIndex = 0
                resetDolls() // clear leftover animations/cues so the next cycle starts clean
                selected?.let { buildScene(it) } // rebuild kills the previous cycle's emitters/particles
            }
        }
        val partial = (tickAccumulatorMs / 50f).coerceIn(0f, 1f)
        // Give the scene the current dolls so it can re-aim moves from the real emit locators toward the
        // opposing doll's centre (the "target spot"); the substitute target has no locators so its centre
        // (mid-height) is the defined aim point.
        val aScale = actorSpecies?.let { PokemonSpecies.getByIdentifier(it)?.baseScale } ?: 1.0f
        val (ahh, _) = actorHW()
        // Aim at the substitute target's centre: mid-height, and the near (+Z) face — half its width toward the
        // attacker from the doll's centre at -separation.
        val targetCentreY = TARGET_HEIGHT * 0.5f
        val targetFaceZ = -sceneSeparation.toFloat() + TARGET_WIDTH * 0.5f
        scene.setAimContext(
            actorState, targetState, aScale, 1.0f,
            sceneSeparation.toFloat(), -sceneSeparation.toFloat(), ahh * 0.5f, targetCentreY, targetFaceZ
        )
        // Guard the particle sim: a misbehaving effect must never throw out of here, or it would skip the ENTIRE
        // viewport render below (floor + dolls + particles) and blank everything — that's what made Swords Dance
        // show nothing at all. Logged so the offending move's actual error is visible.
        runCatching { scene.update(steps, partial) }.onFailure { Cobblemon.LOGGER.warn("MoveDex scene update failed", it) }

        // Advance the doll's bedrock animation by the SAME wall-clock delta (in ticks) the particle scene uses,
        // so the model animation and the particle timeline run on one 20 Hz clock and stay in sync (the particles
        // fire exactly when the animation reaches that point). FloatingState.updatePartialTicks ACCUMULATES, so
        // this must be a per-frame delta, not the in-tick fraction. Both reset together on swap/loop.
        val advance = if (playing) (elapsedMs.toFloat() / 50f) else 0f

        context.enableScissor(left, y + VIEW_Y, left + DETAIL_W, y + VIEW_Y + VIEW_H)

        // Follow-camera: ease the pivot up and the zoom out by just enough to keep a doll thrown upward (Seismic
        // Toss launches the target ~15 blocks up) in frame, then settle back as it returns. Uses LAST frame's
        // measured rise; manual zoom (sceneZoom) and rotation stay the base this multiplies/offsets. Smoothed so
        // resting moves (rise≈0) are unaffected, satisfying "static for normal moves, follow the big ones".
        val baseExtentY = max(1f, fitBox[4] - fitBox[1])
        val rise = if (dollRiseY.isFinite()) dollRiseY.coerceIn(0f, 40f) else 0f
        val desiredFactor = (baseExtentY / (baseExtentY + rise)).coerceIn(0.2f, 1f)
        val desiredPanY = -rise / 2f
        val ease = if (playing) 0.18f else 0.5f
        // Recover if a prior frame's non-finite rise already poisoned these (NaN is sticky through +=).
        if (!followZoomFactor.isFinite()) followZoomFactor = 1f
        if (!followPanY.isFinite()) followPanY = 0f
        followZoomFactor += (desiredFactor - followZoomFactor) * ease
        followPanY += (desiredPanY - followPanY) * ease

        val finalScale = (sceneZoom * followZoomFactor).let { if (it.isFinite() && it > 0f) it else sceneZoom }
        // Pan so the (rotated) content pivot (raised by the follow offset) lands at the viewport centre.
        val pv = Vector3f(fixedPivot[0], fixedPivot[1] + followPanY, fixedPivot[2])
        Quaternionf().mul(Axis.XP.rotationDegrees(scenePitch)).mul(Axis.YP.rotationDegrees(sceneYaw)).transform(pv)
        val cx = left + DETAIL_W / 2.0
        val cy = y + VIEW_Y + VIEW_H / 2.0
        // A non-finite pivot (from a degenerate fit box) would make pose.translate NaN/Inf and blank the ENTIRE
        // viewport (floor + dolls included). Fall back to no pan so the scene still renders centred.
        val pvx = if (pv.x.isFinite()) pv.x else 0f
        val pvy = if (pv.y.isFinite()) pv.y else 0f

        val pose = context.pose()
        pose.pushPose()
        pose.translate(cx - finalScale * pvx, cy - finalScale * pvy, 200.0)
        pose.scale(finalScale, finalScale, finalScale)
        pose.mulPose(Axis.XP.rotationDegrees(scenePitch))
        pose.mulPose(Axis.YP.rotationDegrees(sceneYaw))

        // Render both dolls at their TRUE world scale (baseScale) so model size matches the particle world
        // scale, exactly like battle. The auto-fit camera handles fitting everything in the window.
        val actorScale = actorSpecies?.let { PokemonSpecies.getByIdentifier(it)?.baseScale } ?: 1.0f
        Lighting.setupForEntityInInventory()
        runCatching {
            // Stadium floor at the dolls' feet (y=0). Its Z half-extent is derived from the per-move separation so
            // the texture's A/B pads (at V ±FLOOR_PAD_V from centre) land EXACTLY under each doll however far the
            // differing hitbox volumes push them apart: pad z = ±FLOOR_PAD_V*(2*halfZ) must equal ±separation, so
            // halfZ = separation / (2*FLOOR_PAD_V). This is also the plane the scene collides ground particles
            // (Bulldoze) against, so they visibly come to rest on it.
            // The floor is a SQUARE: halfX == halfZ, so the quad stays symmetric about (0,0) (centred) and BOTH the
            // X and Z extents grow together with the separation — i.e. bigger-hitbox mons (which push the dolls
            // further apart in Z) enlarge the whole square uniformly while the dolls stay pinned to the same pads,
            // so they read as being in the same relative spots regardless of size.
            val floorHalf = (sceneSeparation / (2.0 * FLOOR_PAD_V)).toFloat()
            MoveDexSceneRenderer.renderFloor(context, STADIUM_FLOOR, halfX = floorHalf, halfZ = floorHalf)

            // Dolls are separated along the forward (Z) axis — the same axis directional effects fire along —
            // so beams/sprays travel from the user doll to the target doll. User is nearer (+Z), facing -Z.
            pose.pushPose()
            pose.translate(0.0, 0.0, sceneSeparation)
            MoveDexSceneRenderer.renderModel(context, actorIdentifier(), actorState, actorScale, facingYaw = 0f, partialTicks = advance)
            pose.popPose()
            val actorRise = MoveDexSceneRenderer.lastRootRiseY // read before the target render overwrites it

            // The target is now a real posable model (a target-block-shaped dummy), so it plays the move's actual
            // `target` bedrock animation exactly like a battle defender — the same shared move animations drive its
            // root, giving faithful translate/scale/rotation instead of a procedural guess.
            pose.pushPose()
            pose.translate(0.0, 0.0, -sceneSeparation)
            MoveDexSceneRenderer.renderModel(context, TARGET, targetState, targetScale(), facingYaw = 180f, partialTicks = advance)
            pose.popPose()
            // Record the largest upward rise of either doll for NEXT frame's follow-camera (1-frame lag is fine).
            // Guard against NaN/Inf from a misbehaving animation: a non-finite rise would poison followZoomFactor
            // (which then stays NaN every frame) → finalScale NaN → pose.scale(NaN) blanks the ENTIRE viewport.
            dollRiseY = max(0f, max(actorRise, MoveDexSceneRenderer.lastRootRiseY)).let { if (it.isFinite()) it else 0f }

            // Play this frame's due cues on the matching doll, EXACTLY like battle's PlayPosableAnimationHandler:
            // addFirstAnimation across the move's ordered names, first match wins, and NO fallback — if a species
            // has no animation for the move (e.g. Charmander has no "physical", so Fire Punch resolves nothing) it
            // plays no body animation, just like battle. (We resolve across poses because the GUI doll sits in a UI
            // pose and can't enter the battle pose where those animations are registered.)
            if (pendingAnims.isNotEmpty()) {
                pendingAnims.forEach { (names, isTarget) ->
                    (if (isTarget) targetState else actorState).addFirstAnimationAcrossPoses(names)
                }
                pendingAnims.clear()
            }

            if (debugLocators) renderDebugLocators(context, pose, actorScale)
        }
        Lighting.setupFor3DItems()

        // The virtual battle camera is the INVERSE of the scene's view rotation (XP(pitch)·YP(yaw)); feeding
        // that to each particle's cameraMode makes it billboard to face the viewer, exactly like in-world.
        val cameraAngle = Quaternionf()
            .mul(Axis.XP.rotationDegrees(scenePitch))
            .mul(Axis.YP.rotationDegrees(sceneYaw))
            .conjugate()
        // Particles share the scene pose; "at target" effects emit at B, others at A.
        scene.render(
            context,
            userLocalZ = sceneSeparation.toFloat(),
            targetLocalZ = -sceneSeparation.toFloat(),
            cameraAngle = cameraAngle,
            cameraYaw = sceneYaw - 180f,
            cameraPitch = -scenePitch,
            actorState = actorState,
            targetState = targetState,
            actorScale = actorScale,
            targetScale = 1.0f
        )

        pose.popPose()
        // The 3D scene (dolls + floor) is drawn at GUI z=200 and WRITES depth, so the control buttons drawn
        // afterward at the base GUI z would fail the LEQUAL depth test and hide BEHIND the viewport. Clear the
        // depth buffer now, while the viewport scissor is still bound (glClear respects the scissor box), so only
        // the preview region is reset — every overlay control (play/pause, mute, locator, species box) then renders
        // unconditionally on top of the viewport without disturbing the rest of the screen's depth.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
        context.disableScissor()

        // Debug readout so tuning values can be reported back (drag = rotate, scroll over viewport = zoom).
        drawScaledText(
            context = context,
            text = Component.literal("Yaw ${sceneYaw.toInt()}  Pitch ${scenePitch.toInt()}  Zoom ${"%.1f".format(sceneZoom)}"),
            x = left + 3,
            y = y + VIEW_Y + 2,
            colour = 0xFFFF55,
            shadow = true,
            scale = 0.5F
        )
    }

    /**
     * Debug overlay (toggle with L): a red marker at the target block's aim point (the front-face centre every
     * move fires at and traveling effects now stop on) and blue markers at each of the actor's real geo locators,
     * placed exactly where the move emits from them (un-flipped vs the root locator, scaled, scene up = -Y).
     */
    private fun renderDebugLocators(context: GuiGraphics, pose: com.mojang.blaze3d.vertex.PoseStack, actorScale: Float) {
        MoveDexSceneRenderer.renderDebugMarker(
            context, 0f, -(TARGET_HEIGHT * 0.5f), (-sceneSeparation + TARGET_WIDTH * 0.5f).toFloat(),
            0.07f, 1f, 0.25f, 0.25f
        )
        val root = actorState.locatorStates["root"]?.getOrigin() ?: return
        pose.pushPose()
        pose.translate(0.0, 0.0, sceneSeparation)
        actorState.locatorStates.forEach { (_, wrapper) ->
            val o = wrapper.getOrigin()
            // Match emitAnchor: X is the true model offset (NOT negated — negating mirrored left/right), Y negated
            // for the upright render flip, Z forward. Keeps the markers on the same side as the model's parts.
            val mx = (o.x - root.x).toFloat()
            val my = -((o.y - root.y).toFloat())
            val mz = (o.z - root.z).toFloat()
            MoveDexSceneRenderer.renderDebugMarker(context, actorScale * mx, -(actorScale * my), actorScale * mz, 0.05f, 0.3f, 0.7f, 1f)
        }
        pose.popPose()
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]

    private fun renderPlayPause(context: GuiGraphics, left: Int, y: Int, mouseX: Int, mouseY: Int) {
        val bx = left + 2
        val by = y + VIEW_Y + VIEW_H - 12
        val hovered = mouseX in bx..(bx + 12) && mouseY in by..(by + 10)
        context.fill(bx, by, bx + 12, by + 10, if (hovered) PLATE_HOVER else PLATE)
        drawScaledText(context = context, text = Component.literal(if (playing) "||" else ">"), x = bx + 6, y = by + 2, colour = TEXT_DARK, centered = true, scale = 0.65F)
    }

    /** Speaker icon (drawn) just right of play/pause; click toggles [muted]. Red bar through it = muted. */
    private fun renderMuteButton(context: GuiGraphics, left: Int, y: Int, mouseX: Int, mouseY: Int) {
        val bx = left + 16
        val by = y + VIEW_Y + VIEW_H - 12
        val hovered = mouseX in bx..(bx + 12) && mouseY in by..(by + 10)
        context.fill(bx, by, bx + 12, by + 10, if (hovered) PLATE_HOVER else PLATE)
        val c = 0xFF33586B.toInt()
        context.fill(bx + 3, by + 4, bx + 5, by + 7, c)   // speaker body
        context.fill(bx + 5, by + 3, bx + 7, by + 8, c)   // speaker cone
        if (!muted) {
            context.fill(bx + 8, by + 4, bx + 9, by + 7, c)        // sound wave
        } else {
            context.fill(bx + 2, by + 4, bx + 10, by + 6, 0xFFCC3333.toInt()) // red "muted" bar
        }
    }

    /** Debug toggle (just right of the speaker): shows/hides the locator + target-spot markers. Also bound to L. */
    private fun renderLocatorButton(context: GuiGraphics, left: Int, y: Int, mouseX: Int, mouseY: Int) {
        val bx = left + 30
        val by = y + VIEW_Y + VIEW_H - 12
        val hovered = mouseX in bx..(bx + 12) && mouseY in by..(by + 10)
        context.fill(bx, by, bx + 12, by + 10, if (debugLocators) PLATE_SELECTED else if (hovered) PLATE_HOVER else PLATE)
        // A tiny crosshair/locator glyph: brighter when active.
        val c = if (debugLocators) 0xFF3AA0FF.toInt() else 0xFF33586B.toInt()
        context.fill(bx + 5, by + 2, bx + 7, by + 8, c) // vertical
        context.fill(bx + 3, by + 4, bx + 9, by + 6, c) // horizontal
    }

    private fun playMoveSound(id: String) {
        runCatching {
            val soundEvent = SoundEvent.createVariableRangeEvent(id.asIdentifierDefaultingNamespace())
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1f, 1f))
        }
    }

    private fun renderInfo(context: GuiGraphics, left: Int, y: Int, move: MoveTemplate) {
        // Styled identically to the main Pokédex's per-Pokémon move panel (MovesLearnsetWidget.renderDataSection):
        // the info_move_background border, the move name + PP + category icon, the power/accuracy/effect icon rows,
        // and the wrapped description on the right. `px`/`py` mirror that widget's pX+3 / pY+109 panel origin.
        val px = left + (DETAIL_W - 133) / 2     // centre the 133-wide panel in the detail column
        val py = y + INFO_PANEL_Y
        blitk(matrixStack = context.pose(), texture = MOVE_INFO_BACKGROUND, x = px, y = py, width = 133, height = 38)

        // Move name (left) + PP (right) + damage-category icon, on the panel's top strip.
        drawScaledText(context = context, text = move.displayName.copy().bold(), x = px + 2, y = py + 2, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        drawScaledTextJustifiedRight(context = context, text = lang("ui.moves.pp", move.pp), x = px + 117, y = py + 2, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        MoveCategoryIcon(x = px + 121, y = py.toDouble(), category = move.damageCategory, opacity = 1f).render(context)

        // Power / Accuracy / Effect: shared icon + label + right-justified value, one per row.
        blitk(matrixStack = context.pose(), texture = movesPowerIconResource, x = (px + 2) / INFO_SCALE, y = (py + 11.5f) / INFO_SCALE, width = MOVE_ICON_SIZE, height = MOVE_ICON_SIZE, scale = INFO_SCALE)
        blitk(matrixStack = context.pose(), texture = movesAccuracyIconResource, x = (px + 2) / INFO_SCALE, y = (py + 21.5f) / INFO_SCALE, width = MOVE_ICON_SIZE, height = MOVE_ICON_SIZE, scale = INFO_SCALE)
        blitk(matrixStack = context.pose(), texture = movesEffectIconResource, x = (px + 2) / INFO_SCALE, y = (py + 31.5f) / INFO_SCALE, width = MOVE_ICON_SIZE, height = MOVE_ICON_SIZE, scale = INFO_SCALE)
        drawScaledText(context = context, text = lang("ui.power"), x = px + 9, y = py + 12, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        drawScaledText(context = context, text = lang("ui.accuracy"), x = px + 9, y = py + 22, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        drawScaledText(context = context, text = lang("ui.effect"), x = px + 9, y = py + 32, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        drawScaledTextJustifiedRight(context = context, text = Component.literal(if (move.power > 0) move.power.toInt().toString() else "—"), x = px + 57, y = py + 12, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        drawScaledTextJustifiedRight(context = context, text = Component.literal(if (move.accuracy < 0) "—" else "${move.accuracy.toInt()}%"), x = px + 57, y = py + 22, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)
        val effect = move.effectChances.firstOrNull() ?: 0.0
        drawScaledTextJustifiedRight(context = context, text = Component.literal(if (effect > 0) "${effect.toInt()}%" else "—"), x = px + 57, y = py + 32, scale = INFO_SCALE, colour = INFO_TEXT_COLOUR)

        // SCROLLABLE description in the panel's right column (px+63..px+131 ≈ 68px), wrapped at the panel scale —
        // mirrors the main Pokédex's MoveDescriptionWidget: the mouse wheel over the content area drives contentScroll
        // (shared with the Learnset list; reset to 0 on tab switch), and only a window of lines is shown.
        val font = Minecraft.getInstance().font
        val descLines = font.split(move.description, (65f / INFO_SCALE).toInt())
        val descVisible = 6
        contentScroll = contentScroll.coerceIn(0, max(0, descLines.size - descVisible))
        val pose = context.pose()
        pose.pushPose()
        pose.scale(INFO_SCALE, INFO_SCALE, 1f)
        val end = (contentScroll + descVisible).coerceAtMost(descLines.size)
        for (i in contentScroll until end) {
            context.drawString(font, descLines[i], ((px + 63) / INFO_SCALE).toInt(), ((py + 2 + (i - contentScroll) * 6) / INFO_SCALE).toInt(), INFO_TEXT_COLOUR or 0xFF000000.toInt(), false)
        }
        pose.popPose()
        // Thin scrollbar on the panel's right edge when the description overflows (like the Pokédex description widget).
        if (descLines.size > descVisible) {
            val trackX = px + 130
            val trackTop = py + 2
            val trackH = descVisible * 6
            context.fill(trackX, trackTop, trackX + 1, trackTop + trackH, 0x60FFFFFF)
            val thumbH = max(4, trackH * descVisible / descLines.size)
            val thumbY = trackTop + (trackH - thumbH) * contentScroll / max(1, descLines.size - descVisible)
            context.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, 0xFF7EE7E5.toInt())
        }
    }

    private fun renderLearnset(context: GuiGraphics, left: Int, y: Int, move: MoveTemplate, mouseX: Int, mouseY: Int) {
        // Method sub-filter buttons (All + each method present).
        val all = MoveDexData.learnedBy(move)
        val methods = all.map { it.method }.distinct().sortedBy { it.order }
        val options = listOf<MoveDexData.LearnMethod?>(null) + methods
        val buttonW = (DETAIL_W - (options.size - 1) * 2) / options.size
        options.forEachIndexed { index, method ->
            val tx = left + index * (buttonW + 2)
            val active = method == learnMethodFilter
            val hovered = mouseX in tx..(tx + buttonW) && mouseY in (y + SUBTAB_Y)..(y + SUBTAB_Y + SUBTAB_H)
            context.fill(tx, y + SUBTAB_Y, tx + buttonW, y + SUBTAB_Y + SUBTAB_H, if (active) PLATE_SELECTED else if (hovered) PLATE_HOVER else PLATE)
            val label = method?.let { fallback("method.${it.key}", it.fallback) } ?: fallback("method.all", "All")
            drawScaledText(context = context, text = label, x = tx + buttonW / 2, y = y + SUBTAB_Y + 2, colour = TEXT_DARK, centered = true, scale = 0.5F)
        }

        val contentTop = SUBTAB_Y + SUBTAB_H + 1
        panel(context, left, y, contentTop)
        val rows = all.filter { learnMethodFilter == null || it.method == learnMethodFilter }
        if (rows.isEmpty()) {
            drawScaledText(context = context, text = fallback("no_learners", "Nothing learns this"), x = left + DETAIL_W / 2, y = y + (contentTop + CONTENT_BOTTOM) / 2 - 3, centered = true, colour = INFO_TEXT_COLOUR, scale = 0.6F)
            return
        }
        val visible = (CONTENT_BOTTOM - contentTop) / ROW_TEXT_H
        contentScroll = contentScroll.coerceIn(0, max(0, rows.size - visible))
        val end = (contentScroll + visible).coerceAtMost(rows.size)
        for (i in contentScroll until end) {
            val entry = rows[i]
            val rowY = y + contentTop + 2 + (i - contentScroll) * ROW_TEXT_H
            val seen = MoveDexData.isSpeciesSeen(entry.speciesId)
            val nameText = if (seen) {
                PokemonSpecies.getByIdentifier(entry.speciesId)?.translatedName ?: Component.literal(WorldDexData.prettify(entry.speciesId.path))
            } else Component.literal("???")
            drawScaledText(context = context, text = nameText, x = left + 4, y = rowY, colour = if (seen) INFO_TEXT_COLOUR else 0x9AA5AA, scale = 0.6F)
            val methodText = entry.method.fallback + (entry.level?.let { " $it" } ?: "")
            drawScaledText(context = context, text = Component.literal(methodText), x = left + DETAIL_W - 40, y = rowY, colour = INFO_TEXT_COLOUR, scale = 0.55F)
        }
    }

    private fun panel(context: GuiGraphics, left: Int, y: Int, contentTop: Int) {
        // No dark box — content draws straight on the panel background texture (kept as a no-op for call sites).
    }

    // ---- input ----

    fun mouseClicked(x: Int, y: Int, mouseX: Double, mouseY: Double): Boolean {
        // Arm scene-rotation dragging ONLY when the press lands inside the 3D viewport (not the move list,
        // controls, or play button) — otherwise the tiny mouse movement while clicking a move in the list
        // would spin the scene. mouseDragged checks this flag.
        val dl = x + DETAIL_X
        // The bottom-left control strip (play, mute, locator-toggle) sits inside the viewport; pressing any of it
        // must NOT arm scene-rotation drag.
        val onControls = mouseX >= dl + 2 && mouseX <= dl + 44 && mouseY >= y + VIEW_Y + VIEW_H - 12 && mouseY <= y + VIEW_Y + VIEW_H - 2
        sceneDragArmed = selected != null && !onControls &&
            mouseX >= dl && mouseX <= dl + DETAIL_W && mouseY >= y + VIEW_Y && mouseY <= y + VIEW_Y + VIEW_H


        // Move-list search bar focus.
        val sLeft = x + LIST_X
        val sTop = y + SEARCH_Y
        if (mouseX >= sLeft && mouseX <= sLeft + LIST_W && mouseY >= sTop && mouseY <= sTop + SEARCH_H) {
            searchFocused = true
            return true
        }
        searchFocused = false

        // Filter bar: click anywhere on it to cycle ALL -> Physical -> Special -> Status.
        val (fbx, fby, fbw, fbh) = filterBarRect(x, y)
        if (mouseX >= fbx && mouseX <= fbx + fbw && mouseY >= fby && mouseY <= fby + fbh) {
            filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]; scrollRow = 0; return true
        }
        // Sort buttons (10x10 each).
        val scaledSize = (SORT_BTN_SIZE * SCALE).toInt()
        sortButtonRects(x, y).forEachIndexed { i, (bx, by) ->
            if (mouseX >= bx && mouseX <= bx + scaledSize && mouseY >= by && mouseY <= by + scaledSize) { sort = Sort.entries[i]; scrollRow = 0; return true }
        }

        // Move list.
        if (mouseX >= x + LIST_X && mouseX <= x + LIST_X + LIST_W) {
            val list = moves()
            val end = (scrollRow + VISIBLE_ROWS).coerceAtMost(list.size)
            for (i in scrollRow until end) {
                val rowY = y + LIST_TOP + (i - scrollRow) * ROW_H
                if (mouseY >= rowY && mouseY <= rowY + 12) {
                    if (MoveDexData.isDiscovered(list[i])) {
                        selected = list[i]
                        activeTab = Tab.INFO
                        contentScroll = 0
                        learnMethodFilter = null
                        // Auto-play the move once on selection, then pause at the end of the cycle.
                        animDuration = MoveDexAnimation.durationFor(list[i])
                        animEvents = MoveDexAnimation.animationEventsFor(list[i])
                        nextAnimIndex = 0
                        moveSounds = MoveDexAnimation.soundsFor(list[i])
                        nextSoundIndex = 0
                        animTime = 0f
                        lastMs = 0L
                        tickAccumulatorMs = 0f
                        prepareMove(list[i]) // builds the scene + computes the fixed framing
                        playing = true
                    }
                    return true
                }
            }
            return false
        }

        val move = selected ?: return false
        val left = x + DETAIL_X

        // Play/pause. Pressing play after a finished cycle restarts it.
        val pbx = left + 2; val pby = y + VIEW_Y + VIEW_H - 12
        if (mouseX >= pbx && mouseX <= pbx + 12 && mouseY >= pby && mouseY <= pby + 10) {
            if (!playing && animTime >= animDuration) { animTime = 0f; lastMs = 0L; tickAccumulatorMs = 0f; nextSoundIndex = 0; move?.let { buildScene(it) } }
            playing = !playing
            return true
        }

        // Mute toggle (speaker icon).
        val mbx = left + 16
        if (mouseX >= mbx && mouseX <= mbx + 12 && mouseY >= pby && mouseY <= pby + 10) {
            muted = !muted
            return true
        }

        // Locator-visibility debug toggle (also bound to the L key).
        val lbx = left + 30
        if (mouseX >= lbx && mouseX <= lbx + 12 && mouseY >= pby && mouseY <= pby + 10) {
            debugLocators = !debugLocators
            return true
        }

        // Tabs (icon buttons at the bottom — hit-tested against the same 8x8 rects renderDetail draws).
        if (mouseY >= y + TAB_ROW_Y && mouseY <= y + TAB_ROW_Y + TAB_ICON_DISP) {
            Tab.entries.forEachIndexed { index, tab ->
                val ix = tabIconX(left, index)
                if (mouseX >= ix && mouseX <= ix + TAB_ICON_DISP) { activeTab = tab; contentScroll = 0; return true }
            }
        }

        // Learnset method sub-buttons.
        if (activeTab == Tab.LEARNSET && mouseY >= y + SUBTAB_Y && mouseY <= y + SUBTAB_Y + SUBTAB_H) {
            val methods = MoveDexData.learnedBy(move).map { it.method }.distinct().sortedBy { it.order }
            val options = listOf<MoveDexData.LearnMethod?>(null) + methods
            val buttonW = (DETAIL_W - (options.size - 1) * 2) / options.size
            options.forEachIndexed { index, method ->
                val tx = left + index * (buttonW + 2)
                if (mouseX >= tx && mouseX <= tx + buttonW) { learnMethodFilter = method; contentScroll = 0; return true }
            }
        }
        return false
    }

    fun mouseScrolled(x: Int, y: Int, mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (selected != null && mouseX >= x + DETAIL_X && mouseX <= x + DETAIL_X + DETAIL_W) {
            // Scroll over the viewport zooms the scene (zoom IS the scale); below it scrolls the tab content.
            if (mouseY >= y + VIEW_Y && mouseY <= y + VIEW_Y + VIEW_H) {
                sceneZoom = (sceneZoom * (1f + deltaY.toFloat() * 0.1f)).coerceIn(4f, 80f)
                return true
            }
            if (mouseY >= y + CONTENT_TOP && mouseY < y + TAB_ROW_Y) {
                contentScroll = (contentScroll - deltaY.toInt()).coerceAtLeast(0)
                return true
            }
        }
        val maxScroll = max(0, moves().size - VISIBLE_ROWS)
        scrollRow = (scrollRow - deltaY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    /** Drag inside the viewport (armed in mouseClicked) to orbit the battle scene (yaw/pitch). */
    fun mouseDragged(deltaX: Double, deltaY: Double): Boolean {
        if (!sceneDragArmed) return false
        sceneYaw += deltaX.toFloat()
        scenePitch = (scenePitch + deltaY.toFloat()).coerceIn(-90f, 90f)
        return true
    }

    fun keyPressed(keyCode: Int): Boolean {
        if (searchFocused) {
            when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> if (searchQuery.isNotEmpty()) { searchQuery = searchQuery.dropLast(1); scrollRow = 0 }
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> searchFocused = false
            }
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && selected != null) {
            if (!playing && animTime >= animDuration) animTime = 0f
            playing = !playing
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_L) { debugLocators = !debugLocators; return true }
        return false
    }

    fun charTyped(codePoint: Char): Boolean {
        if (!searchFocused) return false
        if (codePoint.code >= 32) { searchQuery += codePoint; scrollRow = 0 }
        return true
    }
}
