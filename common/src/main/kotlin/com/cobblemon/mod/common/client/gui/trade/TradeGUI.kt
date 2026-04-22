/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.trade

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.ExitButton
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.MarkingsWidget
import com.cobblemon.mod.common.client.gui.summary.widgets.common.reformatNatureTextIfMinted
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.trade.ClientTrade
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket.TradeablePokemon
import com.cobblemon.mod.common.net.messages.server.trade.*
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.toAssetPath
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.MutableComponent
import net.minecraft.sounds.SoundEvent
import java.util.*

/**
 * Notes for Village:
 * When your chosen trade Pokémon is changed, [UpdateTradeOfferPacket].
 * When you change ready status, [ChangeTradeAcceptancePacket].
 * When you cancel the trade, just run this.close()
 */
class TradeGUI(
    val trade: ClientTrade,
    val traderId: UUID,
    val traderName: MutableComponent,
    val traderParty: MutableList<TradeablePokemon?>,
    val party: MutableList<TradeablePokemon?>
): Screen(lang("trade.gui.title")), CobblemonRenderable {

    companion object {
        const val BASE_WIDTH = 293
        const val BASE_HEIGHT = 212
        const val BASE_BACKGROUND_WIDTH = 157
        const val BASE_BACKGROUND_HEIGHT = 85
        const val PARTY_SLOT_PADDING = 4
        const val PORTRAIT_SIZE = 78
        const val PORTRAIT_SCALE = 2F
        const val PORTRAIT_SCALE_MULTIPLIER = 0.4F
        const val PORTRAIT_MODEL_OFFSET_Y = -8.0
        const val TYPE_SPACER_WIDTH = 134
        const val TYPE_SPACER_HEIGHT = 12
        const val TRADE_READY_WIDTH = 28
        const val TRADE_READY_HEIGHT = 6
        const val TRADE_READY_TOP_HEIGHT = 5
        const val READY_PROGRESS_LIMIT = 6
        const val TRADE_ANIMATION_WIDTH = 114
        const val TRADE_ANIMATION_HEIGHT = 133
        const val TRADE_ANIMATION_ARROWS_WIDTH = 69
        const val TRADE_ANIMATION_FRAMES = 13
        const val TRADE_ANIMATION_ARROWS_FRAMES = 18
        const val MODEL_SCALE_BUFFER_FRAMES = 3
        const val REVERSE_ANIMATION_TICK = TRADE_ANIMATION_FRAMES + MODEL_SCALE_BUFFER_FRAMES + TRADE_ANIMATION_ARROWS_FRAMES
        const val MAX_TRADE_PROGRESS = REVERSE_ANIMATION_TICK + TRADE_ANIMATION_FRAMES + MODEL_SCALE_BUFFER_FRAMES
        const val CRY_BUFFER = 7

        const val SCALE = 0.5F

        private val baseResource = cobblemonResource("textures/gui/trade/trade_base.png")
        private val baseBackgroundResource = cobblemonResource("textures/gui/trade/trade_background.png")
        private val typeSpacerResource = cobblemonResource("textures/gui/trade/type_spacer.png")
        private val typeSpacerSingleResource = cobblemonResource("textures/gui/trade/type_spacer_single.png")
        private val typeSpacerDoubleResource = cobblemonResource("textures/gui/trade/type_spacer_double.png")
        private val tradeReadyResource = cobblemonResource("textures/gui/trade/trade_ready.png")
        private val tradeReadyTopResource = cobblemonResource("textures/gui/trade/trade_ready_top.png")
        private val opposingTradeReadyResource = cobblemonResource("textures/gui/trade/trade_ready_opposing.png")
        private val opposingTradeReadyTopResource = cobblemonResource("textures/gui/trade/trade_ready_top_opposing.png")
        private val tradeAnimationResource = cobblemonResource("textures/gui/trade/trade_animation.png")
        private val tradeAnimationArrowsResource = cobblemonResource("textures/gui/trade/trade_animation_arrows.png")
    }

    private var offeredPokemonModels: MutableList<ModelWidget> = mutableListOf()
    private var opposingOfferedPokemonModels: MutableList<ModelWidget> = mutableListOf()

    private lateinit var offeredPokemonMarkings: MarkingsWidget
    private lateinit var opposingOfferedPokemonMarkings: MarkingsWidget

    var selectedPartyIndices = mutableListOf<Int>()
    var offeredPokemons: MutableList<Pokemon> = mutableListOf()
    var opposingPokemons: MutableList<Pokemon> = mutableListOf()

    // For backwards compatibility where single is referenced in rendering text info
    var offeredPokemon: Pokemon? = null
    var opposingOfferedPokemon: Pokemon? = null

    var myGridMode = true
    var opposingGridMode = true

    var ticksElapsed = 0
    var protectiveTicks = 0
    var selectPointerOffsetY = 0
    var readyProgress = 0
    var tradeAnimationProgress = 0
    var selectPointerOffsetIncrement = false
    var tradeProcessing = false
    var isTradeInitiator = false

    var tradeSoundInstance: SoundInstance? = null

    init {
        trade.cancelEmitter.subscribe {
            cancelTradeSound()
            super.onClose()
        }

        trade.completedEmitter.subscribe {
            val (pokemonIds1, pokemonIds2) = it
            val myTradedPokemons = pokemonIds1.mapNotNull { id -> party.find { it?.pokemonId == id } }
            val theirTradedPokemons = pokemonIds2.mapNotNull { id -> traderParty.find { it?.pokemonId == id } }
            if (myTradedPokemons.isEmpty() || theirTradedPokemons.isEmpty()) {
                CobblemonNetwork.sendToServer(CancelTradePacket())
                return@subscribe onClose()
            }

            myTradedPokemons.forEach { pk ->
                val idx = party.indexOf(pk)
                if (idx != -1) party[idx] = null
            }
            theirTradedPokemons.forEach { pk ->
                val idx = traderParty.indexOf(pk)
                if (idx != -1) traderParty[idx] = null
            }

            val newSelectedIndices = mutableListOf<Int>()
            theirTradedPokemons.forEach { pk ->
                val emptyIdx = party.indexOf(null)
                if (emptyIdx != -1) {
                    party[emptyIdx] = pk
                }
            }
            myTradedPokemons.forEach { pk ->
                val emptyIdx = traderParty.indexOf(null)
                if (emptyIdx != -1) traderParty[emptyIdx] = pk
            }

            val myFirstNullIdx = party.indexOf(null)
            val myNeedsReorder = myFirstNullIdx != -1 && party.drop(myFirstNullIdx).any { it != null }
            if (myNeedsReorder) {
                val myNonNulls = party.filterNotNull()
                party.clear()
                party.addAll(myNonNulls)
                while (party.size < 6) party.add(null)
            }

            val theirFirstNullIdx = traderParty.indexOf(null)
            val theirNeedsReorder = theirFirstNullIdx != -1 && traderParty.drop(theirFirstNullIdx).any { it != null }
            if (theirNeedsReorder) {
                val theirNonNulls = traderParty.filterNotNull()
                traderParty.clear()
                traderParty.addAll(theirNonNulls)
                while (traderParty.size < 6) traderParty.add(null)
            }

            theirTradedPokemons.forEach { pk ->
                val idx = party.indexOf(pk)
                if (idx != -1) newSelectedIndices.add(idx)
            }

            trade.oppositeAcceptedMyOffer.set(false)
            selectedPartyIndices.clear()
            selectedPartyIndices.addAll(newSelectedIndices)

            val newOffers = selectedPartyIndices.mapNotNull { idx ->
                party[idx]?.let { it.pokemonId to PartyPosition(idx) }
            }
            CobblemonNetwork.sendToServer(UpdateTradeOfferPacket(newOffers))

            rebuildWidgets()
        }

        trade.oppositeOffer.subscribe { newOffer: List<Pokemon> ->
            setOfferedPokemon(pokemonList = newOffer, isOpposing = true)
        }

        trade.myOffer.subscribe { myOffer: List<Pokemon> ->
            setOfferedPokemon(pokemonList = myOffer)
        }

        trade.oppositeAcceptedMyOffer.subscribe {
            ticksElapsed = 0
            readyProgress = 0
        }

        trade.tradeProcessing.subscribe {
            val (processStatus, isInitiator) = it
            tradeProcessing = processStatus
            isTradeInitiator = isInitiator
            tradeAnimationProgress = 0
        }
    }

    override fun init() {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2

        // Exit Button
        this.addRenderableWidget(
            ExitButton(pX = x + 265, pY = y + 6) {
                playSound(CobblemonSounds.GUI_CLICK)
                onClose()
                Minecraft.getInstance().setScreen(null)
            }
        )

        // Trade Button
        this.addRenderableWidget(
            TradeButton(
                x = x + 120,
                y = y + 119,
                parent = this,
                onPress = {
                    if (offeredPokemon != null && opposingOfferedPokemon != null && protectiveTicks <= 0 && !tradeProcessing) {
                        ticksElapsed = 0
                        readyProgress = 0
                        CobblemonNetwork.sendToServer(ChangeTradeAcceptancePacket(opposingOfferedPokemon!!.uuid, !trade.acceptedOppositeOffer))
                        playSound(CobblemonSounds.GUI_CLICK)
                    }
                }
            )
        )

        // Party
        for (partyIndex in 0..5) {
            var slotX = x + 9
            var slotY = y + 38

            if (partyIndex > 0) {
                val isEven = partyIndex % 2 == 0
                val offsetIndex = (partyIndex - (if (isEven) 0 else 1)) / 2
                val offsetX = if (isEven) 0 else (PartySlot.SIZE + PARTY_SLOT_PADDING)
                val offsetY = if (isEven) 0 else -8

                slotX += offsetX
                slotY += ((PartySlot.SIZE + PARTY_SLOT_PADDING) * offsetIndex) + offsetY
            }

            val pokemon = party[partyIndex]
            PartySlot(
                x = slotX,
                y = slotY,
                pokemon = pokemon,
                parent = this,
                onPress = {
                    if (!trade.acceptedOppositeOffer && !tradeProcessing && pokemon != null) {
                        if (selectedPartyIndices.contains(partyIndex)) {
                            selectedPartyIndices.remove(partyIndex)
                        } else if (selectedPartyIndices.size < 6) {
                            selectedPartyIndices.add(partyIndex)
                        }
                        val newOffers = selectedPartyIndices.mapNotNull { idx ->
                            party[idx]?.let { it.pokemonId to PartyPosition(idx) }
                        }
                        CobblemonNetwork.sendToServer(UpdateTradeOfferPacket(newOffers))
                        playSound(CobblemonSounds.GUI_CLICK)
                    }
                }
            ).also { widget -> addRenderableWidget(widget) }
        }

        // Opposing Party
        for (partyIndex in 0..5) {
            var slotX = x + 230
            var slotY = y + 30

            if (partyIndex > 0) {
                val isEven = partyIndex % 2 == 0
                val offsetIndex = (partyIndex - (if (isEven) 0 else 1)) / 2
                val offsetX = if (isEven) 0 else (PartySlot.SIZE + PARTY_SLOT_PADDING)
                val offsetY = if (isEven) 0 else 8

                slotX += offsetX
                slotY += ((PartySlot.SIZE + PARTY_SLOT_PADDING) * offsetIndex) + offsetY
            }

            PartySlot(
                x = slotX,
                y = slotY,
                pokemon = traderParty[partyIndex],
                parent = this,
                isOpposing = true,
                onPress = {}
            ).also { widget -> addRenderableWidget(widget) }
        }

        setOfferedPokemon(pokemonList = offeredPokemons.toList(), isOpposing = false)
        setOfferedPokemon(pokemonList = opposingPokemons.toList(), isOpposing = true)

        offeredPokemonMarkings = MarkingsWidget(x + 74.5, y + 21, offeredPokemon, false)
        opposingOfferedPokemonMarkings = MarkingsWidget(x + 177.5, y + 21, opposingOfferedPokemon, false)
        addRenderableWidget(offeredPokemonMarkings)
        addRenderableWidget(opposingOfferedPokemonMarkings)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2
        val matrices = context.pose()

        // Render Background Resource
        val backgroundX = x + 68
        val backgroundY = y + 23
        blitk(
            matrixStack = matrices,
            texture = baseBackgroundResource,
            x = backgroundX,
            y = backgroundY,
            width = BASE_BACKGROUND_WIDTH,
            height = BASE_BACKGROUND_HEIGHT
        )

        if (
            (tradeAnimationProgress in MODEL_SCALE_BUFFER_FRAMES until(TRADE_ANIMATION_FRAMES + MODEL_SCALE_BUFFER_FRAMES)) ||
            (tradeAnimationProgress in (REVERSE_ANIMATION_TICK..(REVERSE_ANIMATION_TICK + TRADE_ANIMATION_FRAMES)).drop(1))
        ) {
            val frameOffset = (
                if (tradeAnimationProgress > REVERSE_ANIMATION_TICK)
                    (REVERSE_ANIMATION_TICK + TRADE_ANIMATION_FRAMES - tradeAnimationProgress)
                else (tradeAnimationProgress - MODEL_SCALE_BUFFER_FRAMES)
                ) * TRADE_ANIMATION_HEIGHT
            blitk(
                matrixStack = matrices,
                texture = tradeAnimationResource,
                x = (x + 80.5) / SCALE,
                y = (y + 31) / SCALE,
                width = TRADE_ANIMATION_WIDTH,
                height = TRADE_ANIMATION_HEIGHT,
                vOffset = frameOffset,
                textureHeight = TRADE_ANIMATION_HEIGHT * TRADE_ANIMATION_FRAMES,
                scale = SCALE
            )

            blitk(
                matrixStack = matrices,
                texture = tradeAnimationResource,
                x = (x + 155.5) / SCALE,
                y = (y + 31) / SCALE,
                width = TRADE_ANIMATION_WIDTH,
                height = TRADE_ANIMATION_HEIGHT,
                vOffset = frameOffset,
                textureHeight = TRADE_ANIMATION_HEIGHT * TRADE_ANIMATION_FRAMES,
                scale = SCALE
            )
        }

        // Render Model Portraits
        var scale = PORTRAIT_SCALE
        var scaleOffsetY = PORTRAIT_MODEL_OFFSET_Y
        var isShrinking = false
        if (tradeProcessing) {
            val scaleInterval = if (tradeAnimationProgress > REVERSE_ANIMATION_TICK) (MAX_TRADE_PROGRESS - tradeAnimationProgress) else tradeAnimationProgress
            scale = (PORTRAIT_SCALE - (scaleInterval * PORTRAIT_SCALE_MULTIPLIER)).coerceIn(0F, PORTRAIT_SCALE)
            scaleOffsetY = (45.0 - (45.0 / PORTRAIT_SCALE) * scale) + PORTRAIT_MODEL_OFFSET_Y
            isShrinking = true
        }
        val shrinkPercentage = if (isShrinking) 1.0F - (scale / PORTRAIT_SCALE) else 0.0F
        val myCenterXDest = x + 70 + (PORTRAIT_SIZE / 2.0)
        val myCenterYDest = y + 30 + (PORTRAIT_SIZE / 2.0)
        val opCenterXDest = x + 145 + (PORTRAIT_SIZE / 2.0)
        val opCenterYDest = y + 30 + (PORTRAIT_SIZE / 2.0)

        if (scale > 0F) {
            context.enableScissor(backgroundX, backgroundY, backgroundX + BASE_BACKGROUND_WIDTH, backgroundY +  BASE_BACKGROUND_HEIGHT)

            offeredPokemonModels.forEachIndexed { i, model ->
                val posAndScale = getModelPositionAndScale(i, offeredPokemons.size, x + 70, y + 30)
                val originalScale = posAndScale.second
                model.baseScale = if (isShrinking) (originalScale / PORTRAIT_SCALE) * scale else originalScale
                model.offsetY = if (isShrinking) scaleOffsetY else PORTRAIT_MODEL_OFFSET_Y

                val startCenterX = posAndScale.first.first + (PORTRAIT_SIZE / 2.0)
                val startCenterY = posAndScale.first.second + (PORTRAIT_SIZE / 2.0)

                context.pose().pushPose()
                context.pose().translate((myCenterXDest - startCenterX) * shrinkPercentage, (myCenterYDest - startCenterY) * shrinkPercentage, 0.0)

                model.render(context, mouseX, mouseY, delta)

                context.pose().popPose()
            }

            opposingOfferedPokemonModels.forEachIndexed { i, model ->
                val posAndScale = getModelPositionAndScale(i, opposingPokemons.size, x + 145, y + 30)
                val originalScale = posAndScale.second
                model.baseScale = if (isShrinking) (originalScale / PORTRAIT_SCALE) * scale else originalScale
                model.offsetY = if (isShrinking) scaleOffsetY else PORTRAIT_MODEL_OFFSET_Y

                val startCenterX = posAndScale.first.first + (PORTRAIT_SIZE / 2.0)
                val startCenterY = posAndScale.first.second + (PORTRAIT_SIZE / 2.0)

                context.pose().pushPose()
                context.pose().translate((opCenterXDest - startCenterX) * shrinkPercentage, (opCenterYDest - startCenterY) * shrinkPercentage, 0.0)

                model.render(context, mouseX, mouseY, delta)

                context.pose().popPose()
            }

            context.disableScissor()
        }

        // Render Base Resource
        blitk(
            matrixStack = matrices,
            texture = baseResource,
            x = x, y = y,
            width = BASE_WIDTH,
            height = BASE_HEIGHT
        )

        renderInfoLabels(context, x, y)

        children().filterIsInstance<PartySlot>().forEach { slot ->
            if (slot.isHovered(mouseX, mouseY) && slot.hasSelected()) {
                val hoveredId = slot.pokemon?.pokemonId
                if (slot.isOpposing) {
                    opposingPokemons.find { it.uuid == hoveredId }?.let { opposingOfferedPokemon = it }
                } else {
                    offeredPokemons.find { it.uuid == hoveredId }?.let { offeredPokemon = it }
                }
            }
        }

        val currentOffered = offeredPokemon
        if (currentOffered != null) {
            renderPokemonInfo(currentOffered, false, context, x, y, mouseX, mouseY)
            if (::offeredPokemonMarkings.isInitialized) {
                offeredPokemonMarkings.visible = true
                if (offeredPokemonMarkings.pokemon != currentOffered) offeredPokemonMarkings.setActivePokemon(currentOffered)
            }
        } else {
            if (::offeredPokemonMarkings.isInitialized) offeredPokemonMarkings.visible = false
        }

        val currentOpposing = opposingOfferedPokemon
        if (currentOpposing != null) {
            renderPokemonInfo(currentOpposing, true, context, x, y, mouseX, mouseY)
            if (::opposingOfferedPokemonMarkings.isInitialized) {
                opposingOfferedPokemonMarkings.visible = true
                if (opposingOfferedPokemonMarkings.pokemon != currentOpposing) opposingOfferedPokemonMarkings.setActivePokemon(currentOpposing)
            }
        } else {
            if (::opposingOfferedPokemonMarkings.isInitialized) opposingOfferedPokemonMarkings.visible = false
        }

        if (trade.acceptedOppositeOffer) {
            blitk(
                matrixStack = matrices,
                texture = tradeReadyResource,
                x = x + 85,
                y = y + 126,
                width = TRADE_READY_WIDTH,
                height = TRADE_READY_HEIGHT,
                vOffset = TRADE_READY_HEIGHT * readyProgress,
                textureHeight = TRADE_READY_HEIGHT * READY_PROGRESS_LIMIT
            )

            if (!tradeProcessing) {
                blitk(
                    matrixStack = matrices,
                    texture = tradeReadyTopResource,
                    x = x + 112,
                    y = y + 2,
                    width = TRADE_READY_WIDTH,
                    height = TRADE_READY_TOP_HEIGHT,
                    vOffset = TRADE_READY_TOP_HEIGHT * readyProgress,
                    textureHeight = TRADE_READY_TOP_HEIGHT * READY_PROGRESS_LIMIT
                )
            }
        }

        if (trade.oppositeAcceptedMyOffer.get()) {
            blitk(
                matrixStack = matrices,
                texture = opposingTradeReadyResource,
                x = x + 180,
                y = y + 126,
                width = TRADE_READY_WIDTH,
                height = TRADE_READY_HEIGHT,
                vOffset = TRADE_READY_HEIGHT * readyProgress,
                textureHeight = TRADE_READY_HEIGHT * READY_PROGRESS_LIMIT
            )

            if (!tradeProcessing) {
                blitk(
                    matrixStack = matrices,
                    texture = opposingTradeReadyTopResource,
                    x = x + 153,
                    y = y + 2,
                    width = TRADE_READY_WIDTH,
                    height = TRADE_READY_TOP_HEIGHT,
                    vOffset = TRADE_READY_TOP_HEIGHT * readyProgress,
                    textureHeight = TRADE_READY_TOP_HEIGHT * READY_PROGRESS_LIMIT
                )
            }
        }

        // Trade animation arrows
        if (tradeAnimationProgress >= (TRADE_ANIMATION_FRAMES + MODEL_SCALE_BUFFER_FRAMES) && tradeAnimationProgress < REVERSE_ANIMATION_TICK) {
            blitk(
                matrixStack = matrices,
                texture = tradeAnimationArrowsResource,
                x = x + 112,
                y = y + 2,
                width = TRADE_ANIMATION_ARROWS_WIDTH,
                height = TRADE_READY_TOP_HEIGHT,
                vOffset = (((tradeAnimationProgress - MODEL_SCALE_BUFFER_FRAMES) % TRADE_ANIMATION_ARROWS_FRAMES) - MODEL_SCALE_BUFFER_FRAMES) * TRADE_READY_TOP_HEIGHT,
                textureHeight = TRADE_READY_TOP_HEIGHT * TRADE_ANIMATION_ARROWS_FRAMES
            )
        }

        // Render usernames
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Minecraft.getInstance().user.name.text().bold(),
            x = x + 13,
            y = y - 10.5,
            shadow = true
        )

        drawScaledTextJustifiedRight(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = traderName.bold(),
            x = x + 280,
            y = y - 10.5,
            shadow = true
        )

        super.render(context, mouseX, mouseY, delta)

        val ttOffered = offeredPokemon
        if (ttOffered != null) {
            // Item Tooltip
            if (!ttOffered.heldItemNoCopy().isEmpty) {
                val itemX = x + 50
                val itemY = y + 125
                val itemHovered = mouseX.toFloat() in (itemX.toFloat()..(itemX.toFloat() + 16)) && mouseY.toFloat() in (itemY.toFloat()..(itemY.toFloat() + 16))
                if (itemHovered) context.renderTooltip(Minecraft.getInstance().font, ttOffered.heldItemNoCopy(), mouseX, mouseY)
            }
        }

        val ttOpposing = opposingOfferedPokemon
        if (ttOpposing != null) {
            if (!ttOpposing.heldItemNoCopy().isEmpty) {
                val itemX = x + 227
                val itemY = y + 125
                val itemHovered = mouseX.toFloat() in (itemX.toFloat()..(itemX.toFloat() + 16)) && mouseY.toFloat() in (itemY.toFloat()..(itemY.toFloat() + 16))
                if (itemHovered) context.renderTooltip(Minecraft.getInstance().font, ttOpposing.heldItemNoCopy(), mouseX, mouseY)
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (minecraft?.options?.keyInventory?.matches(keyCode, scanCode) == true) {
            CancelTradePacket().sendToServer()
            cancelTradeSound()
            Minecraft.getInstance().setScreen(null)
            return true
        }

        when (keyCode) {
            InputConstants.KEY_ESCAPE -> {
                CancelTradePacket().sendToServer()
                cancelTradeSound()
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun tick() {
        ticksElapsed++
        if (protectiveTicks > 0) protectiveTicks--
        if (tradeProcessing) {
            if (tradeAnimationProgress == 0) tradeSoundInstance = playSound(CobblemonSounds.GUI_TRADE)

            tradeAnimationProgress++

            if (offeredPokemon != null && opposingOfferedPokemon != null
                && tradeAnimationProgress == REVERSE_ANIMATION_TICK
                && isTradeInitiator
            ) {
                CobblemonNetwork.sendToServer(PerformTradePacket(opposingOfferedPokemon!!.uuid))
            }

            if (tradeAnimationProgress == (MAX_TRADE_PROGRESS + CRY_BUFFER)) {
                offeredPokemonModels.forEach {
                    it.state.activeAnimations.clear()
                    it.state.addFirstAnimation(setOf("cry"))
                }
                opposingOfferedPokemonModels.forEach {
                    it.state.activeAnimations.clear()
                    it.state.addFirstAnimation(setOf("cry"))
                }
            }

            if (tradeAnimationProgress > (MAX_TRADE_PROGRESS + CRY_BUFFER)) {
                trade.tradeProcessing.set(Pair(false, false))
                ticksElapsed = 0
                readyProgress = 0
                tradeAnimationProgress = 0
                tradeSoundInstance = null
            }
        }

        // Calculate select pointer offset
        val delayFactor = 3
        if (ticksElapsed % (2 * delayFactor) == 0) selectPointerOffsetIncrement = !selectPointerOffsetIncrement
        if (ticksElapsed % delayFactor == 0) selectPointerOffsetY += if (selectPointerOffsetIncrement) 1 else -1

        if (ticksElapsed % 6 == 0) readyProgress = if (readyProgress == READY_PROGRESS_LIMIT) 0 else readyProgress + 1
    }

    override fun onClose() {
        CobblemonNetwork.sendToServer(CancelTradePacket())
        cancelTradeSound()
        super.onClose()
    }

    override fun renderBlurredBackground(delta: Float) {}

    override fun renderMenuBackground(context: GuiGraphics) {}

    private fun setOfferedPokemon(pokemonList: List<Pokemon>, isOpposing: Boolean = false) {
        protectiveTicks = 20
        if (isOpposing) {
            val wasEmpty = opposingPokemons.isEmpty()
            val currentViewedUuid = if (opposingGridMode) null else opposingOfferedPokemon?.uuid
            opposingPokemons.clear()
            opposingPokemons.addAll(pokemonList)

            val matchingPokemon = opposingPokemons.find { it.uuid == currentViewedUuid }

            if (matchingPokemon != null) {
                opposingOfferedPokemon = matchingPokemon
                if (::opposingOfferedPokemonMarkings.isInitialized) opposingOfferedPokemonMarkings.setActivePokemon(opposingOfferedPokemon)
            } else {
                opposingOfferedPokemon = opposingPokemons.lastOrNull()
                if (::opposingOfferedPokemonMarkings.isInitialized) opposingOfferedPokemonMarkings.setActivePokemon(opposingOfferedPokemon)
                if (wasEmpty && opposingOfferedPokemon != null) {
                    opposingGridMode = false
                } else if (!opposingGridMode) {
                    opposingGridMode = true
                }
            }
            trade.acceptedOppositeOffer = false
        } else {
            val wasEmpty = offeredPokemons.isEmpty()
            val currentViewedUuid = if (myGridMode) null else offeredPokemon?.uuid
            offeredPokemons.clear()
            offeredPokemons.addAll(pokemonList)

            val matchingPokemon = offeredPokemons.find { it.uuid == currentViewedUuid }

            if (matchingPokemon != null) {
                offeredPokemon = matchingPokemon
                if (::offeredPokemonMarkings.isInitialized) offeredPokemonMarkings.setActivePokemon(offeredPokemon)
            } else {
                offeredPokemon = offeredPokemons.lastOrNull()
                if (::offeredPokemonMarkings.isInitialized) offeredPokemonMarkings.setActivePokemon(offeredPokemon)
                if (wasEmpty && offeredPokemon != null) {
                    myGridMode = false
                } else if (!myGridMode) {
                    myGridMode = true
                }
            }
        }
        rebuildSelectedModels(isOpposing)
    }

    private fun rebuildSelectedModels(isOpposing: Boolean) {
        val x = (width - BASE_WIDTH) / 2
        val y = (height - BASE_HEIGHT) / 2

        if (isOpposing) {
            opposingOfferedPokemonModels.clear()
            val total = opposingPokemons.size
            opposingPokemons.forEachIndexed { i, pk ->
                val (pos, currentScale) = getModelPositionAndScale(i, total, x + 145, y + 30)
                opposingOfferedPokemonModels.add(ModelWidget(
                    pX = pos.first, pY = pos.second,
                    pWidth = PORTRAIT_SIZE, pHeight = PORTRAIT_SIZE,
                    pokemon = pk.asRenderablePokemon(),
                    baseScale = currentScale,
                    rotationY = 35F,
                    offsetY = PORTRAIT_MODEL_OFFSET_Y
                ))
            }
        } else {
            offeredPokemonModels.clear()
            val total = offeredPokemons.size
            offeredPokemons.forEachIndexed { i, pk ->
                val (pos, currentScale) = getModelPositionAndScale(i, total, x + 70, y + 30)
                offeredPokemonModels.add(ModelWidget(
                    pX = pos.first, pY = pos.second,
                    pWidth = PORTRAIT_SIZE, pHeight = PORTRAIT_SIZE,
                    pokemon = pk.asRenderablePokemon(),
                    baseScale = currentScale,
                    rotationY = 325F,
                    offsetY = PORTRAIT_MODEL_OFFSET_Y
                ))
            }
        }
    }

    private fun getModelPositionAndScale(index: Int, total: Int, baseX: Int, baseY: Int): Pair<Pair<Int, Int>, Float> {
        val scale = when (total) {
            1 -> PORTRAIT_SCALE
            2 -> PORTRAIT_SCALE * 0.75f
            3 -> PORTRAIT_SCALE * 0.65f
            4 -> PORTRAIT_SCALE * 0.55f
            else -> PORTRAIT_SCALE * 0.5f
        }

        if (total <= 1) {
            return Pair(Pair(baseX, baseY), scale)
        }

        val rowLength1 = when (total) {
            2, 3, 4 -> 2
            else -> 3
        }
        val rowLength2 = total - rowLength1

        val isBottomRow = index >= rowLength1
        val row = if (isBottomRow) 1 else 0
        val col = if (isBottomRow) index - rowLength1 else index
        val currentRowLength = if (isBottomRow) rowLength2 else rowLength1

        val colSpacing = when (total) {
            2 -> 35
            3 -> 30
            4 -> 25
            else -> 23
        }
        val rowSpacing = when (total) {
            2, 3 -> 30
            4 -> 28
            else -> 25
        }

        val totalWidth = (currentRowLength - 1) * colSpacing
        val startX = baseX - (totalWidth / 2)

        val totalHeight = if (rowLength2 > 0) rowSpacing else 0
        var startY = baseY + 12 - (totalHeight / 2)

        if (total >= 4) {
            startY += 8
        }

        val posX = startX + (col * colSpacing)
        val posY = startY + (row * rowSpacing)

        return Pair(Pair(posX, posY), scale)
    }

    private fun playSound(soundEvent: SoundEvent): SoundInstance {
        val soundInstance = SimpleSoundInstance.forUI(soundEvent, 1.0F)
        Minecraft.getInstance().soundManager.play(soundInstance)
        return soundInstance
    }

    private fun cancelTradeSound() {
        if (tradeSoundInstance != null) Minecraft.getInstance().soundManager.stop(tradeSoundInstance!!)
    }

    public fun renderPokemonInfo(pokemon: Pokemon?, isOpposing: Boolean, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        if (pokemon != null) {
            val matrices = context.pose()
            // Level
            val levelXOffset = if (isOpposing) 117 else 0
            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = lang("ui.lv").bold(),
                x = x + 76 + levelXOffset,
                y = y + 1.5,
                shadow = true
            )

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = pokemon.level.toString().text().bold(),
                x = x + 89 + levelXOffset,
                y = y + 1.5,
                shadow = true
            )

            // Poké Ball
            val nameXOffset = if (isOpposing) 75 else 0
            val ballResource = pokemon.caughtBall.name.toAssetPath("textures/item/poke_balls/", ".png")
            blitk(
                matrixStack = context.pose(),
                texture = ballResource,
                x = (x + 73.5 + nameXOffset) / SCALE,
                y = (y + 12) / SCALE,
                width = 16,
                height = 16,
                scale = SCALE
            )

            drawScaledText(
                context = context,
                font = CobblemonResources.DEFAULT_LARGE,
                text = pokemon.getDisplayName().bold(),
                x = x + 82 + nameXOffset,
                y = y + 11.5,
                shadow = true
            )

            if (pokemon.gender != Gender.GENDERLESS) {
                val isMale = pokemon.gender == Gender.MALE
                val textSymbol = if (isMale) "♂".text().bold() else "♀".text().bold()
                drawScaledText(
                    context = context,
                    font = CobblemonResources.DEFAULT_LARGE,
                    text = textSymbol,
                    x = x + 139 + nameXOffset,
                    y = y + 11.5,
                    colour = if (isMale) 0x32CBFF else 0xFC5454,
                    shadow = true
                )
            }

            // Held Item
            val heldItem = pokemon.heldItemNoCopy()
            val itemX = x + (if (isOpposing) 227 else 50)
            val itemY = y + 125
            if (!heldItem.isEmpty) {
                val textRenderer = Minecraft.getInstance().font
                context.renderItem(heldItem, itemX, itemY)
                context.renderItemDecorations(textRenderer, heldItem, itemX, itemY)
            }

            // Shiny Icon
            if (pokemon.shiny) {
                blitk(
                    matrixStack = matrices,
                    texture = Summary.iconShinyResource,
                    x = (x + (if (isOpposing) 213.5 else 71.5)) / SCALE,
                    y = (y + 33.5) / SCALE,
                    width = 16,
                    height = 16,
                    scale = SCALE
                )
            }

            blitk(
                matrixStack = matrices,
                texture = if (pokemon.secondaryType != null) typeSpacerDoubleResource else typeSpacerSingleResource,
                x = (x + (if (isOpposing) 153 else 73)) / SCALE,
                y = (y + 113.5) / SCALE,
                width = TYPE_SPACER_WIDTH,
                height = TYPE_SPACER_HEIGHT,
                vOffset = if (isOpposing) TYPE_SPACER_HEIGHT else 0,
                textureHeight = TYPE_SPACER_HEIGHT * 2,
                scale = SCALE
            )

            TypeIcon(
                x = x + (if (isOpposing) 187 else 106),
                y = y + 112,
                type = pokemon.primaryType,
                secondaryType = pokemon.secondaryType,
                doubleCenteredOffset = 5F,
                secondaryOffset = 10F,
                small = true,
                centeredX = true
            ).render(context)

            val labelXOffset = if (isOpposing) 77 else 0

            // Nature
            val natureText = reformatNatureTextIfMinted(pokemon)
            drawScaledText(
                context = context,
                text = natureText,
                x = x + 108 + labelXOffset,
                y = y + 146.5,
                centered = true,
                shadow = true,
                scale = SCALE,
                pMouseX = mouseX,
                pMouseY = mouseY
            )

            // Ability
            drawScaledText(
                context = context,
                text = pokemon.ability.displayName.asTranslated(),
                x = x + 108 + labelXOffset,
                y = y + 163.5,
                centered = true,
                shadow = true,
                scale = SCALE
            )

            // Moves
            val moves = pokemon.moveSet.getMoves()
            for (i in moves.indices) {
                drawScaledText(
                    context = context,
                    text = moves[i].displayName,
                    x = x + 108 + labelXOffset,
                    y = y + 180.5 + (7 * i),
                    centered = true,
                    shadow = true,
                    scale = SCALE
                )
            }

            // IVs
            val ivXOffset = if (isOpposing) 205 else -13
            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.HP).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 155.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.ATTACK).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 163.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.DEFENCE).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 171.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.SPECIAL_ATTACK).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 179.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.SPECIAL_DEFENCE).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 187.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.ivs.getOrDefault(Stats.SPEED).toString().text(),
                x = x + 60 + ivXOffset,
                y = y + 195.5,
                scale = SCALE,
                centered = true
            )

            // EVs
            val evXOffset = if (isOpposing) 221 else 3
            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.HP).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 155.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.ATTACK).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 163.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.DEFENCE).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 171.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.SPECIAL_ATTACK).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 179.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.SPECIAL_DEFENCE).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 187.5,
                scale = SCALE,
                centered = true
            )

            drawScaledText(
                context = context,
                text = pokemon.evs.getOrDefault(Stats.SPEED).toString().text(),
                x = x + 60 + evXOffset,
                y = y + 195.5,
                scale = SCALE,
                centered = true
            )
        } else {
            blitk(
                matrixStack = context.pose(),
                texture = typeSpacerResource,
                x = (x + (if (isOpposing) 153 else 73)) / SCALE,
                y = (y + 113.5) / SCALE,
                width = TYPE_SPACER_WIDTH,
                height = TYPE_SPACER_HEIGHT,
                vOffset = if (isOpposing) TYPE_SPACER_HEIGHT else 0,
                textureHeight = TYPE_SPACER_HEIGHT * 2,
                scale = SCALE
            )
        }
    }

    private fun renderInfoLabels(context: GuiGraphics, x: Int, y: Int) {
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.party").bold(),
            x = x + 25.5,
            y = y + 7,
            centered = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.info.nature").bold(),
            x = x + 108,
            y = y + 139.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.info.ability").bold(),
            x = x + 108,
            y = y + 156.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.moves").bold(),
            x = x + 108,
            y = y + 173.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("held_item"),
            x = x + 22.5,
            y = y + 135.5,
            scale = SCALE,
            centered = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.ivs").bold(),
            x = x + 47,
            y = y + 147.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.evs").bold(),
            x = x + 62.5,
            y = y + 147.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.hp"),
            x = x + 9.5,
            y = y + 155.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.atk"),
            x = x + 9.5,
            y = y + 163.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.def"),
            x = x + 9.5,
            y = y + 171.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.sp_atk"),
            x = x + 9.5,
            y = y + 179.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.sp_def"),
            x = x + 9.5,
            y = y + 187.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.speed"),
            x = x + 9.5,
            y = y + 195.5,
            scale = SCALE
        )

        // Opposing
        drawScaledText(
            context = context,
            text = lang("ui.info.nature").bold(),
            x = x + 185,
            y = y + 139.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.info.ability").bold(),
            x = x + 185,
            y = y + 156.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.moves").bold(),
            x = x + 185,
            y = y + 173.5,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("held_item"),
            x = x + 270.5,
            y = y + 135.5,
            scale = SCALE,
            centered = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.ivs").bold(),
            x = x + 265,
            y = y + 148,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.evs").bold(),
            x = x + 280.5,
            y = y + 148,
            centered = true,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.hp"),
            x = x + 227.5,
            y = y + 155.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.atk"),
            x = x + 227.5,
            y = y + 163.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.def"),
            x = x + 227.5,
            y = y + 171.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.sp_atk"),
            x = x + 227.5,
            y = y + 179.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.sp_def"),
            x = x + 227.5,
            y = y + 187.5,
            scale = SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.stats.speed"),
            x = x + 227.5,
            y = y + 195.5,
            scale = SCALE
        )
    }
}
