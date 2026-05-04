/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.battle.preview

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.gui.ParentWidget
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.gui.renderSprite
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.text.yellow
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.battle.preview.ClientBattleTeamPreview
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.PORTRAIT_DIAMETER
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.SCALE
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.questionMarkIcon
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.net.messages.client.battle.BattlePokemonDTO
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.toHex
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class TeamPreviewWidget(val preview: ClientBattleTeamPreview) : ParentWidget(
    pX = 0,
    pY = if (Minecraft.getInstance().window.guiScaledHeight > 304) (Minecraft.getInstance().window.guiScaledHeight / 2) - (BACKGROUND_HEIGHT / 2)
    else Minecraft.getInstance().window.guiScaledHeight - (BACKGROUND_HEIGHT + 78),
    pWidth = Minecraft.getInstance().window.guiScaledWidth,
    pHeight = Minecraft.getInstance().window.guiScaledHeight,
    "cobblemon.battle.ui.team_info".asTranslated()
) {
    companion object {
        const val SLOT_HORIZONTAL_SPACING = 4F
        const val SLOT_VERTICAL_SPACING = 2F

        const val BACKGROUND_HEIGHT = 148
        val underlayTexture = cobblemonResource("textures/gui/battle/selection_underlay.png")
        val title = cobblemonResource("textures/gui/battle/team_preview_title.png")
        val checkmark = cobblemonResource("textures/gui/battle/checkmark.png")
        var visible = false
    }

    val teamTiles = mutableListOf<PokemonTile>()
    val opponentTiles = mutableListOf<PokemonTile>()

    init {
        addTiles()
    }

    fun addTiles() {
        preview.team.forEachIndexed { index, pokemon ->
            if (pokemon == null) return@forEachIndexed
            val (slotX, slotY) = getSlotPosition(index, 1)
            teamTiles.add(PokemonTile(slotX, slotY, pokemon, this, false))
        }
        preview.opponent.forEachIndexed { index, pokemon ->
            if (pokemon == null) return@forEachIndexed
            val (slotX, slotY) = getSlotPosition(index, 2)
            opponentTiles.add(PokemonTile(slotX, slotY, pokemon, this, true))
        }
    }

    fun getSlotPosition(index: Int, side: Int): Pair<Float, Float> {
        val startX = if (side == 1) 10 else width - 10 - (41 + SLOT_HORIZONTAL_SPACING) * 2
        val startY = y + 15
        val row = index / 2
        val column = index % 2
        val slotX = startX.toFloat() + column * (SLOT_HORIZONTAL_SPACING + 41)
        val slotY = startY.toFloat() + row * (SLOT_VERTICAL_SPACING + 39)
        return Pair(slotX, slotY)
    }

    private fun getHoveredTile(team: Boolean, mouseX: Int, mouseY: Int): Int {
        val tiles = if (team) teamTiles else opponentTiles
        return tiles.indexOfFirst { it.isHovered(mouseX.toDouble(), mouseY.toDouble()) }
    }

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val matrixStack = context.pose()
        val mc = Minecraft.getInstance()
        blitk(
            matrixStack = matrixStack,
            texture = underlayTexture,
            x = x,
            y = y,
            width = width,
            height = BACKGROUND_HEIGHT
        )

        blitk(
            matrixStack = matrixStack,
            texture = title,
            x = (mc.window.guiScaledWidth / 2) - (103 / 2),
            y = 4,
            width = 103,
            height = 22
        )

        drawScaledText(
            context = context,
            text = "cobblemon.battle.ui.team_preview.title".text().bold(),
            x = (mc.window.guiScaledWidth / 2),
            y = 6,
            scale = 0.75f,
            shadow = true,
            centered = true
        )

        val totalSecondsRemaining = Duration.between(Instant.now(), preview.mustPickBy).seconds
        val minutesRemaining = max(0, totalSecondsRemaining / 60)
        val secondsRemaining = max(0, totalSecondsRemaining % 60)
        val secondsRemainingStr = String.format("%02d", secondsRemaining)
        val timer = "$minutesRemaining:$secondsRemainingStr"
        val text = when {
            totalSecondsRemaining <= 15 -> timer.text().red().bold()
            totalSecondsRemaining <= 30 -> timer.text().yellow().bold()
            else -> timer.text().bold()
        }
        drawScaledText(
            context = context,
            text = text,
            x = (mc.window.guiScaledWidth / 2) + 2,
            y = 15,
            scale = 0.75f,
            shadow = true,
            centered = true
        )


        val ellipsis = ".".repeat(3 - (secondsRemaining % 3).toInt())
        if (preview.selection.size == preview.selections) {
            blitk(
                matrixStack = matrixStack,
                texture = checkmark,
                x = (mc.window.guiScaledWidth / 2) - 39,
                y = 24,
                width = 8,
                height = 6
            )
            val offset = when (ellipsis.length) {
                1 -> 0.5
                2 -> 1.0
                else -> 2.0
            }
            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.team_preview.waiting".asTranslated(ellipsis).bold(),
                x = (mc.window.guiScaledWidth / 2) + 2.0 + offset,
                y = 26,
                scale = 0.5f,
                shadow = true,
                centered = true
            )
        }

        blitk(
            matrixStack = matrixStack,
            texture = cobblemonResource("textures/gui/battle/name_plate.png"),
            x = 10,
            y = y - 8,
            width = 128,
            height = 8
        )

        drawScaledText(
            context = context,
            text = mc.player!!.name.string.text(),
            y = y - 8 + 2,
            x = x + 14 + 10,
            scale = SCALE,
            shadow = true
        )

        blitk(
            matrixStack = matrixStack,
            texture = cobblemonResource("textures/gui/battle/nameplate_reversed.png"),
            x = mc.window.guiScaledWidth - 14 - 128,
            y = y - 8,
            width = 128,
            height = 8
        )

        drawScaledTextJustifiedRight(
            context = context,
            text = preview.opponentName.string.text(),
            y = y - 8 + 2,
            x = mc.window.guiScaledWidth - 14 - 12,
            scale = SCALE,
            shadow = true
        )

        teamTiles.forEachIndexed { index, tile ->
            val hoveredTile = getHoveredTile(true, mouseX, mouseY)
            if (shouldHide(hoveredTile, index, false)) return@forEachIndexed
            tile.render(context, mouseX.toDouble(), mouseY.toDouble(), delta)
        }
        opponentTiles.forEachIndexed { index, tile ->
            val hoveredTile = getHoveredTile(false, mouseX, mouseY)
            if (shouldHide(hoveredTile, index, true)) return@forEachIndexed
            tile.render(context, mouseX.toDouble(), mouseY.toDouble(), delta)
        }
    }

    private fun shouldHide(hoveredTile: Int, currentTile: Int, reversed: Boolean): Boolean {
        if (hoveredTile != -1) {
            val hoveredRow = hoveredTile / 2
            val hoveredColumn = hoveredTile % 2
            val currentRow = currentTile / 2
            val currentColumn = currentTile % 2
            if (!reversed && hoveredColumn < currentColumn && hoveredRow <= currentRow) return true
            if (reversed && hoveredColumn > currentColumn && hoveredRow <= currentRow) return true
        }
        return false
    }

    override fun mouseClicked(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        val tile = teamTiles.firstOrNull { it.isHovered(pMouseX, pMouseY) }
        if (tile != null && tile.pokemonDto in preview.team) {
            if (pButton == InputConstants.MOUSE_BUTTON_LEFT) {
                if (tile.pokemonDto !in preview.selection && preview.selection.size < preview.selections) {
                    preview.select(tile.pokemonDto)
                }
            }
            else if (pButton == InputConstants.MOUSE_BUTTON_RIGHT) {
                if (tile.pokemonDto in preview.selection) {
                    preview.unselect(tile.pokemonDto)
                }
            }
        }
        playDownSound(Minecraft.getInstance().soundManager)
        return super.mouseClicked(pMouseX, pMouseY, pButton)
    }

    override fun defaultButtonNarrationText(builder: NarrationElementOutput) {
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))
    }

    override fun narrationPriority() = NarratableEntry.NarrationPriority.HOVERED

    class PokemonTile(
        val x: Float,
        val y: Float,
        val pokemonDto: BattlePokemonDTO,
        val widget: TeamPreviewWidget,
        val reversed: Boolean
    ) {
        companion object {
            const val SELECT_WIDTH = 41
            const val SELECT_HEIGHT = 39
            const val SCALE = 0.5F
            val expandedPokemonInfo = cobblemonResource("textures/gui/battle/expanded_pokemon_info_center.png")

            val pokemonTile = cobblemonResource("textures/gui/battle/pokemon_tile.png")
            val pokemonTileReversed = cobblemonResource("textures/gui/battle/pokemon_tile_reversed.png")
            val pokemonTileDisabled = cobblemonResource("textures/gui/battle/pokemon_tile_disabled.png")
            val pokemonTileDisabledReversed = cobblemonResource("textures/gui/battle/pokemon_tile_disabled_reversed.png")
            val pokemonTileSelected = cobblemonResource("textures/gui/battle/pokemon_tile_selected.png")
            val pokemonTileSelectedReversed = cobblemonResource("textures/gui/battle/pokemon_tile_selected_reversed.png")

            val hoverGapSelected = cobblemonResource("textures/gui/battle/hover_gap_selected.png")
            val hoverGap = cobblemonResource("textures/gui/battle/hover_gap.png")
            val hoverGapReversed = cobblemonResource("textures/gui/battle/hover_gap_reversed.png")

            private val decimalFormat = DecimalFormat("0.00").also {
                it.roundingMode = RoundingMode.CEILING
            }
        }

        val state = FloatingState()

        fun isHovered(mouseX: Double, mouseY: Double) = mouseX in x..(x + SELECT_WIDTH) && mouseY in (y..(y + SELECT_HEIGHT))

        fun render(context: GuiGraphics, mouseX: Double, mouseY: Double, deltaTicks: Float) {
            val pokemon = pokemonDto.activeBattlePokemonDTO ?: return
            val battlePokemon = ClientBattlePokemon(
                uuid = pokemon.uuid,
                properties = pokemon.properties,
                aspects = pokemon.aspects,
                displayName = pokemon.displayName,
                hpValue = pokemon.hpValue,
                maxHp = pokemon.maxHp,
                isHpFlat = pokemon.isFlatHp,
                status = pokemon.status,
                statChanges = pokemon.statChanges,
            )
            state.currentAspects = pokemon.aspects
            val matrixStack = context.pose()
            val isFainted = pokemonDto.fainted
            val isSelected = pokemonDto in widget.preview.selection

            blitk(
                matrixStack = matrixStack,
                texture = cobblemonResource("textures/gui/battle/battle_info_underlay.png"),
                x = x + 5 + (if (reversed) 3 else 0),
                y = y + 4,
                width = 28,
                height = 28
            )
            val speciesToDisplay = battlePokemon.species
            val stateToDisplay = battlePokemon.state
            context.enableScissor(
                (x + 5 + (if (reversed) 3 else 0)).toInt(),
                (y + 5).toInt(),
                (x + 5 + 28 + (if (reversed) 3 else 0)).toInt(),
                (y + 5 + 28).toInt(),
            )
            matrixStack.pushPose()
            matrixStack.translate(
                x + (if (reversed) 3 else 0) + 5 + 28 / 2.0,
                y.toDouble() + 5 - 5.0,
                0.0
            )
            drawCustomPosablePortrait(
                identifier = speciesToDisplay.resourceIdentifier,
                matrixStack = matrixStack,
                scale = 18f,
                contextScale = speciesToDisplay.getForm(stateToDisplay.currentAspects).baseScale,
                reversed = reversed,
                doQuirks = false,
                state = stateToDisplay,
                partialTicks = if (Cobblemon.config.animateBattleTiles) deltaTicks else 0F
            )
            matrixStack.popPose()
            context.disableScissor()

            val tile = if (reversed) pokemonTileReversed else pokemonTile
            val disabled = if (reversed) pokemonTileDisabledReversed else pokemonTileDisabled
            val selected = if (reversed) pokemonTileSelectedReversed else pokemonTileSelected
            if (isFainted) {
                blitk(
                    matrixStack = matrixStack,
                    texture = disabled,
                    x = x,
                    y = y,
                    width = 41,
                    height = 39
                )
            }
            else if (isSelected) {
                blitk(
                    matrixStack = matrixStack,
                    texture = selected,
                    x = x,
                    y = y,
                    width = 41,
                    height = 39
                )

                val selectionIndex = widget.preview.selection.indexOf(pokemonDto)
                if (selectionIndex != -1) {
                    drawScaledText(
                        context = context,
                        font = CobblemonResources.DEFAULT_LARGE,
                        text = "${selectionIndex + 1}".text().bold(),
                        x = x + 34,
                        y = y + 29,
                        scale = 1.25f,
                        opacity = 1f,
                        shadow = true
                    )
                }
            }
            else {
                blitk(
                    matrixStack = matrixStack,
                    texture = tile,
                    x = x,
                    y = y,
                    width = 41,
                    height = 39
                )
            }

            if (isHovered(mouseX, mouseY)) {
                renderExpandedInfo(context, isSelected, reversed, battlePokemon, pokemonDto)
            }
        }

        private fun renderExpandedInfo(context: GuiGraphics, selected: Boolean, reversed: Boolean, pokemon: ClientBattlePokemon, dto: BattlePokemonDTO) {
            context.pose().pushPose()
            context.pose().translate(0.0, 0.0, 300.0)
            val startX = if (reversed) x - 136 else x + 40
            val startY = y
            val species = pokemon.species
            val form = species.getForm(pokemon.state.currentAspects)
            val expandedInfoTexture = expandedPokemonInfo

            if (selected) {
                blitk(
                    matrixStack = context.pose(),
                    texture = hoverGapSelected,
                    x = startX - 3,
                    y = startY,
                    height = 30,
                    width = 4,
                    textureHeight = 30,
                    textureWidth = 4,
                )
            }
            else {
                val gap = if (reversed) hoverGapReversed else hoverGap
                val xOffset = if (reversed) 136 else -7
                blitk(
                    matrixStack = context.pose(),
                    texture = gap,
                    x = startX + xOffset,
                    y = startY,
                    height = 36,
                    width = 8,
                    textureHeight = 36,
                    textureWidth = 8,
                )
            }

            blitk(
                matrixStack = context.pose(),
                texture = expandedInfoTexture,
                x = startX,
                y = startY,
                height = 112,
                width = 137,
                textureHeight = 112,
                textureWidth = 137,
            )

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.label.pokemon_form".asTranslated().bold(),
                x = startX + 36,
                y = startY + 6.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            drawScaledText(
                context = context,
                text = getFormText(species, form),
                x = startX + 36,
                y = startY + 13.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.label.buffs".asTranslated().bold(),
                x = startX + 36,
                y = startY + 23.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            drawScaledText(
                context = context,
                text = "cobblemon.stat.attack.name".asTranslated(),
                x = startX + 10,
                y = startY + 31.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.ATTACK)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 31.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.stat.defence.name".asTranslated(),
                x = startX + 10,
                y = startY + 39.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.DEFENCE)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 39.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.stats.special_attack".asTranslated(),
                x = startX + 10,
                y = startY + 47.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.SPECIAL_ATTACK)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 47.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.stats.special_defence".asTranslated(),
                x = startX + 10,
                y = startY + 55.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.SPECIAL_DEFENCE)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 55.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.stat.speed.name".asTranslated(),
                x = startX + 10,
                y = startY + 63.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.SPEED)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 63.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.stat.accuracy.name".asTranslated(),
                x = startX + 10,
                y = startY + 71.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto.buffs.get(Stats.ACCURACY)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 71.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.stat.evasion.name".asTranslated(),
                x = startX + 10,
                y = startY + 79.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = false
            )

            dto?.buffs?.get(Stats.EVASION)?.let {
                drawScaledText(
                    context = context,
                    text = getMultiplierText(it),
                    x = startX + 50,
                    y = startY + 79.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = false
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.label.ability".asTranslated().bold(),
                x = startX + 100,
                y = startY + 6.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            val ability = dto.ability?.let { Abilities.get(it) }
            val abilityName = ability?.displayName?.text() ?: "?".text()

            drawScaledText(
                context = context,
                text = abilityName,
                x = startX + 100,
                y = startY + 13.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.label.moves".asTranslated().bold(),
                x = startX + 100,
                y = startY + 23.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            if (dto.moves.size != 4) {
                for (i in 0 until 4) {
                    drawScaledText(
                        context = context,
                        text = "?".text(),
                        x = startX + 100,
                        y = startY + 31.5 + i * 8,
                        scale = BattleOverlay.Companion.SCALE,
                        shadow = true,
                        centered = true
                    )
                }
            }
            else {
                val questionMarkText = "?".text()
                dto.moves.forEachIndexed { index, moveDTO ->
                    val move = moveDTO?.move ?: questionMarkText
                    drawScaledText(
                        context = context,
                        text = move.string.text(),
                        x = if (move == questionMarkText) startX + 100 else startX + 74,
                        y = startY + 31.5 + index * 8,
                        scale = BattleOverlay.Companion.SCALE,
                        shadow = true,
                        centered = move == questionMarkText
                    )

                    if (moveDTO != null) {
                        drawScaledTextJustifiedRight(
                            context = context,
                            text = moveDTO.timesUsed.toString().text(),
                            x = startX + 127.5,
                            y = startY + 31.5 + index * 8,
                            scale = BattleOverlay.Companion.SCALE,
                            shadow = true,
                        )
                    }
                }
            }

            if (dto.speed != null) {
                val speed = dto.speed
                val speedBoost = dto.buffs.get(Stats.SPEED) ?: 1.0
                val speedAfterBoost = (speed * speedBoost).toInt()
                drawScaledText(
                    context = context,
                    text = "cobblemon.stat.speed.name".asTranslated(),
                    x = startX + 100,
                    y = startY + 65.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = true
                )

                drawScaledText(
                    context = context,
                    text = speedAfterBoost.toString().text(),
                    x = startX + 100,
                    y = startY + 72.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = true
                )
            }
            else {
                drawScaledText(
                    context = context,
                    text = "cobblemon.battle.ui.label.speed_tier".asTranslated(),
                    x = startX + 100,
                    y = startY + 65.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = true
                )

                val speedBoost = dto.buffs.get(Stats.SPEED) ?: 1.0
                val speedTier = getSpeedRange(form, pokemon.level, speedBoost)

                drawScaledText(
                    context = context,
                    text = speedTier,
                    x = startX + 100,
                    y = startY + 72.5,
                    scale = BattleOverlay.Companion.SCALE,
                    shadow = true,
                    centered = true
                )
            }

            drawScaledText(
                context = context,
                text = "cobblemon.battle.ui.label.held_item".asTranslated(),
                x = startX + 40,
                y = startY + 99.5,
                scale = BattleOverlay.Companion.SCALE,
                shadow = true,
                centered = true
            )

            TypeIcon(
                x = startX + 100.5,
                y = startY + 88,
                type = form.primaryType,
                secondaryType = form.secondaryType,
                small = false,
                centeredX = true
            ).render(context)

            if (form.secondaryType != null) {
                blitk(
                    matrixStack = context.pose(),
                    texture = cobblemonResource("textures/gui/battle/type_spacer_double.png"),
                    x = (startX + 69.0) / 0.45,
                    y = (startY + 91.5) / 0.45,
                    height = 24,
                    width = 140,
                    textureHeight = 24,
                    textureWidth = 140,
                    scale = 0.45f
                )
            }
            else {
                blitk(
                    matrixStack = context.pose(),
                    texture = cobblemonResource("textures/gui/summary/type_spacer.png"),
                    x = (startX + 70.5) / 0.45,
                    y = (startY + 91.5) / 0.45,
                    height = 24,
                    width = 132,
                    textureHeight = 24,
                    textureWidth = 132,
                    scale = 0.45f
                )
            }

            val heldItem = dto.heldItem
            if (heldItem != null && !heldItem.isEmpty()) {
                context.pose().pushPose()
                context.pose().scale(0.95F, 0.95F, 0.95F)
                val itemX = (startX + 4) / 0.95
                val itemY = (startY + 91) / 0.95
                context.renderItem(heldItem, itemX.toInt(), itemY.toInt())
                context.pose().popPose()
            }
            else {
                blitk(
                    matrixStack = context.pose(),
                    texture = questionMarkIcon,
                    x = startX + 6,
                    y = startY + 92.5,
                    height = 11,
                    width = 10,
                )
            }
            context.pose().popPose()
        }

        @JvmOverloads
        fun drawCustomPosablePortrait(
            identifier: ResourceLocation,
            matrixStack: PoseStack,
            scale: Float = 13F,
            contextScale: Float = 1F,
            reversed: Boolean = false,
            state: PosableState,
            partialTicks: Float,
            limbSwing: Float = 0F,
            limbSwingAmount: Float = 0F,
            ageInTicks: Float = 0F,
            headYaw: Float = 0F,
            headPitch: Float = 0F,
            r: Float = 1F,
            g: Float = 1F,
            b: Float = 1F,
            a: Float = 1F,
            doQuirks: Boolean = true
        ) {
            RenderSystem.applyModelViewMatrix()
            matrixStack.pushPose()
            matrixStack.translate(0.0, PORTRAIT_DIAMETER.toDouble() + 2.0, 0.0)
            matrixStack.scale(scale, scale, -scale)
            matrixStack.translate(0.0, -PORTRAIT_DIAMETER / 18.0, 0.0)

            val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PORTRAIT);

            if (sprite == null) {
                val model = VaryingModelRepository.getPoser(identifier, state)
                state.currentModel = model
                val texture = VaryingModelRepository.getTexture(identifier, state)

                val context = RenderContext()
                model.context = context
                VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { context.put(RenderContext.TEXTURE, it) }
                context.put(RenderContext.SCALE, contextScale)
                context.put(RenderContext.SPECIES, identifier)
                context.put(RenderContext.ASPECTS, state.currentAspects)
                context.put(RenderContext.POSABLE_STATE, state)
                context.put(RenderContext.DO_QUIRKS, doQuirks)

                val renderType = RenderType.entityCutout(texture)

                val quaternion1 = Axis.YP.rotationDegrees(-32F * if (reversed) -1F else 1F)
                val quaternion2 = Axis.XP.rotationDegrees(5F)

                val originalPose = state.currentPose
                state.setPoseToFirstSuitable(PoseType.PORTRAIT)
                state.updatePartialTicks(partialTicks)
                model.applyAnimations(null, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
                originalPose?.let { state.setPose(it) }

                matrixStack.translate(
                    model.portraitTranslation.x * if (reversed) -1F else 1F,
                    model.portraitTranslation.y + 1.5 * model.portraitScale,
                    model.portraitTranslation.z - 4
                )
                matrixStack.scale(model.portraitScale, model.portraitScale, 1 / model.portraitScale)
                matrixStack.mulPose(quaternion1)
                matrixStack.mulPose(quaternion2)

                val light1 = Vector3f(0.2F, 1.0F, -1.0F)
                val light2 = Vector3f(0.1F, 0.0F, 8.0F)
                RenderSystem.setShaderLights(light1, light2)
                quaternion1.conjugate()

                val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
                val buffer = immediate.getBuffer(renderType)
                val packedLight = LightTexture.pack(11, 7)

                val colour = toHex(r, g, b, a)
                model.withLayerContext(immediate, state, VaryingModelRepository.getLayers(identifier, state)) {
                    model.render(context, matrixStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, colour)
                    immediate.endBatch()
                }

                model.setDefault()

                Lighting.setupFor3DItems()
            } else {
                renderSprite(matrixStack, sprite)
            }

            matrixStack.popPose()
        }

        private fun getSpeedRange(form: FormData, level: Int, speedBoost: Double): MutableComponent {
            val base = form.baseStats[Stats.SPEED] ?: 0
            val maxIV = 31
            val maxEV = 252
            val minNatureMod = 0.9
            val maxNatureMod = 1.1

            val minSpeed = getStat(base, level, 0, 0, minNatureMod) * speedBoost
            val maxSpeed = getStat(base, level, maxIV, maxEV, maxNatureMod) * speedBoost
            return "cobblemon.battle.ui.speed_tier".asTranslated(minSpeed.toInt(), maxSpeed.toInt())
        }

        private fun getStat(base: Int, level: Int, iv: Int, ev: Int, natureMod: Double): Int {
            return floor((floor(((2.0 * base + iv + floor(ev / 4.0)) * level) / 100) + 5) * natureMod).toInt()
        }

        private fun getMultiplierText(multiplier: Double): MutableComponent {
            val rounded = decimalFormat.format(multiplier)
            val str = "x$rounded"
            return when {
                multiplier > 1.0 -> str.green()
                multiplier < 1.0 -> str.red()
                else -> str.text()
            }
        }

        private fun getFormText(species: Species, form: FormData): MutableComponent {
            val speciesName = species.name
            val formName = form.name
            return if (formName == "Normal") speciesName.text() else "$speciesName-$formName".text()
        }
    }
}