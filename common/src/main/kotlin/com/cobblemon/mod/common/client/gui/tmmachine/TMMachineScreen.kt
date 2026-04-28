/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.ColourLibrary
import com.cobblemon.mod.common.api.gui.MultiLineLabelK
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.reactive.SettableObservable
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.ScrollingWidget
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.interact.moveselect.MoveSlotButton
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.net.messages.client.ui.SetActiveTMPacket
import com.cobblemon.mod.common.net.messages.client.ui.SetTMMachineContainerDataPacket
import com.cobblemon.mod.common.net.messages.server.block.TMMachineTeachMovePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isInventoryKeyPressed
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil

class TMMachineScreen(containerMenu: TMMachineMenu, val inventory: Inventory, title: Component) : AbstractContainerScreen<TMMachineMenu>(containerMenu, inventory, title) {
    companion object {
        const val HEIGHT = 226
        const val WIDTH = 191
        const val HALF_SCALE = 0.5F

        const val DISC_DIAMETER = 56

        const val LOGO_WIDTH = 86
        const val LOGO_HEIGHT = 64

        const val TYPE_SELECT_MODE = 1
        const val MOVE_SELECT_MODE = 2
        const val TM_BURN_MODE = 3

        const val CRAFT_TICKS = 10 // Length for craft completion animation
        const val RESET_DISC_TICKS = 4 // Length for disc reset animation

        const val SCREEN_SAVER_TIMEOUT_TICKS = 1200 // 60 Seconds
        const val SCREEN_SAVER_WIDTH = 118
        const val SCREEN_SAVER_HEIGHT = 110

        const val DESCRIPTION_SCROLLBAR_WIDTH = 2

        val moveOverlayBar = cobblemonResource("textures/gui/tmmachine/summary_move_overlay_bar.png")

        val baseResource = cobblemonResource("textures/gui/tmmachine/base.png")
        val tmTray = cobblemonResource("textures/gui/tmmachine/tm_tray.png")
        val iconBack = cobblemonResource("textures/gui/tmmachine/icon_back.png")
        val tooltipMoveInfo = cobblemonResource("textures/gui/tmmachine/tooltip_move_info.png")
        val emptyDiscSlot = cobblemonResource("textures/item/tms/blank_disc_empty_slot.png")

        val scrollbarSlide = cobblemonResource("textures/gui/tmmachine/scrollbar_slide.png")
        val scrollbarTrack = cobblemonResource("textures/gui/tmmachine/scrollbar_track.png")

        val discBase = cobblemonResource("textures/gui/tmmachine/tm_base.png")
        val discBorder = cobblemonResource("textures/gui/tmmachine/tm_border.png")
        val discOverlay = cobblemonResource("textures/gui/tmmachine/tm_base_overlay.png")
        val discReflective = cobblemonResource("textures/gui/tmmachine/tm_overlay_reflective.png")
        val discReflectiveRotating = cobblemonResource("textures/gui/tmmachine/tm_overlay_reflective_rotating.png")
        val discRotating = cobblemonResource("textures/gui/tmmachine/tm_overlay_rotating.png")

        val logo = cobblemonResource("textures/gui/tmmachine/logo_tm.png")
        val screenSaver = cobblemonResource("textures/gui/tmmachine/screen_saver_background.png")
        val screenOverlay = cobblemonResource("textures/gui/tmmachine/screen_overlay.png")
        val scanLines = cobblemonResource("textures/gui/tmmachine/scan_lines.png")

        val logoResourceMap: Map<ResourceLocation, List<String>> = mapOf(
            cobblemonResource("textures/gui/tmmachine/logo_ct.png") to listOf("fr"),
            cobblemonResource("textures/gui/tmmachine/logo_mt.png") to listOf("es", "it", "pt")
        )

        fun getLogoResource(langCode: String?): ResourceLocation =
            logoResourceMap.entries.find { it.value.contains(langCode?.substringBefore("_")) }?.key ?: logo
    }
    var selectedTM: TechnicalMachine? = null
    var heldStackMove: MoveTemplate? = null
    var tmList: SettableObservable<MutableList<TechnicalMachine>> = SettableObservable(mutableListOf())
    var output: ItemStack? = null
    var mode: Int = TYPE_SELECT_MODE
    var sortType: ElementalType? = null
    var selectedPokemon: Pokemon? = null

    var partySlotList: MutableSet<TMPartySlotWidget> = mutableSetOf()

    lateinit var backButton: IconButton
    lateinit var startButton: StartButton
    lateinit var selectedMoveButton: MoveSlotButton
    lateinit var moveDescriptionWidget: MoveDescriptionWidget
    lateinit var typesScrollList: TypesScrollingWidget
    lateinit var movesScrollingList: MovesScrollingWidget
    lateinit var moveSearchWidget: MoveSearchWidget

    var discReflectionFrame: Int = 0
    var discRotationFrame: Int = 0

    var ticksElapsed: Int = 0

    var logoResource: ResourceLocation = logo
    var logoTint: Triple<Double, Double, Double> = ElementalTypes.all().random().secondaryColor.toRGB()
    var logoPosX: Int = 0
    var logoPosY: Int = 0
    var logoSlideRight: Boolean = true
    var logoSlideDown: Boolean = true
    var scanPosY: Int = 0
    var scanLineOffsetY: Double = 0.0

    var initScreen: Boolean = false
    var heldMoveItemChanged = false

    override fun init() {
        imageWidth = WIDTH
        imageHeight = HEIGHT

        super.init()

        logoResource = getLogoResource(minecraft?.languageManager?.selected)

        logoPosX = leftPos + 1
        logoPosY = topPos + 1
        scanPosY = topPos + 1

        partySlotList.clear()
        CobblemonClient.storage.party.forEachIndexed { index, pokemon ->
            partySlotList.add(TMPartySlotWidget(
                pX = leftPos - 60,
                pY = topPos + 9 + ((TMPartySlotWidget.HEIGHT + 3) * index),
                pokemon = pokemon,
                onPress = {
                    if ((it as TMPartySlotWidget).clickable  && pokemon != null) {
                        // Teach move if holding TM item, else open learnable moves list for Pokémon
                        if (heldStackMove != null) {
                            heldStackMove?.let { move ->
                                if (canLearnTMMove(move, pokemon) == TMPartySlotWidget.CAN_LEARN) {
                                    CobblemonNetwork.sendToServer(TMMachineTeachMovePacket(pokemon.uuid, menu.carried, move))
                                    // Mark to update client next tick
                                    heldMoveItemChanged = true
                                }
                            }
                        } else if (mode != TM_BURN_MODE) {
                            setMoveList(pokemon = pokemon)
                            setScreenFromMode(MOVE_SELECT_MODE)
                            selectedPokemon = pokemon
                            playSound(CobblemonSounds.GUI_CLICK)
                        }
                    }
                }
            ))
        }

        for (slot in partySlotList) {
            if (!children().contains(slot)) addRenderableWidget(slot)
        }

        moveDescriptionWidget = MoveDescriptionWidget(
            leftPos + 125,
            topPos + 42,
            60,
            34
        )
        if (!children().contains(moveDescriptionWidget)) addRenderableWidget(moveDescriptionWidget)

        typesScrollList = TypesScrollingWidget(
            pX = leftPos + 6,
            pY = topPos + 11,
            setType = { type ->
                sortType = type
                setMoveList()
                setScreenFromMode(MOVE_SELECT_MODE)
            }
        )
        if (!children().contains(typesScrollList)) addRenderableWidget(typesScrollList)

        movesScrollingList = MovesScrollingWidget(
            pX = leftPos + 4,
            pY = topPos + 29,
            tmList,
            setTM = { tm, clicked -> setSelectedTM(tm, clicked) }
        )
        if (!children().contains(movesScrollingList)) addRenderableWidget(movesScrollingList)

        moveSearchWidget = MoveSearchWidget(pX = leftPos + 16, pY = topPos + 13) {
            if (::movesScrollingList.isInitialized && moveSearchWidget.isFocused) {
                val input = moveSearchWidget.value
                setMoveList(filter = if (input.isBlank()) null else input)
            }
        }
        if (!children().contains(moveSearchWidget)) addRenderableWidget(moveSearchWidget)

        backButton = IconButton(
            leftPos + 106F,
            topPos + 1F,
            20,
            18,
            iconBack
        ) {
            setScreenFromMode(TYPE_SELECT_MODE)
        }
        if (!children().contains(backButton)) addRenderableWidget(backButton)

        selectedMoveButton = MoveSlotButton(
            leftPos + 6,
            topPos + 6,
            move = selectedTM?.moveName,
            pp = selectedTM?.moveName?.pp ?: 0,
            ppAsFraction = false
        ) {
            if ((it as MoveSlotButton).enabled) {
                if (::movesScrollingList.isInitialized && movesScrollingList.children().isEmpty()) setMoveList()
                playSound(CobblemonSounds.GUI_CLICK)
                setScreenFromMode(MOVE_SELECT_MODE)

                // Clear active TM
                CobblemonNetwork.sendToServer(SetActiveTMPacket(null))
            }
        }
        if (!children().contains(selectedMoveButton)) addRenderableWidget(selectedMoveButton)

        startButton = StartButton(leftPos + 5F, topPos + 35F) {
            if (!(it as StartButton).disabled) {
                selectedTM?.let {
                    val burnActive = menu.containerData?.get(TMMachineBlockEntity.BURN_ACTIVE_INDEX) ?: 0
                    CobblemonNetwork.sendToServer(SetTMMachineContainerDataPacket(
                        TMMachineBlockEntity.BURN_ACTIVE_INDEX,
                        burnActive xor 1 // Toggle active state, 1 ⇆ 0
                    ))

                    /*// If button is not active, set if process should repeat or not
                    if (burnActive == 0) {
                        CobblemonNetwork.sendToServer(SetTMMachineContainerDataPacket(
                            TMMachineBlockEntity.REPEAT_PROCESS_INDEX,
                            if (hasShiftDown()) 1 else 0
                        ))
                    }*/

                    CobblemonNetwork.sendToServer(SetActiveTMPacket(it))
                    inventory.setChanged()
                    menu.broadcastChanges()
                }
            }
        }
        if (!children().contains(startButton)) addRenderableWidget(startButton)

        setScreenFromMode(mode)
        initScreen = false
    }

    override fun containerTick() {
        ticksElapsed++
        scanPosY++
        scanLineOffsetY = (scanLineOffsetY + 0.5) % 6
        if (scanPosY + 1 >= (topPos + SCREEN_SAVER_HEIGHT) * 2) scanPosY = 0

        if (mode == TM_BURN_MODE) {
            selectedTM?.let { tm ->
                val resultStack = menu.inventory!!.getItem(TMMachineMenu.RESULT_SLOT)
                val craftedTmStack = ItemStack(CobblemonItems.TECHNICAL_MACHINE)
                    .also { TMMoveComponent.setTMMove(it, tm.moveName) }

                var validOutput = resultStack.isEmpty || (
                    !resultStack.isEmpty
                    && (resultStack.count < resultStack.maxStackSize)
                    && ItemStack.isSameItemSameComponents(resultStack, craftedTmStack)
                )
                var validCost = !menu.inventory!!.getItem(TMMachineMenu.BLANK_TM_SLOT).isEmpty // Blank TM slot

                if (validOutput && validCost) {
                    val recipes = tm.getClampedRecipe() ?: listOf()
                    recipes.forEachIndexed { index, recipe ->
                        val slotStack = menu.inventory!!.getItem(index + TMMachineMenu.INGREDIENT_SLOTS.first)
                        if (!recipe.ingredient.test(slotStack) || slotStack.count < recipe.count) {
                            validCost = false
                            return@forEachIndexed
                        }
                    }
                }

                if (::startButton.isInitialized) {
                    val discResetting = menu.getPostCraftTicks() > CRAFT_TICKS
                    val burning = isBurnActive()

                    startButton.disabled = !burning && (discResetting || !(validCost && validOutput))
                    startButton.processing = burning
                    startButton.shouldRepeat = false
                }

                if (::selectedMoveButton.isInitialized) {
                    selectedMoveButton.enabled = !isBurnActive() && (heldStackMove == null || heldStackMove == selectedTM?.moveName)
                }

                if (isBurnActive()) {
                    val progress = menu.getBurnProgressRatio()
                    if (progress > 0 && progress < 1) {
                        discReflectionFrame = (discReflectionFrame + 1) % 4
                        discRotationFrame = (discRotationFrame + 1) % 8
                    }
                    if ((progress == 1F) && (menu.getPostCraftTicks() == 0)) playSound(CobblemonSounds.TM_MACHINE_CRAFT)
                }
            }
        } else {
            // Check if screen should be switched upon opening UI
            if (!initScreen) loadBurnScreenData()
        }

        if (heldMoveItemChanged) {
            heldMoveItemChanged = false
            updateHeldStackMove()
        }

        // Screen saver
        if (ticksElapsed > SCREEN_SAVER_TIMEOUT_TICKS) {
            logoPosX = logoPosX + (if (logoSlideRight) 1 else -1)
            logoPosY = logoPosY + (if (logoSlideDown) 1 else -1)
            var borderHit = false

            if (logoSlideRight && (logoPosX + (LOGO_WIDTH * HALF_SCALE).toInt()) >= (leftPos + SCREEN_SAVER_WIDTH + 1)) {
                logoSlideRight = false
                borderHit = true
            }
            if ((!logoSlideRight) && logoPosX <= (leftPos + 1)) {
                logoSlideRight = true
                borderHit = true
            }

            if (logoSlideDown && (logoPosY + (LOGO_HEIGHT * HALF_SCALE).toInt()) >= (topPos + SCREEN_SAVER_HEIGHT + 1)) {
                logoSlideDown = false
                borderHit = true
            }
            if ((!logoSlideDown) && logoPosY <= (topPos + 1)) {
                logoSlideDown = true
                borderHit = true
            }

            if (borderHit) {
                logoTint = ElementalTypes.all().random().secondaryColor.toRGB()
            }
        }

        super.containerTick()
    }

    private fun resetScreenSaver(): Boolean {
        val wasScreenSaverActive = ticksElapsed > SCREEN_SAVER_TIMEOUT_TICKS
        ticksElapsed = 0
        return wasScreenSaverActive
    }

    private fun loadBurnScreenData() {
        var tm: TechnicalMachine? = null

        val blockPos = menu.containerData?.let {
            val x = it.get(TMMachineBlockEntity.POSITION_X_INDEX)
            val y = it.get(TMMachineBlockEntity.POSITION_Y_INDEX)
            val z = it.get(TMMachineBlockEntity.POSITION_Z_INDEX)
            BlockPos(x, y, z)
        }

        inventory.player.level().getBlockEntity(blockPos)?.let { blockEntity ->
            if (blockEntity is TMMachineBlockEntity) {
                Moves.getByName(blockEntity.activeMove)?.let {
                    tm = TechnicalMachines.moveToTM[it]
                }
            }
        }

        // If burn currently in progress or if active TM set, set to burn screen
        if (isBurnActive() || tm != null) {
            setSelectedTM(tm, true)
        }

        initScreen = true
    }

    private fun isBurnActive(): Boolean = (menu.containerData?.get(TMMachineBlockEntity.BURN_ACTIVE_INDEX) ?: 0) == 1

    private fun setScreenFromMode(screen: Int) {
        mode = screen
        when (mode) {
            TYPE_SELECT_MODE -> {
                toggleTMBurn(false)
                toggleMoveSelect(false)
                toggleTypeSelect(true)
            }
            MOVE_SELECT_MODE -> {
                toggleTMBurn(false)
                toggleTypeSelect(false)
                toggleMoveSelect(true)
            }
            else -> {
                toggleTypeSelect(false)
                toggleMoveSelect(false)
                toggleTMBurn(true)
            }
        }
        updateHeldStackMove()
    }

    private fun setMoveList(filter: String? = null, pokemon: Pokemon? = null) {
        val filteredList = TechnicalMachine.filterTms(
            search = filter,
            type = sortType,
            pokemon = pokemon,
            player = inventory.player,
            includeUnlearned = ServerSettings.unlockAllMoveDexMovesByDefault
        ).toMutableList()
        pokemon?.let { selectedPokemon ->
            filteredList.retainAll { tm -> tm.moveName in selectedPokemon.form.moves.tmMoves }
        }
        if (sortType == null) filteredList.sortBy { it.type }
        tmList.set(filteredList)
    }

    private fun toggleTypeSelect(isVisible: Boolean) {
        if (isVisible) {
            setSelectedTM(null, false)
            if (::moveSearchWidget.isInitialized) moveSearchWidget.value = ""
            if (selectedPokemon != null) selectedPokemon = null
            sortType = null
        }

        if (::typesScrollList.isInitialized) {
            typesScrollList.apply {
                visible = isVisible
                scrollAmount = 0.0
                // Resize widget to toggle input detection as screen only detects first widget added for overlapping children
                if (isVisible) { setSize(TypesScrollingWidget.WIDTH, TypesScrollingWidget.HEIGHT) }
                else { setSize(0, 0) }
            }
        }

        setPartySlotsClickable(isVisible)
    }

    private fun toggleMoveSelect(isVisible: Boolean) {
        if (::movesScrollingList.isInitialized) {
            movesScrollingList.apply {
                visible = isVisible
                scrollAmount = 0.0
                if (isVisible) { setSize(MovesScrollingWidget.WIDTH, MovesScrollingWidget.HEIGHT) }
                else { setSize(0, 0) }
            }
        }
        if (::moveSearchWidget.isInitialized) {
            moveSearchWidget.visible = isVisible
        }
        if (::backButton.isInitialized) {
            backButton.visible = isVisible
        }
        heldStackMove = null
    }

    private fun toggleTMBurn(isVisible: Boolean) {
        if (!isVisible) setSelectedTM(null, false)
        if (::selectedMoveButton.isInitialized) selectedMoveButton.visible = isVisible
        if (::startButton.isInitialized) startButton.visible = isVisible
    }

    fun canLearnTMMove(move: MoveTemplate, pokemon: Pokemon): Int {
        if (pokemon.moveSet.getMoveTemplates().contains(move) || pokemon.allAccessibleMoves.contains(move)) return TMPartySlotWidget.LEARNED
        val learnableMoves = pokemon.form.moves.tmMoves
        return if (learnableMoves.contains(move)) TMPartySlotWidget.CAN_LEARN else TMPartySlotWidget.CANNOT_LEARN
    }

    fun setSelectedTM(tm: TechnicalMachine?, clicked: Boolean) {
        if (selectedTM != tm) {
            selectedTM = tm
            updatePartySlotStatus(tm?.moveName)
        }

        if (::movesScrollingList.isInitialized) {
            movesScrollingList.setSlotHighlighted(if (clicked) null else selectedTM?.id)
        }

        if (::moveDescriptionWidget.isInitialized) {
            val selectedDescription = selectedTM?.moveName?.description?.string
            moveDescriptionWidget.setText(
                if (selectedDescription != null) listOf(selectedDescription) else emptyList()
            )
        }

        if (clicked) {
            CobblemonNetwork.sendToServer(SetActiveTMPacket(tm))

            selectedTM?.let {
                if (::selectedMoveButton.isInitialized) {
                    selectedMoveButton.apply {
                        move = it.moveName
                        pp = it.moveName.pp
                    }
                    setScreenFromMode(TM_BURN_MODE)
                }
            }
        }
    }

    fun updatePartySlotStatus(moveTemplate: MoveTemplate?) {
        for (slot in partySlotList) {
            if (slot.pokemon != null) {
                slot.teachable = if (moveTemplate != null) canLearnTMMove(moveTemplate, slot.pokemon) else null
            }
        }
    }

    fun renderDiscTypeOverlay(poseStack: PoseStack, isDiscRotating: Boolean, alpha: Float = 1F) {
        selectedTM?.let {
            val primaryRgb = it.moveName.elementalType.primaryColor.toRGB()
            val secondaryRgb = it.moveName.elementalType.secondaryColor.toRGB()
            blitk(
                matrixStack = poseStack,
                texture = discBase,
                x = leftPos + 32,
                y = topPos + 50,
                width = DISC_DIAMETER,
                height = DISC_DIAMETER,
                red = primaryRgb.first,
                green = primaryRgb.second,
                blue = primaryRgb.third,
                alpha = alpha
            )

            if (isDiscRotating) {
                // Reflective rotation
                blitk(
                    matrixStack = poseStack,
                    texture = discReflectiveRotating,
                    x = leftPos + 32,
                    y = topPos + 50,
                    width = DISC_DIAMETER,
                    height = DISC_DIAMETER,
                    vOffset = DISC_DIAMETER * discReflectionFrame,
                    textureHeight = DISC_DIAMETER * 4,
                    red = secondaryRgb.first,
                    green = secondaryRgb.second,
                    blue = secondaryRgb.third,
                    alpha = alpha
                )

                // Groove rotation
                blitk(
                    matrixStack = poseStack,
                    texture = discRotating,
                    x = leftPos + 32,
                    y = topPos + 50,
                    width = DISC_DIAMETER,
                    height = DISC_DIAMETER,
                    vOffset = DISC_DIAMETER * discRotationFrame,
                    textureHeight = DISC_DIAMETER * 8,
                    red = secondaryRgb.first,
                    green = secondaryRgb.second,
                    blue = secondaryRgb.third,
                    alpha = alpha
                )
            } else {
                blitk(
                    matrixStack = poseStack,
                    texture = discReflective,
                    x = leftPos + 32,
                    y = topPos + 50,
                    width = DISC_DIAMETER,
                    height = DISC_DIAMETER,
                    red = secondaryRgb.first,
                    green = secondaryRgb.second,
                    blue = secondaryRgb.third,
                    alpha = alpha
                )
            }

            val postCraftTicks = menu.getPostCraftTicks()
            if (postCraftTicks > 0 && postCraftTicks < 8) {
                val opacity = when {
                    postCraftTicks <= 5 -> postCraftTicks * 0.25F
                    postCraftTicks > 5 -> (CRAFT_TICKS - postCraftTicks) * 0.25F  // decreases by 0.25 each tick after 5
                    else -> 0F
                }

                blitk(
                    matrixStack = poseStack,
                    texture = discOverlay,
                    x = leftPos + 32,
                    y = topPos + 50,
                    width = DISC_DIAMETER,
                    height = DISC_DIAMETER,
                    alpha = opacity
                )
            }
        }
    }

    fun renderDisc(poseStack: PoseStack, isDiscRotating: Boolean, baseAlpha: Float = 1F, overlayAlpha: Float = 1F) {
        blitk(
            matrixStack = poseStack,
            texture = discBase,
            x = leftPos + 32,
            y = topPos + 50,
            width = DISC_DIAMETER,
            height = DISC_DIAMETER,
            alpha = baseAlpha
        )

        if (isDiscRotating) {
            // Reflective rotation
            blitk(
                matrixStack = poseStack,
                texture = discReflectiveRotating,
                x = leftPos + 32,
                y = topPos + 50,
                width = DISC_DIAMETER,
                height = DISC_DIAMETER,
                vOffset = DISC_DIAMETER * discReflectionFrame,
                textureHeight = DISC_DIAMETER * 4,
                alpha = baseAlpha
            )

            // Groove rotation
            blitk(
                matrixStack = poseStack,
                texture = discRotating,
                x = leftPos + 32,
                y = topPos + 50,
                width = DISC_DIAMETER,
                height = DISC_DIAMETER,
                vOffset = DISC_DIAMETER * discRotationFrame,
                textureHeight = DISC_DIAMETER * 8,
                alpha = baseAlpha
            )
        } else {
            blitk(
                matrixStack = poseStack,
                texture = discReflective,
                x = leftPos + 32,
                y = topPos + 50,
                width = DISC_DIAMETER,
                height = DISC_DIAMETER,
                alpha = baseAlpha
            )
        }

        if (menu.getPostCraftTicks() < CRAFT_TICKS) {
            renderDiscTypeOverlay(poseStack, isDiscRotating, overlayAlpha)
        }

        blitk(
            matrixStack = poseStack,
            texture = discBorder,
            x = leftPos + 32,
            y = topPos + 50,
            width = DISC_DIAMETER,
            height = DISC_DIAMETER,
            alpha = baseAlpha
        )
    }

    fun renderMoveInfo(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        val moveTemplate = selectedTM?.moveName

        blitk(
            matrixStack = context.pose(),
            texture = MovesWidget.movesPowerIconResource,
            x = leftPos + 127.5,
            y = topPos + 7.5,
            width = 5,
            height = 5
        )

        blitk(
            matrixStack = context.pose(),
            texture = MovesWidget.movesAccuracyIconResource,
            x = leftPos + 127.5,
            y = topPos + 19.5,
            width = 5,
            height = 5
        )

        blitk(
            matrixStack = context.pose(),
            texture = MovesWidget.movesEffectIconResource,
            x = leftPos + 127.5,
            y = topPos + 31.5,
            width = 5,
            height = 5
        )

        drawScaledText(
            context = context,
            text = lang("ui.power"),
            x = leftPos + 134.5,
            y = topPos + 8,
            shadow = true,
            scale = HALF_SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.accuracy"),
            x = leftPos + 134.5,
            y = topPos + 20,
            shadow = true,
            scale = HALF_SCALE
        )

        drawScaledText(
            context = context,
            text = lang("ui.effect"),
            x = leftPos + 134.5,
            y = topPos + 32,
            shadow = true,
            scale = HALF_SCALE
        )

        val movePower = if (moveTemplate != null && moveTemplate.power.toInt() > 0) moveTemplate.power.toInt().toString().text() else "—".text()
        drawScaledTextJustifiedRight(
            context = context,
            text = movePower,
            x = leftPos + 183,
            y = topPos + 8,
            shadow = true,
            scale = HALF_SCALE
        )

        val moveAccuracy = if (moveTemplate != null) MovesWidget.format(moveTemplate.accuracy).text() else "—".text()
        drawScaledTextJustifiedRight(
            context = context,
            text = moveAccuracy,
            x = leftPos + 183,
            y = topPos + 20,
            shadow = true,
            scale = HALF_SCALE
        )

        val moveEffect = if (moveTemplate != null) MovesWidget.format(moveTemplate.effectChances.firstOrNull() ?: 0.0).text() else "—".text()
        drawScaledTextJustifiedRight(
            context = context,
            text = moveEffect,
            x = leftPos + 183,
            y = topPos + 32,
            shadow = true,
            scale = HALF_SCALE
        )

        if (moveTemplate != null) {
            val recipe = TechnicalMachines.moveToTM[moveTemplate]?.getClampedRecipe() ?: listOf()
            // Render material cost
            recipe.forEachIndexed { index, recipe ->
                val itemX = (leftPos + 129 + (index * 18))
                val itemY = (topPos + 90)

                val stacks = recipe.ingredient.items
                if (stacks.isEmpty()) return@forEachIndexed

                val level = Minecraft.getInstance().level ?: return@forEachIndexed
                val index = ((level.gameTime / 20) % stacks.size).toInt()

                val itemStack = stacks[index].copy().also { it.count = recipe.count }

                context.renderItem(itemStack, itemX, itemY)
                context.renderItemDecorations(Minecraft.getInstance().font, itemStack, itemX, itemY)

                if (
                    menu.carried.isEmpty &&
                    mouseX >= itemX && mouseX < itemX + 16 &&
                    mouseY >= itemY && mouseY < itemY + 16
                ) {
                    context.renderTooltip(Minecraft.getInstance().font, itemStack, mouseX, mouseY)
                }
            }
        }
    }

    fun renderHeldMoveInfoTooltip(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        heldStackMove?.let { moveTemplate ->
            val matrices = context.pose()
            val posX = mouseX + 10
            val posY = mouseY - 49

            matrices.pushPose()
            matrices.translate(0.0F, 0.0F, 1000.0F)
            blitk(
                matrixStack = matrices,
                texture = tooltipMoveInfo,
                x = posX,
                y = posY,
                width = 96,
                height = 98,
                alpha = 0.96F
            )

            drawScaledText(
                context = context,
                text = moveTemplate.displayName.bold(),
                x = posX + 6,
                y = posY + 6,
                shadow = true,
                scale = HALF_SCALE
            )

            TypeIcon(posX + 81, posY + 3.5, moveTemplate.elementalType, small = true).render(context)

            blitk(
                matrixStack = matrices,
                texture = MovesWidget.movesPowerIconResource,
                x = posX + 8.5,
                y = posY + 15.5,
                width = 5,
                height = 5
            )

            blitk(
                matrixStack = matrices,
                texture = MovesWidget.movesAccuracyIconResource,
                x = posX + 8.5,
                y = posY + 27.5,
                width = 5,
                height = 5
            )

            blitk(
                matrixStack = matrices,
                texture = MovesWidget.movesEffectIconResource,
                x = posX + 8.5,
                y = posY + 39.5,
                width = 5,
                height = 5
            )

            drawScaledText(
                context = context,
                text = lang("ui.power"),
                x = posX + 15.5,
                y = posY + 16,
                shadow = true,
                scale = HALF_SCALE
            )

            drawScaledText(
                context = context,
                text = lang("ui.accuracy"),
                x = posX + 15.5,
                y = posY + 28,
                shadow = true,
                scale = HALF_SCALE
            )

            drawScaledText(
                context = context,
                text = lang("ui.effect"),
                x = posX + 15.5,
                y = posY + 40,
                shadow = true,
                scale = HALF_SCALE
            )

            val movePower = if (moveTemplate.power.toInt() > 0) moveTemplate.power.toInt().toString().text() else "—".text()
            drawScaledTextJustifiedRight(
                context = context,
                text = movePower,
                x = posX + 88,
                y = posY + 16,
                shadow = true,
                scale = HALF_SCALE
            )

            val moveAccuracy = MovesWidget.format(moveTemplate.accuracy).text()
            drawScaledTextJustifiedRight(
                context = context,
                text = moveAccuracy,
                x = posX + 88,
                y = posY + 28,
                shadow = true,
                scale = HALF_SCALE
            )

            val moveEffect = MovesWidget.format(moveTemplate.effectChances.firstOrNull() ?: 0.0).text()
            drawScaledTextJustifiedRight(
                context = context,
                text = moveEffect,
                x = posX + 88,
                y = posY + 40,
                shadow = true,
                scale = HALF_SCALE
            )

            matrices.pushPose()
            matrices.scale(HALF_SCALE, HALF_SCALE, 1F)
            MultiLineLabelK.create(
                component = moveTemplate.description,
                width = 79 / HALF_SCALE,
                maxLines = 5
            ).renderLeftAligned(
                context = context,
                x = (posX + 8.5) / HALF_SCALE,
                y = (posY + 52.5) / HALF_SCALE,
                ySpacing = 6 / HALF_SCALE,
                colour = ColourLibrary.WHITE,
                shadow = true
            )
            matrices.popPose()

            drawScaledText(
                context = context,
                text = lang("ui.moves.pp", moveTemplate.pp).bold(),
                x = posX + 6,
                y = posY + 88,
                scale = HALF_SCALE,
                shadow = true
            )
            MoveCategoryIcon(x = posX + 78, y = posY + 86, category = moveTemplate.damageCategory).render(context)

            matrices.popPose()
        }
    }

    fun renderScreenSaver(context: GuiGraphics) {
        val poseStack = context.pose()
        // Screen saver
        if (ticksElapsed > SCREEN_SAVER_TIMEOUT_TICKS) {
            val opacity = Math.max(0.0, Math.min(1.0, (ticksElapsed - SCREEN_SAVER_TIMEOUT_TICKS) * 0.2))
            poseStack.pushPose()
            poseStack.translate(0.0, 0.0, 100.0)
            blitk(
                matrixStack = poseStack,
                texture = screenSaver,
                x = leftPos + 1,
                y = topPos + 1,
                width = SCREEN_SAVER_WIDTH,
                height = SCREEN_SAVER_HEIGHT,
                alpha = opacity
            )

            blitk(
                matrixStack = poseStack,
                texture = logoResource,
                x = logoPosX / HALF_SCALE,
                y = logoPosY / HALF_SCALE,
                width = LOGO_WIDTH,
                height = LOGO_HEIGHT,
                scale = HALF_SCALE,
                red = logoTint.first,
                green = logoTint.second,
                blue = logoTint.third,
                alpha = 0.8 * opacity
            )

            blitk(
                matrixStack = poseStack,
                texture = cobblemonResource("textures/white.png"),
                x = leftPos + 1,
                y = scanPosY * 0.5,
                width = SCREEN_SAVER_WIDTH,
                height = 1,
                red = 0,
                green = 0,
                blue = 0,
                alpha = 0.1 * opacity
            )

            context.enableScissor(
                leftPos + 1,
                topPos + 1,
                leftPos + 1 + SCREEN_SAVER_WIDTH,
                topPos + 1 + SCREEN_SAVER_HEIGHT
            )
            blitk(
                matrixStack = poseStack,
                texture = scanLines,
                x = leftPos + 1,
                y = topPos + 1 - scanLineOffsetY,
                width = 118,
                height = 114,
                alpha = opacity
            )
            context.disableScissor()

            blitk(
                matrixStack = poseStack,
                texture = screenOverlay,
                x = leftPos + 1,
                y = topPos + 1,
                width = SCREEN_SAVER_WIDTH,
                height = SCREEN_SAVER_HEIGHT,
                alpha = 0.5 * opacity
            )

            poseStack.popPose()
        }
    }

    private fun setPartySlotsClickable(clickable: Boolean) {
        for (slot in partySlotList) slot.clickable = clickable
    }

    private fun updateHeldStackMove() {
        val heldStack = menu.carried
        val heldStackIsTm = heldStack.item == CobblemonItems.TECHNICAL_MACHINE

        if (heldStackIsTm) {
            // Store move of TM item picked up by player cursor
            TMMoveComponent.getTMMove(heldStack)?.let { heldMove ->
                if (heldStackMove != heldMove) heldStackMove = heldMove
            }

            if (::movesScrollingList.isInitialized) {
                movesScrollingList.setDisabled(true, TechnicalMachines.moveToTM[heldStackMove])
                if (mode == MOVE_SELECT_MODE) {
                    val heldStackTm = TechnicalMachines.moveToTM[heldStackMove]
                    setSelectedTM(if (tmList.get().contains(heldStackTm)) heldStackTm else null, false)

                    // Scroll to move if moves list contains held TM move
                    if (tmList.get().contains(heldStackTm)) {
                        val moveListIndex = tmList.get().indexOf(heldStackTm)
                        movesScrollingList.scrollAmount = (if (moveListIndex == tmList.get().lastIndex) 1.0 else (moveListIndex / tmList.get().size.toDouble()))* movesScrollingList.maxScroll
                    }
                }
            }

            if (::typesScrollList.isInitialized) {
                typesScrollList.setDisabled(true, heldStackMove?.elementalType)
            }

            updatePartySlotStatus(heldStackMove)
        } else if (heldStackMove != null) {
            // Reset only if last held item was a TM
            heldStackMove = null

            // Reset selected move on move select screen
            if (mode == MOVE_SELECT_MODE) setSelectedTM(null, false)
            updatePartySlotStatus(selectedTM?.moveName)

            if (::movesScrollingList.isInitialized) {
                movesScrollingList.setDisabled(false)
            }
            if (::typesScrollList.isInitialized) {
                typesScrollList.setDisabled(false)
            }
        }

        setPartySlotsClickable(mode == TYPE_SELECT_MODE || heldStackIsTm)
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        ticksElapsed = 0
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        ticksElapsed = 0
        if (resetScreenSaver()) return false
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun slotClicked(slot: Slot?, slotId: Int, mouseButton: Int, type: ClickType) {
        if (slot != null) {
            super.slotClicked(slot, slotId, mouseButton, type)
            updateHeldStackMove()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        ticksElapsed = 0
        if (resetScreenSaver()) return false
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        ticksElapsed = 0
        if (resetScreenSaver()) return false
        val searchFocused = this::moveSearchWidget.isInitialized && moveSearchWidget.isFocused

        if (isInventoryKeyPressed(minecraft, keyCode, scanCode)) {
            if (searchFocused) { return true }
            else {
                playSound(CobblemonSounds.TM_MACHINE_OFF)
                Minecraft.getInstance().setScreen(null)
                return true
            }
        }

        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (searchFocused) {
                // Escape from text box
                this.focused = null
                return true
            } else {
                playSound(CobblemonSounds.TM_MACHINE_OFF)
                Minecraft.getInstance().setScreen(null)
                return true
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderTransparentBackground(graphics)

        val matrices = graphics.pose()
        blitk(
            matrixStack = matrices,
            texture = baseResource,
            x = leftPos,
            y = topPos,
            width = WIDTH,
            height = HEIGHT
        )

        blitk(
            matrixStack = matrices,
            texture = emptyDiscSlot,
            x = leftPos + 105,
            y = topPos + 116,
            width = 16,
            height = 16
        )

        var label = if (mode != MOVE_SELECT_MODE) "block.cobblemon.tm_machine".asTranslated()
            else ( if (selectedPokemon != null) selectedPokemon!!.getDisplayName() else (if (sortType != null) lang("type.suffix", sortType!!.displayName) else lang("ui.moves")))
        drawScaledText(
            context = graphics,
            font = CobblemonResources.DEFAULT_LARGE,
            text = label.bold(),
            x = leftPos + 5,
            y = topPos + 1
        )

        if (mode == TM_BURN_MODE) {
            blitk(
                matrixStack = matrices,
                texture = tmTray,
                x = leftPos,
                y = topPos,
                width = 120,
                height = 112
            )

            val burnProgressWidth = ceil(menu.getBurnProgressRatio() * 81).toInt()
            blitk(
                matrixStack = matrices,
                texture = CobblemonResources.WHITE,
                x = leftPos + 33,
                y = topPos + 36,
                width = burnProgressWidth,
                height = 2
            )

            if (!menu.inventory!!.getItem(TMMachineMenu.BLANK_TM_SLOT).isEmpty) {
                val postCraftTicks = menu.getPostCraftTicks()
                val baseOpacity = when {
                    ((postCraftTicks >= (CRAFT_TICKS + 1)) && (postCraftTicks <= (CRAFT_TICKS + RESET_DISC_TICKS))) -> (postCraftTicks - (CRAFT_TICKS + 1)) * 0.25F
                    postCraftTicks == (CRAFT_TICKS) -> 0F
                    else -> 1F
                }

                val overlayOpacity = if (postCraftTicks < CRAFT_TICKS) menu.getBurnProgressRatio() else 0F
                renderDisc(matrices, (menu.getBurnProgressRatio() < 1F) && isBurnActive(), baseOpacity, overlayOpacity)
            }
        }

        super.render(graphics, mouseX, mouseY, delta)

        if (mode == TM_BURN_MODE && ::selectedMoveButton.isInitialized) {
            selectedMoveButton.showOverlayBar = !selectedMoveButton.isHovered

            if (selectedMoveButton.isHovered) {
                blitk(
                    matrixStack = matrices,
                    texture = moveOverlayBar,
                    x = selectedMoveButton.x + 85,
                    y = selectedMoveButton.y + 13,
                    width = 22,
                    height = 8
                )

                blitk(
                    matrixStack = matrices,
                    texture = iconBack,
                    x = (selectedMoveButton.x + 92) / HALF_SCALE,
                    y = (selectedMoveButton.y + 13) / HALF_SCALE,
                    width = 20,
                    height = 18,
                    textureHeight = 36,
                    vOffset = 18,
                    scale = HALF_SCALE
                )
            }
        }

        renderScreenSaver(graphics)

        renderMoveInfo(graphics, mouseX, mouseY)

        this.renderTooltip(graphics, mouseX, mouseY)

        renderHeldMoveInfoTooltip(graphics, mouseX, mouseY)
    }

    override fun renderBlurredBackground(partialTick: Float) {}
    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {}

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }

    class MoveDescriptionWidget(pX: Int, pY: Int, width: Int, height: Int) : ScrollingWidget<MoveDescriptionWidget.TextSlot>(
        left = pX,
        top = pY - height,
        width = width,
        height = height,
        slotHeight = 6
    ) {
        fun setText(text: Collection<String>) {
            clearEntries()
            text.forEach { line ->
                Minecraft.getInstance().font.splitter.splitLines(
                    Component.literal(line),
                    ((width - DESCRIPTION_SCROLLBAR_WIDTH - 5) / HALF_SCALE).toInt(),
                    Style.EMPTY
                ).stream()
                    .map { it.string }
                    .forEach { addEntry(TextSlot(it)) }
            }
            scrollAmount = 0.0
        }

        override fun renderScrollbar(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val xLeft = this.scrollbarPosition

            val barHeight = this.bottom - y

            var yBottom = ((barHeight * barHeight).toFloat() / this.maxPosition.toFloat()).toInt()
            yBottom = Mth.clamp(yBottom, 16, barHeight - 6)
            var yTop = scrollAmount.toInt() * (barHeight - yBottom) / this.maxScroll + y
            if (yTop < y) yTop = y

            // Scroll Track
            blitk(
                texture = scrollbarTrack,
                matrixStack = context.pose(),
                x = xLeft,
                y = y,
                width = 2,
                height = height
            )

            // Scroll Slide
            blitk(
                texture = scrollbarSlide,
                matrixStack = context.pose(),
                x = xLeft,
                y = yTop,
                width = 2,
                height = yBottom
            )
        }

        override fun getScrollbarPosition(): Int {
            return left + width - DESCRIPTION_SCROLLBAR_WIDTH
        }

        class TextSlot(val text: String) : Slot<TextSlot>() {
            override fun render(context: GuiGraphics, index: Int, y: Int, x: Int, entryWidth: Int, entryHeight: Int, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
                drawScaledText(
                    context = context,
                    text = text.text(),
                    x = x + 2.5,
                    y = y + 2.5,
                    scale = HALF_SCALE,
                    shadow = true
                )
            }

            override fun getNarration(): Component {
                return Component.literal(text)
            }
        }
    }
}
