/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.BASE_WIDTH
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.net.messages.server.pokenav.RequestPokeNavAreaScanPacket
import com.cobblemon.mod.common.net.messages.server.pokenav.RequestPokeNavSpawnDataPacket
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.ceil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f

class PokeNavPokedexPage(
    override val id: ResourceLocation,
    override val titleKey: String
) : PokedexGuiPage {
    companion object {
        private const val LIST_LEFT = 26
        private const val LIST_TOP = 43
        private const val LIST_WIDTH = 293
        private const val LIST_HEIGHT = 149

        private const val CONTROL_TOP = 30
        private const val CONTROL_HEIGHT = 11
        private const val PREV_WIDTH = 11
        private const val NEXT_WIDTH = 11
        private const val REFRESH_WIDTH = 43

        private const val MODE_BUTTON_TOP = 194
        private const val MODE_BUTTON_HEIGHT = 11
        private const val MODE_BUTTON_WIDTH = 95
        private const val FINDER_BUTTON_WIDTH = 68
        private const val FINDER_BUTTON_HEIGHT = 11
        private const val FINDER_BUTTON_GAP = 4

        private const val GRID_COLUMNS = 5
        private const val GRID_SLOT_SIZE = 25
        private const val GRID_SLOT_SPACING = 2
        private const val GRID_Y_OFFSET = 14
        private const val GRID_X_OFFSET = 4

        private val DECIMAL_FORMAT = DecimalFormat("#.##")

        private val gridSlot = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")
        private val gridSlotHighlight = cobblemonResource("textures/gui/pokedex/slot_select.png")
        private val caughtIcon = cobblemonResource("textures/gui/pokedex/caught_icon_small.png")
    }

    private enum class ViewMode {
        SPAWNS,
        AREA_SCAN,
        FINDER
    }

    private var viewMode = ViewMode.SPAWNS
    private var selectedBucketIndex = 0
    private var spawnScrollRow = 0
    private var selectedSpawnSpeciesId: ResourceLocation? = null
    private var areaScanScrollRow = 0
    private var selectedAreaEntityUuid: UUID? = null

    override fun onSelected(gui: PokedexGUI) {
        syncSelectedBucketIndex()
        requestSpawns(bucketName = PokeNavClientState.buckets.getOrNull(selectedBucketIndex))
    }

    override fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {
        val layout = layout(x, y)

        context.fill(layout.list.left, layout.list.top, layout.list.right, layout.list.bottom, 0xA0111A21.toInt())
        context.fill(layout.list.left, layout.list.top, layout.list.right, layout.list.top + 13, 0xC01D2A34.toInt())

        renderButton(context, layout.prev, "<", mouseX, mouseY)
        renderButton(context, layout.next, ">", mouseX, mouseY)
        renderButton(context, layout.refresh, lang("ui.pokenav.spawn.refresh").string, mouseX, mouseY)

        val modeLabel = if (viewMode == ViewMode.SPAWNS) {
            lang("ui.pokenav.mode.area_scan").string
        } else {
            lang("ui.pokenav.mode.spawns").string
        }
        renderButton(context, layout.modeButton, modeLabel, mouseX, mouseY)

        when (viewMode) {
            ViewMode.SPAWNS -> renderSpawnView(context, x, y, mouseX, mouseY, layout)
            ViewMode.AREA_SCAN -> renderAreaScanView(context, x, y, mouseX, mouseY, layout, delta)
            ViewMode.FINDER -> renderFinderView(context, x, y, mouseX, mouseY, layout, delta)
        }
    }

    override fun mouseClicked(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) {
            return false
        }

        val layout = layout((gui.width - BASE_WIDTH) / 2, (gui.height - BASE_HEIGHT) / 2)
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        when {
            layout.prev.contains(mx, my) && viewMode != ViewMode.FINDER -> {
                if (viewMode == ViewMode.SPAWNS) {
                    cycleBucket(-1)
                } else if (viewMode == ViewMode.AREA_SCAN) {
                    areaScanScrollRow = (areaScanScrollRow - 1).coerceAtLeast(0)
                }
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }

            layout.next.contains(mx, my) && viewMode != ViewMode.FINDER -> {
                if (viewMode == ViewMode.SPAWNS) {
                    cycleBucket(1)
                } else if (viewMode == ViewMode.AREA_SCAN) {
                    val maxRowStart = (areaTotalRows() - areaVisibleRows()).coerceAtLeast(0)
                    areaScanScrollRow = (areaScanScrollRow + 1).coerceAtMost(maxRowStart)
                }
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }

            layout.refresh.contains(mx, my) && viewMode != ViewMode.FINDER -> {
                if (viewMode == ViewMode.SPAWNS) {
                    requestSpawns(bucketName = PokeNavClientState.buckets.getOrNull(selectedBucketIndex))
                } else if (viewMode == ViewMode.AREA_SCAN) {
                    requestAreaScan()
                }
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }

            layout.modeButton.contains(mx, my) -> {
                toggleMode()
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }
        }

        if (viewMode == ViewMode.SPAWNS) {
            val spawnEntry = getSpawnEntryAt(layout.list, mx, my)
            if (spawnEntry != null) {
                selectedSpawnSpeciesId = spawnEntry.speciesId
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }
        }

        if (viewMode == ViewMode.AREA_SCAN) {
            val areaEntry = getAreaEntryAt(layout.list, mx, my)
            if (areaEntry != null) {
                selectedAreaEntityUuid = areaEntry.entityUuid
                viewMode = ViewMode.FINDER
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }
        }

        if (viewMode == ViewMode.FINDER) {
            if (layout.finderBack.contains(mx, my)) {
                viewMode = ViewMode.AREA_SCAN
                gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                return true
            }

            if (layout.finderTrack.contains(mx, my)) {
                getSelectedAreaEntry()?.let { entry ->
                    PokeNavClientState.startTracking(entry.entityUuid, entry.speciesId)
                    Minecraft.getInstance().setScreen(null)
                    gui.playSound(CobblemonSounds.POKEDEX_CLICK_SHORT)
                    return true
                }
            }
        }

        return false
    }

    override fun mouseScrolled(gui: PokedexGUI, mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val layout = layout((gui.width - BASE_WIDTH) / 2, (gui.height - BASE_HEIGHT) / 2)
        if (!layout.list.contains(mouseX.toInt(), mouseY.toInt())) {
            return false
        }

        val direction = if (scrollY > 0) -1 else if (scrollY < 0) 1 else 0
        if (direction == 0) {
            return false
        }

        if (viewMode == ViewMode.SPAWNS) {
            val maxRowStart = (spawnTotalRows() - spawnVisibleRows()).coerceAtLeast(0)
            if (maxRowStart == 0) {
                return false
            }
            spawnScrollRow = (spawnScrollRow + direction).coerceIn(0, maxRowStart)
            return true
        }

        if (viewMode != ViewMode.AREA_SCAN) {
            return false
        }

        val maxRowStart = (areaTotalRows() - areaVisibleRows()).coerceAtLeast(0)
        if (maxRowStart == 0) {
            return false
        }
        areaScanScrollRow = (areaScanScrollRow + direction).coerceIn(0, maxRowStart)
        return true
    }

    private fun renderSpawnView(context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, layout: Layout) {
        val bucketLabel = lang(
            "ui.pokenav.spawn.bucket",
            PokeNavClientState.buckets.getOrNull(selectedBucketIndex) ?: "---"
        ).bold()

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = bucketLabel,
            x = x + 146,
            y = y + CONTROL_TOP,
            shadow = true,
            centered = true
        )

        val entries = PokeNavClientState.entries
        val rowsVisible = spawnVisibleRows()
        val totalRows = spawnTotalRows()
        val maxRowStart = (totalRows - rowsVisible).coerceAtLeast(0)
        spawnScrollRow = spawnScrollRow.coerceIn(0, maxRowStart)

        if (PokeNavClientState.isSpawnLoading && entries.isEmpty()) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.spawn.loading").bold())
            return
        }

        if (entries.isEmpty()) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.spawn.none").bold())
            return
        }

        val startIndex = spawnScrollRow * GRID_COLUMNS
        val visibleEntries = entries.drop(startIndex).take(rowsVisible * GRID_COLUMNS)
        visibleEntries.forEachIndexed { index, entry ->
            val row = index / GRID_COLUMNS
            val column = index % GRID_COLUMNS
            val slotX = layout.list.left + GRID_X_OFFSET + (column * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            val slotY = layout.list.top + GRID_Y_OFFSET + (row * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))

            blitk(
                matrixStack = context.pose(),
                texture = gridSlot,
                x = slotX,
                y = slotY,
                width = GRID_SLOT_SIZE,
                height = GRID_SLOT_SIZE
            )

            val hovered = mouseX in slotX..(slotX + GRID_SLOT_SIZE) && mouseY in slotY..(slotY + GRID_SLOT_SIZE)
            val selected = selectedSpawnSpeciesId == entry.speciesId
            if (hovered || selected) {
                blitk(
                    matrixStack = context.pose(),
                    texture = gridSlotHighlight,
                    x = slotX,
                    y = slotY,
                    width = GRID_SLOT_SIZE,
                    height = GRID_SLOT_SIZE,
                    vOffset = GRID_SLOT_SIZE,
                    textureHeight = GRID_SLOT_SIZE * 2
                )
            }

            val species = PokemonSpecies.getByIdentifier(entry.speciesId) ?: return@forEachIndexed
            context.enableScissor(
                slotX + 1,
                slotY + 1,
                slotX + GRID_SLOT_SIZE - 1,
                slotY + GRID_SLOT_SIZE - 1
            )
            context.pose().pushPose()
            context.pose().translate(slotX + (GRID_SLOT_SIZE / 2.0), slotY + 1.0, 0.0)
            context.pose().scale(2.5F, 2.5F, 1F)

            drawProfilePokemon(
                renderablePokemon = RenderablePokemon(species, setOf()),
                matrixStack = context.pose(),
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = FloatingState(),
                partialTicks = 0F,
                scale = 4.5F,
                r = 1F,
                g = 1F,
                b = 1F
            )

            context.pose().popPose()
            context.disableScissor()
        }

        val selectedEntry = entries.firstOrNull { it.speciesId == selectedSpawnSpeciesId }
            ?: visibleEntries.firstOrNull()
        if (selectedEntry != null) {
            val speciesName = PokemonSpecies.getByIdentifier(selectedEntry.speciesId)?.translatedName?.string ?: selectedEntry.speciesId.path
            val chanceText = "${DECIMAL_FORMAT.format(selectedEntry.chance)}%"
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = "$speciesName  $chanceText".text().bold(),
                x = x + 146,
                y = layout.list.top + 3,
                shadow = true,
                centered = true
            )
        }
    }

    private fun renderAreaScanView(
        context: GuiGraphics,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        layout: Layout,
        delta: Float
    ) {
        val entries = PokeNavClientState.areaScanEntries
        val rowsVisible = areaVisibleRows()
        val totalRows = areaTotalRows()
        val maxRowStart = (totalRows - rowsVisible).coerceAtLeast(0)
        areaScanScrollRow = areaScanScrollRow.coerceIn(0, maxRowStart)

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokenav.area_scan.title", PokeNavClientState.areaScanRadius.toInt()).bold(),
            x = x + 146,
            y = y + CONTROL_TOP,
            shadow = true,
            centered = true
        )

        if (PokeNavClientState.isAreaScanLoading && entries.isEmpty()) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.area_scan.loading").bold())
            return
        }

        if (entries.isEmpty()) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.area_scan.none").bold())
            return
        }

        val startIndex = areaScanScrollRow * GRID_COLUMNS
        val visibleEntries = entries.drop(startIndex).take(rowsVisible * GRID_COLUMNS)
        visibleEntries.forEachIndexed { index, entry ->
            val row = index / GRID_COLUMNS
            val column = index % GRID_COLUMNS
            val slotX = layout.list.left + GRID_X_OFFSET + (column * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            val slotY = layout.list.top + GRID_Y_OFFSET + (row * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))

            blitk(
                matrixStack = context.pose(),
                texture = gridSlot,
                x = slotX,
                y = slotY,
                width = GRID_SLOT_SIZE,
                height = GRID_SLOT_SIZE
            )

            val hovered = mouseX in slotX..(slotX + GRID_SLOT_SIZE) && mouseY in slotY..(slotY + GRID_SLOT_SIZE)
            val selected = selectedAreaEntityUuid == entry.entityUuid
            if (hovered || selected) {
                blitk(
                    matrixStack = context.pose(),
                    texture = gridSlotHighlight,
                    x = slotX,
                    y = slotY,
                    width = GRID_SLOT_SIZE,
                    height = GRID_SLOT_SIZE,
                    vOffset = GRID_SLOT_SIZE,
                    textureHeight = GRID_SLOT_SIZE * 2
                )
            }

            val species = PokemonSpecies.getByIdentifier(entry.speciesId) ?: return@forEachIndexed
            val knowledge = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(entry.speciesId)
            val isSeen = knowledge >= PokedexEntryProgress.SEEN
            val isCaught = knowledge == PokedexEntryProgress.OWNED
            val renderTint = if (isSeen) 1F else 0.07F

            context.enableScissor(
                slotX + 1,
                slotY + 1,
                slotX + GRID_SLOT_SIZE - 1,
                slotY + GRID_SLOT_SIZE - 1
            )
            context.pose().pushPose()
            context.pose().translate(slotX + (GRID_SLOT_SIZE / 2.0), slotY + 1.0, 0.0)
            context.pose().scale(2.5F, 2.5F, 1F)

            drawProfilePokemon(
                renderablePokemon = RenderablePokemon(species, setOf()),
                matrixStack = context.pose(),
                rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                state = FloatingState(),
                partialTicks = delta,
                scale = 4.5F,
                r = renderTint,
                g = renderTint,
                b = renderTint
            )

            context.pose().popPose()
            context.disableScissor()

            if (isCaught) {
                blitk(
                    matrixStack = context.pose(),
                    texture = caughtIcon,
                    x = slotX + 15,
                    y = slotY + 1.5,
                    width = 11,
                    height = 11
                )
            }
        }
    }

    private fun renderFinderView(
        context: GuiGraphics,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        layout: Layout,
        delta: Float
    ) {
        val entry = getSelectedAreaEntry()
        renderButton(context, layout.finderBack, lang("ui.pokenav.finder.back").string, mouseX, mouseY)
        renderButton(context, layout.finderTrack, lang("ui.pokenav.finder.track").string, mouseX, mouseY)

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokenav.finder.title").bold(),
            x = x + 146,
            y = y + CONTROL_TOP,
            shadow = true,
            centered = true
        )

        if (entry == null) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.finder.missing").bold())
            return
        }

        val species = PokemonSpecies.getByIdentifier(entry.speciesId)
        if (species == null) {
            drawCenteredStatus(context, x, y, lang("ui.pokenav.finder.missing").bold())
            return
        }

        val slotX = layout.list.left + 12
        val slotY = layout.list.top + 21
        val slotSize = 82
        blitk(
            matrixStack = context.pose(),
            texture = gridSlot,
            x = slotX,
            y = slotY,
            width = slotSize,
            height = slotSize
        )

        val knowledge = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(entry.speciesId)
        val isSeen = knowledge >= PokedexEntryProgress.SEEN
        val isCaught = knowledge == PokedexEntryProgress.OWNED
        val tint = if (isSeen) 1F else 0.07F

        context.enableScissor(slotX + 2, slotY + 2, slotX + slotSize - 2, slotY + slotSize - 2)
        context.pose().pushPose()
        context.pose().translate(slotX + (slotSize / 2.0), slotY + 8.0, 0.0)
        context.pose().scale(4.6F, 4.6F, 1F)
        drawProfilePokemon(
            renderablePokemon = RenderablePokemon(species, setOf()),
            matrixStack = context.pose(),
            rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
            state = FloatingState(),
            partialTicks = delta,
            scale = 7F,
            r = tint,
            g = tint,
            b = tint
        )
        context.pose().popPose()
        context.disableScissor()

        if (isCaught) {
            blitk(
                matrixStack = context.pose(),
                texture = caughtIcon,
                x = slotX + slotSize - 13,
                y = slotY + 2,
                width = 11,
                height = 11
            )
        }

        val nameText = if (isSeen) species.translatedName else "???".text()
        val statusText = when {
            isCaught -> lang("ui.pokenav.finder.status.caught")
            isSeen -> lang("ui.pokenav.finder.status.seen")
            else -> lang("ui.pokenav.finder.status.unknown")
        }

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = nameText.bold(),
            x = slotX + slotSize + 10,
            y = slotY + 6,
            shadow = true
        )
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.pokenav.finder.distance", DECIMAL_FORMAT.format(entry.distance)).bold(),
            x = slotX + slotSize + 10,
            y = slotY + 22,
            shadow = true
        )
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = statusText.bold(),
            x = slotX + slotSize + 10,
            y = slotY + 38,
            shadow = true
        )
    }

    private fun getSpawnEntryAt(listRect: Rect, mouseX: Int, mouseY: Int): com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavSpawnDataPacket.SpawnChanceEntry? {
        val entries = PokeNavClientState.entries
        val rowsVisible = spawnVisibleRows()
        val startIndex = spawnScrollRow * GRID_COLUMNS
        val visibleEntries = entries.drop(startIndex).take(rowsVisible * GRID_COLUMNS)
        visibleEntries.forEachIndexed { index, entry ->
            val row = index / GRID_COLUMNS
            val column = index % GRID_COLUMNS
            val slotX = listRect.left + GRID_X_OFFSET + (column * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            val slotY = listRect.top + GRID_Y_OFFSET + (row * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            if (mouseX in slotX..(slotX + GRID_SLOT_SIZE) && mouseY in slotY..(slotY + GRID_SLOT_SIZE)) {
                return entry
            }
        }
        return null
    }

    private fun getAreaEntryAt(listRect: Rect, mouseX: Int, mouseY: Int): com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavAreaScanDataPacket.AreaScanEntry? {
        val entries = PokeNavClientState.areaScanEntries
        val rowsVisible = areaVisibleRows()
        val startIndex = areaScanScrollRow * GRID_COLUMNS
        val visibleEntries = entries.drop(startIndex).take(rowsVisible * GRID_COLUMNS)
        visibleEntries.forEachIndexed { index, entry ->
            val row = index / GRID_COLUMNS
            val column = index % GRID_COLUMNS
            val slotX = listRect.left + GRID_X_OFFSET + (column * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            val slotY = listRect.top + GRID_Y_OFFSET + (row * (GRID_SLOT_SIZE + GRID_SLOT_SPACING))
            if (mouseX in slotX..(slotX + GRID_SLOT_SIZE) && mouseY in slotY..(slotY + GRID_SLOT_SIZE)) {
                return entry
            }
        }
        return null
    }

    private fun getSelectedAreaEntry(): com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavAreaScanDataPacket.AreaScanEntry? {
        val selected = selectedAreaEntityUuid ?: return null
        return PokeNavClientState.areaScanEntries.firstOrNull { it.entityUuid == selected }
    }

    private fun drawCenteredStatus(context: GuiGraphics, x: Int, y: Int, label: net.minecraft.network.chat.MutableComponent) {
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = label,
            x = x + (BASE_WIDTH / 2),
            y = y + 96,
            shadow = true,
            centered = true
        )
    }

    private fun areaVisibleRows(): Int {
        return ((LIST_HEIGHT - GRID_Y_OFFSET - 2) / (GRID_SLOT_SIZE + GRID_SLOT_SPACING)).coerceAtLeast(1)
    }

    private fun spawnVisibleRows(): Int {
        return ((LIST_HEIGHT - GRID_Y_OFFSET - 2) / (GRID_SLOT_SIZE + GRID_SLOT_SPACING)).coerceAtLeast(1)
    }

    private fun spawnTotalRows(): Int {
        val count = PokeNavClientState.entries.size
        if (count == 0) return 0
        return ceil(count.toDouble() / GRID_COLUMNS.toDouble()).toInt()
    }

    private fun areaTotalRows(): Int {
        val count = PokeNavClientState.areaScanEntries.size
        if (count == 0) return 0
        return ceil(count.toDouble() / GRID_COLUMNS.toDouble()).toInt()
    }

    private fun toggleMode() {
        viewMode = if (viewMode == ViewMode.SPAWNS) ViewMode.AREA_SCAN else ViewMode.SPAWNS
        if (viewMode == ViewMode.AREA_SCAN) {
            areaScanScrollRow = 0
            requestAreaScan()
        } else {
            selectedAreaEntityUuid = null
        }
    }

    private fun cycleBucket(delta: Int) {
        val buckets = PokeNavClientState.buckets
        if (buckets.isEmpty()) {
            requestSpawns(bucketName = null)
            return
        }

        selectedBucketIndex = Math.floorMod(selectedBucketIndex + delta, buckets.size)
        spawnScrollRow = 0
        selectedSpawnSpeciesId = null
        requestSpawns(bucketName = buckets[selectedBucketIndex])
    }

    private fun requestSpawns(bucketName: String?) {
        PokeNavClientState.markSpawnLoading()
        RequestPokeNavSpawnDataPacket(bucketName = bucketName).sendToServer()
    }

    private fun requestAreaScan() {
        PokeNavClientState.markAreaScanLoading()
        RequestPokeNavAreaScanPacket().sendToServer()
    }

    private fun syncSelectedBucketIndex() {
        val buckets = PokeNavClientState.buckets
        if (buckets.isEmpty()) {
            selectedBucketIndex = 0
            return
        }

        val stateIndex = buckets.indexOf(PokeNavClientState.selectedBucket)
        selectedBucketIndex = if (stateIndex >= 0) {
            stateIndex
        } else {
            selectedBucketIndex.coerceIn(0, buckets.lastIndex)
        }
    }

    private fun renderButton(
        context: GuiGraphics,
        rect: Rect,
        label: String,
        mouseX: Int,
        mouseY: Int
    ) {
        val hovered = rect.contains(mouseX, mouseY)
        context.fill(rect.left, rect.top, rect.right, rect.bottom, if (hovered) 0xE03F5364.toInt() else 0xC02A3A49.toInt())
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = label.text().bold(),
            x = rect.left + ((rect.right - rect.left) / 2),
            y = rect.top + 2,
            shadow = false,
            centered = true
        )
    }

    private fun layout(x: Int, y: Int): Layout {
        val prev = Rect(x + 26, y + CONTROL_TOP, x + 26 + PREV_WIDTH, y + CONTROL_TOP + CONTROL_HEIGHT)
        val next = Rect(prev.right + 2, y + CONTROL_TOP, prev.right + 2 + NEXT_WIDTH, y + CONTROL_TOP + CONTROL_HEIGHT)
        val refresh = Rect(next.right + 4, y + CONTROL_TOP, next.right + 4 + REFRESH_WIDTH, y + CONTROL_TOP + CONTROL_HEIGHT)
        val modeButton = Rect(x + 26, y + MODE_BUTTON_TOP, x + 26 + MODE_BUTTON_WIDTH, y + MODE_BUTTON_TOP + MODE_BUTTON_HEIGHT)
        val finderBack = Rect(
            modeButton.left,
            modeButton.top,
            modeButton.left + FINDER_BUTTON_WIDTH,
            modeButton.top + FINDER_BUTTON_HEIGHT
        )
        val finderTrack = Rect(
            finderBack.right + FINDER_BUTTON_GAP,
            modeButton.top,
            finderBack.right + FINDER_BUTTON_GAP + FINDER_BUTTON_WIDTH,
            modeButton.top + FINDER_BUTTON_HEIGHT
        )
        val list = Rect(x + LIST_LEFT, y + LIST_TOP, x + LIST_LEFT + LIST_WIDTH, y + LIST_TOP + LIST_HEIGHT)
        return Layout(prev = prev, next = next, refresh = refresh, modeButton = modeButton, finderBack = finderBack, finderTrack = finderTrack, list = list)
    }

    private data class Layout(
        val prev: Rect,
        val next: Rect,
        val refresh: Rect,
        val modeButton: Rect,
        val finderBack: Rect,
        val finderTrack: Rect,
        val list: Rect
    )

    private data class Rect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun contains(x: Int, y: Int) = x in left..right && y in top..bottom
    }
}
