/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
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
        private const val TABS_Y = 126
        private const val TAB_H = 11
        private const val SUBTAB_Y = 140
        private const val SUBTAB_H = 10
        private const val CONTENT_BOTTOM = 188
        private const val SPECIES_H = 11

        private val SUBSTITUTE = cobblemonResource("substitute")
        private val STADIUM_FLOOR = cobblemonResource("textures/gui/pokedex/movedex_stadium_floor.png")
        // The move's target dummy: a posable target-block-shaped model so it can play the move's real `target`
        // bedrock animation (throw/squash/recoil) exactly like a battle defender, rather than a static block.
        private val TARGET = cobblemonResource("targetdummy")
        // Rendered size of the target dummy (its geo is a 1-block cube). Moves aim at the centre of the face
        // nearest the attacker, so projectiles strike the front like a real target.
        private const val TARGET_BLOCK_SIZE = 1.0f
        // Texture-V of each stand pad measured from the floor's centre (the PNG's A/B pads sit at V 0.75/0.25).
        // The floor's Z half-extent is derived from this so the pads always land exactly under the two dolls,
        // however far apart the per-move separation pushes them.
        private const val FLOOR_PAD_V = 0.25f
        // Generic move animations appended after a move's own (ordered) names so SOME attack animation plays even
        // when a species lacks the specific one — e.g. Lapras has no "physical", so a physical move falls back to
        // its "special"/"status" animation. Tried only after the move's specific names, so it never overrides them.
        private val ACTOR_ANIM_FALLBACKS = listOf("physical", "special", "status")

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
    // The Pokémon performing the move (null = the substitute doll), chosen via the species search box.
    private var actorSpecies: ResourceLocation? = null
    private var speciesQuery = ""
    private var speciesFocused = false
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
        speciesQuery = ""
        speciesFocused = false
        actorSpecies = null
        // Always start at the default camera angle + zoom; move/species changes never touch them (only drag/scroll).
        sceneYaw = SCENE_YAW
        scenePitch = SCENE_PITCH
        sceneZoom = DEFAULT_ZOOM
    }

    /** The model identifier for the actor doll — the chosen species, or the substitute fallback. */
    private fun actorIdentifier(): ResourceLocation = actorSpecies ?: SUBSTITUTE

    /** Resolves the species search text to a species model (live), falling back to the substitute. */
    private fun resolveSpecies() {
        val query = speciesQuery.trim().lowercase().replace(" ", "")
        val newSpecies = if (query.isEmpty()) null else PokemonSpecies.getByName(query)?.resourceIdentifier
        if (newSpecies != actorSpecies) {
            actorSpecies = newSpecies
            actorState = FloatingState() // fresh state so the new model re-poses cleanly
            // Recompute the centroid for the new Pokémon (its hitbox/size changes the bounds) and replay the
            // move from the start on it — same treatment as choosing a new move. Rotation/zoom are untouched.
            selected?.let {
                animTime = 0f
                lastMs = 0L
                tickAccumulatorMs = 0f
                nextSoundIndex = 0
                nextAnimIndex = 0
                prepareMove(it)
                playing = true
            }
        }
    }

    /** (height, width) hitbox of the chosen actor species in world blocks; substitute-ish default otherwise. */
    private fun actorHW(): Pair<Float, Float> {
        val s = actorSpecies?.let { PokemonSpecies.getByIdentifier(it) } ?: return 2.0f to 1.0f
        return s.hitbox.height to s.hitbox.width
    }

    /** (height, width) of the target dummy — a 1-block target model, so square in both dims. */
    private fun targetHW(): Pair<Float, Float> = TARGET_BLOCK_SIZE to TARGET_BLOCK_SIZE

    /** World render scale of the target dummy: its geo is a 16-unit (1-block) cube, so 1.0 renders ~1 block. */
    private fun targetScale(): Float = TARGET_BLOCK_SIZE

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
    private fun prepareMove(move: MoveTemplate) {
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

    private fun renderDetail(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val move = selected ?: return
        val left = x + DETAIL_X

        context.fill(left, y + HEADER_Y, left + DETAIL_W, y + HEADER_Y + HEADER_H, PLATE)
        drawScaledText(context = context, text = move.displayName.copy().bold(), x = left + 4, y = y + HEADER_Y + 3, colour = move.elementalType.hue, scale = 0.75F)

        // Battle-style viewport: the chosen Pokémon (or substitute) performs the move on a substitute target.
        context.fill(left, y + VIEW_Y, left + DETAIL_W, y + VIEW_Y + VIEW_H, PREVIEW_BG)
        renderDolls(context, left, y, delta)
        renderPlayPause(context, left, y, mouseX, mouseY)
        renderMuteButton(context, left, y, mouseX, mouseY)
        renderLocatorButton(context, left, y, mouseX, mouseY)
        renderSpeciesBox(context, left, y, mouseX, mouseY)

        // Tabs.
        val tabs = Tab.entries
        val tabW = (DETAIL_W - (tabs.size - 1) * 2) / tabs.size
        tabs.forEachIndexed { index, tab ->
            val tx = left + index * (tabW + 2)
            val hovered = mouseX in tx..(tx + tabW) && mouseY in (y + TABS_Y)..(y + TABS_Y + TAB_H)
            context.fill(tx, y + TABS_Y, tx + tabW, y + TABS_Y + TAB_H, if (tab == activeTab) PLATE_SELECTED else if (hovered) PLATE_HOVER else PLATE)
            drawScaledText(context = context, text = fallback(tab.key, tab.label), x = tx + tabW / 2, y = y + TABS_Y + 3, colour = TEXT_DARK, centered = true, scale = 0.55F)
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
            // Loop as soon as every effect has finished (scene emptied) — don't sit frozen through the
            // action effect's trailing battle pauses — with animDuration as a hard fallback cap.
            if (animTime >= animDuration || (animTime > 0.5f && scene.isEmpty)) {
                animTime = 0f
                tickAccumulatorMs = 0f
                nextSoundIndex = 0
                nextAnimIndex = 0
                selected?.let { buildScene(it) }
            }
        }
        val partial = (tickAccumulatorMs / 50f).coerceIn(0f, 1f)
        // Give the scene the current dolls so it can re-aim moves from the real emit locators toward the
        // opposing doll's centre (the "target spot"); the substitute target has no locators so its centre
        // (mid-height) is the defined aim point.
        val aScale = actorSpecies?.let { PokemonSpecies.getByIdentifier(it)?.baseScale } ?: 1.0f
        val (ahh, _) = actorHW()
        // The target is the block: its centre height is half its size, and the face the attacker fires at is the
        // near (+Z) face — half a block toward the attacker from the block's centre at -separation.
        val targetCentreY = TARGET_BLOCK_SIZE * 0.5f
        val targetFaceZ = -sceneSeparation.toFloat() + TARGET_BLOCK_SIZE * 0.5f
        scene.setAimContext(
            actorState, targetState, aScale, 1.0f,
            sceneSeparation.toFloat(), -sceneSeparation.toFloat(), ahh * 0.5f, targetCentreY, targetFaceZ
        )
        scene.update(steps, partial)

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
        val rise = dollRiseY.coerceIn(0f, 40f)
        val desiredFactor = (baseExtentY / (baseExtentY + rise)).coerceIn(0.2f, 1f)
        val desiredPanY = -rise / 2f
        val ease = if (playing) 0.18f else 0.5f
        followZoomFactor += (desiredFactor - followZoomFactor) * ease
        followPanY += (desiredPanY - followPanY) * ease

        val finalScale = sceneZoom * followZoomFactor
        // Pan so the (rotated) content pivot (raised by the follow offset) lands at the viewport centre.
        val pv = Vector3f(fixedPivot[0], fixedPivot[1] + followPanY, fixedPivot[2])
        Quaternionf().mul(Axis.XP.rotationDegrees(scenePitch)).mul(Axis.YP.rotationDegrees(sceneYaw)).transform(pv)
        val cx = left + DETAIL_W / 2.0
        val cy = y + VIEW_Y + VIEW_H / 2.0

        val pose = context.pose()
        pose.pushPose()
        pose.translate(cx - finalScale * pv.x, cy - finalScale * pv.y, 200.0)
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
            val floorHalfZ = (sceneSeparation / (2.0 * FLOOR_PAD_V)).toFloat()
            MoveDexSceneRenderer.renderFloor(context, STADIUM_FLOOR, halfX = floorHalfZ * 0.7f, halfZ = floorHalfZ)

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
            dollRiseY = max(0f, max(actorRise, MoveDexSceneRenderer.lastRootRiseY))

            // Play this frame's due cues on the matching doll. Both are real models, so each plays the move's
            // bedrock animation (the actor's windup/overlay, the target's hit/throw/squash) just like battle.
            // Move-specific animations live in shared files resolved by the "group:key" names; the actor also gets
            // generic fallbacks (physical/special/status) so a species lacking the specific one still reacts.
            if (pendingAnims.isNotEmpty()) {
                pendingAnims.forEach { (names, isTarget) ->
                    if (isTarget) {
                        targetState.addFirstAnimationAcrossPoses(names)
                    } else {
                        actorState.addFirstAnimationAcrossPoses(names + ACTOR_ANIM_FALLBACKS)
                    }
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
            context, 0f, -(TARGET_BLOCK_SIZE * 0.5f), (-sceneSeparation + TARGET_BLOCK_SIZE * 0.5).toFloat(),
            0.07f, 1f, 0.25f, 0.25f
        )
        val root = actorState.locatorStates["root"]?.getOrigin() ?: return
        pose.pushPose()
        pose.translate(0.0, 0.0, sceneSeparation)
        actorState.locatorStates.forEach { (_, wrapper) ->
            val o = wrapper.getOrigin()
            val mx = -((o.x - root.x).toFloat())
            val my = -((o.y - root.y).toFloat())
            val mz = (o.z - root.z).toFloat()
            MoveDexSceneRenderer.renderDebugMarker(context, actorScale * mx, -(actorScale * my), actorScale * mz, 0.05f, 0.3f, 0.7f, 1f)
        }
        pose.popPose()
    }

    /** Rect of the species search box — its own row between the move header and the viewport. */
    private fun speciesBoxRect(left: Int, y: Int): IntArray = intArrayOf(left, y + SPECIES_Y, DETAIL_W, SPECIES_H)

    private fun renderSpeciesBox(context: GuiGraphics, left: Int, y: Int, mouseX: Int, mouseY: Int) {
        val (bx, by, bw, bh) = speciesBoxRect(left, y)
        val hovered = mouseX in bx..(bx + bw) && mouseY in by..(by + bh)
        context.fill(bx, by, bx + bw, by + bh, if (speciesFocused || hovered) PLATE_HOVER else PLATE)
        val text: MutableComponent = when {
            speciesQuery.isEmpty() && !speciesFocused -> fallback("species", "Pokémon: Substitute")
            else -> Component.literal(speciesQuery + if (speciesFocused) "_" else "")
        }
        drawScaledText(context = context, text = text, x = bx + 3, y = by + 2, colour = TEXT_DARK, scale = 0.6F)
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
        panel(context, left, y, TABS_Y + TAB_H + 1)
        val top = y + TABS_Y + TAB_H + 3
        fun line(i: Int, label: String, value: MutableComponent) {
            drawScaledText(context = context, text = Component.literal(label), x = left + 4, y = top + i * ROW_TEXT_H, shadow = true, scale = 0.6F)
            drawScaledText(context = context, text = value, x = left + 54, y = top + i * ROW_TEXT_H, shadow = true, scale = 0.6F)
        }
        line(0, "Type", Component.literal(WorldDexData.prettify(move.elementalType.name)).withColor(move.elementalType.hue))
        line(1, "Category", move.damageCategory.displayName.copy())
        line(2, "Power", Component.literal(if (move.power > 0) move.power.toInt().toString() else "—"))
        line(3, "Accuracy", Component.literal(if (move.accuracy < 0) "—" else "${move.accuracy.toInt()}%"))
        line(4, "PP", Component.literal(move.pp.toString()))
        val effect = move.effectChances.firstOrNull() ?: 0.0
        line(5, "Effect", Component.literal(if (effect > 0) "${effect.toInt()}%" else "—"))
        // Description (single wrapped block at the bottom).
        drawScaledText(context = context, text = move.description, x = left + 4, y = top + 6 * ROW_TEXT_H + 2, shadow = true, scale = 0.55F, maxCharacterWidth = (DETAIL_W - 8))
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
            drawScaledText(context = context, text = fallback("no_learners", "Nothing learns this"), x = left + DETAIL_W / 2, y = y + (contentTop + CONTENT_BOTTOM) / 2 - 3, centered = true, shadow = true, scale = 0.6F)
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
            drawScaledText(context = context, text = nameText, x = left + 4, y = rowY, colour = if (seen) 0xFFFFFF else TEXT_UNKNOWN, shadow = true, scale = 0.6F)
            val methodText = entry.method.fallback + (entry.level?.let { " $it" } ?: "")
            drawScaledText(context = context, text = Component.literal(methodText), x = left + DETAIL_W - 40, y = rowY, shadow = true, scale = 0.55F)
        }
    }

    private fun panel(context: GuiGraphics, left: Int, y: Int, contentTop: Int) {
        context.fill(left, y + contentTop, left + DETAIL_W, y + CONTENT_BOTTOM, PANEL_BG)
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

        // Species box focus (overlaid on the viewport; only present when a move is selected).
        if (selected != null) {
            val (pbx, pby, pbw, pbh) = speciesBoxRect(x + DETAIL_X, y)
            if (mouseX >= pbx && mouseX <= pbx + pbw && mouseY >= pby && mouseY <= pby + pbh) {
                speciesFocused = true
                searchFocused = false
                return true
            }
        }
        speciesFocused = false

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

        // Tabs.
        val tabs = Tab.entries
        if (mouseY >= y + TABS_Y && mouseY <= y + TABS_Y + TAB_H) {
            val tabW = (DETAIL_W - (tabs.size - 1) * 2) / tabs.size
            tabs.forEachIndexed { index, tab ->
                val tx = left + index * (tabW + 2)
                if (mouseX >= tx && mouseX <= tx + tabW) { activeTab = tab; contentScroll = 0; return true }
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
            if (mouseY >= y + TABS_Y + TAB_H) {
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
        if (speciesFocused) {
            when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> if (speciesQuery.isNotEmpty()) { speciesQuery = speciesQuery.dropLast(1); resolveSpecies() }
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> speciesFocused = false
            }
            return true
        }
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
        if (speciesFocused) {
            if (codePoint.code >= 32) { speciesQuery += codePoint; resolveSpecies() }
            return true
        }
        if (!searchFocused) return false
        if (codePoint.code >= 32) { searchQuery += codePoint; scrollRow = 0 }
        return true
    }
}
