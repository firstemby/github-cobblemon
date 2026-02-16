/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_PORTRAIT_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.POKEMON_PORTRAIT_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.PORTRAIT_POKE_BALL_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.PORTRAIT_POKE_BALL_WIDTH
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab.Companion.CosmeticChoice
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab.Companion.collectCosmeticChoices
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab.Companion.choiceAspectId
import java.io.FileNotFoundException

class PokeEditGeneralTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft,
    private val onDraftChanged: () -> Unit
) : SoundlessWidget(pX, pY, HALF_OVERLAY_WIDTH, HALF_OVERLAY_HEIGHT, lang("ui.pokeedit.general")) {
    companion object {
        private val backgroundOverlay = cobblemonResource("textures/gui/pokedex/pokedex_screen_info_overlay.png")
        private val pokeBallOverlay = cobblemonResource("textures/gui/pokedex/pokedex_screen_poke_ball.png")
        private val platformBase = cobblemonResource("textures/gui/pokedex/platform_base.png")
        private val platformShadow = cobblemonResource("textures/gui/pokedex/platform_shadow.png")
        private val typeBar = cobblemonResource("textures/gui/pokedex/type_bar.png")
        private val typeBarDouble = cobblemonResource("textures/gui/pokedex/type_bar_double.png")
        private val arrowFormLeft = cobblemonResource("textures/gui/pokedex/forms_arrow_left.png")
        private val arrowFormRight = cobblemonResource("textures/gui/pokedex/forms_arrow_right.png")
        private val buttonGenderMale = cobblemonResource("textures/gui/pokedex/button_male.png")
        private val buttonGenderFemale = cobblemonResource("textures/gui/pokedex/button_female.png")
        private val buttonNone = cobblemonResource("textures/gui/pokedex/button_none.png")
        private val buttonShiny = cobblemonResource("textures/gui/pokedex/button_shiny.png")
        private val buttonSoundArrow = cobblemonResource("textures/gui/pokedex/button_sound_arrow.png")

        private const val COSMETIC_SLOT_SIZE = 16
        private const val COSMETIC_SLOT_PADDING = 4
        private const val COSMETIC_MENU_COLUMNS = 3
        private const val COSMETIC_MENU_MAX_ROWS = 3
        private const val COSMETIC_MENU_SLOT_STEP = 18
    }

    private val runtime = MoLangRuntime().setupClient().setup().also {
        it.environment.query.addFunction("get_pokedex") { null }
    }

    private val state = FloatingState().also { state ->
        state.runtime.environment.query.addFunction("is_holding_item") {
            val held = currentHeldStack()
            DoubleValue(!held.isEmpty && !held.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !held.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS))
        }
        state.runtime.environment.query.addFunction("is_wearing_hat") {
            DoubleValue(currentHeldStack().`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS))
        }
        state.runtime.environment.query.addFunction("is_wearing_face") {
            DoubleValue(currentHeldStack().`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS))
        }
    }
    private val heldItemRenderer = HeldItemRenderer()
    private var rotationY = 30F
    private var poseList: Array<PoseType> = arrayOf(PoseType.PROFILE, PoseType.WALK, PoseType.SLEEP)
    private var selectedPoseIndex: Int = 0
    private var cosmeticMenuOpen = false
    private var cosmeticScrollRow = 0
    private var cachedCosmeticChoices: List<CosmeticChoice> = emptyList()
    private var lastCosmeticKey: CosmeticKey? = null
    private var heldMenuOpen = false
    private var heldScrollRow = 0
    private var cachedHeldChoices: List<HeldChoice> = emptyList()
    private var heldChoicesInitialized = false

    private data class CosmeticKey(
        val speciesId: ResourceLocation,
        val formName: String,
        val gender: Gender,
        val shiny: Boolean
    )

    private data class HeldChoice(
        val itemId: ResourceLocation,
        val label: MutableComponent
    )

    private val formLeftButton = ScaledButton(
        pX + 18F,
        pY + 55.5F,
        10,
        16,
        arrowFormLeft,
        clickAction = { switchForm(false) }
    ).also { addWidget(it) }

    private val formRightButton = ScaledButton(
        pX + 116F,
        pY + 55.5F,
        10,
        16,
        arrowFormRight,
        clickAction = { switchForm(true) }
    ).also { addWidget(it) }

    private val genderButton = ScaledButton(
        pX + 114F,
        pY + 27F,
        20,
        20,
        resource = buttonGenderMale,
        clickAction = { toggleGender() }
    ).also { addWidget(it) }

    private val shinyButton = ScaledButton(
        pX + 126F,
        pY + 27F,
        20,
        20,
        resource = buttonNone,
        clickAction = { toggleShiny() }
    ).also { addWidget(it) }

    private val animationButton = ScaledButton(
        pX + 115F,
        pY + 83F,
        12,
        12,
        buttonSoundArrow,
        silent = true,
        clickAction = { switchPose() }
    ).also { addWidget(it) }

    private val levelEditBox = EditBox(
        Minecraft.getInstance().font,
        pX + 6,
        pY + 119,
        38,
        12,
        Component.literal("level")
    ).also { editBox ->
        editBox.setMaxLength(3)
        editBox.setFilter { value ->
            if (value.isBlank()) {
                true
            } else {
                value.toIntOrNull()?.let { it in 1..100 } == true
            }
        }
        editBox.value = draft.level.toString()
        editBox.setResponder { value ->
            val parsed = value.toIntOrNull() ?: return@setResponder
            draft.level = parsed.coerceIn(1, 100)
        }
        addWidget(editBox)
    }

    private var renderablePokemon: RenderablePokemon? = null

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val species = draft.species
        val form = draft.form
        val matrices = context.pose()
        val hasGender = form.maleRatio in 0F..1F
        refreshCosmeticChoicesIfNeeded()
        refreshHeldChoicesIfNeeded()

        blitk(
            matrixStack = matrices,
            texture = backgroundOverlay,
            x = x,
            y = y,
            width = HALF_OVERLAY_WIDTH,
            height = HALF_OVERLAY_HEIGHT
        )

        blitk(
            matrixStack = matrices,
            texture = pokeBallOverlay,
            x = x + 15,
            y = y + 25,
            width = PORTRAIT_POKE_BALL_WIDTH,
            height = PORTRAIT_POKE_BALL_HEIGHT,
            vOffset = 0,
            textureHeight = 1744,
        )

        val speciesNumber = species.nationalPokedexNumber.toString().padStart(4, '0').text()
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = speciesNumber.bold(),
            x = x + 3,
            y = y + 1,
            shadow = true
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = species.translatedName.bold(),
            x = x + 26,
            y = y + 1,
            colour = 0x606B6E
        )

        blitk(
            matrixStack = matrices,
            texture = platformBase,
            x = x + 13,
            y = y + 69,
            width = 113,
            height = 24,
            textureHeight = 30
        )

        getPlatformResource(form.primaryType?.showdownId)?.let { platform ->
            blitk(
                matrixStack = matrices,
                texture = platform,
                x = x + 13,
                y = y + 66,
                width = 113,
                height = 27,
                textureHeight = 30
            )
        }

        blitk(
            matrixStack = matrices,
            texture = platformShadow,
            x = (x + 47) / SCALE,
            y = (y + 76.5F) / SCALE,
            width = 90,
            height = 20,
            scale = SCALE
        )

        buildRenderable()?.let { renderable ->
            context.enableScissor(
                x + 1,
                y + 25,
                x + POKEMON_PORTRAIT_WIDTH + 1,
                y + 25 + POKEMON_PORTRAIT_HEIGHT
            )

            matrices.pushPose()
            matrices.translate(
                x.toDouble() + (POKEMON_PORTRAIT_WIDTH.toDouble() + 2) / 2,
                y.toDouble() + 25 - 12,
                1000.0
            )
            matrices.scale(2F, 2F, 2F)
            val rotationVector = Vector3f(13F, rotationY, 0F)

            drawProfilePokemon(
                renderablePokemon = renderable,
                poseType = poseList[selectedPoseIndex],
                matrixStack = matrices,
                partialTicks = delta,
                rotation = Quaternionf().fromEulerXYZDegrees(rotationVector),
                state = state
            )
            if (!renderable.heldItem.isEmpty) {
                heldItemRenderer.renderOnModel(
                    renderable.heldItem,
                    state,
                    matrices,
                    context.bufferSource(),
                    light = 0xF000F0,
                    frontLight = true
                )
            }
            matrices.popPose()
            context.disableScissor()
        }

        blitk(
            matrixStack = matrices,
            texture = if (form.secondaryType != null) typeBarDouble else typeBar,
            x = x,
            y = y + 14,
            width = HALF_OVERLAY_WIDTH,
            height = 25
        )

        form.primaryType?.let { primary ->
            TypeIcon(
                x = x + 3,
                y = y + 17,
                type = primary,
                secondaryType = form.secondaryType,
            ).render(context)
        }

        // Form label
        val formLabel = if (form.name.equals("normal", true)) {
            species.translatedName
        } else {
            lang("ui.pokedex.info.form.${species.resourceIdentifier.path}-${form.name.lowercase()}")
        }
        drawScaledTextJustifiedRight(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = formLabel.bold(),
            x = x + 136,
            y = y + 15,
            shadow = true
        )

        genderButton.active = hasGender
        genderButton.resource = if (!hasGender) {
            buttonNone
        } else if (draft.gender == Gender.FEMALE) {
            buttonGenderFemale
        } else {
            buttonGenderMale
        }
        shinyButton.resource = if (draft.shiny) buttonShiny else buttonNone

        genderButton.render(context, mouseX, mouseY, delta)
        shinyButton.render(context, mouseX, mouseY, delta)
        formLeftButton.render(context, mouseX, mouseY, delta)
        formRightButton.render(context, mouseX, mouseY, delta)
        animationButton.render(context, mouseX, mouseY, delta)

        // Level label
        drawScaledText(
            context = context,
            text = lang("ui.level").bold(),
            x = x + 6,
            y = y + 108,
            scale = SCALE,
            shadow = true
        )
        if (!levelEditBox.isFocused) {
            val clamped = draft.level.coerceIn(1, 100)
            if (levelEditBox.value != clamped.toString()) {
                levelEditBox.value = clamped.toString()
            }
        }
        levelEditBox.render(context, mouseX, mouseY, delta)

        matrices.pushPose()
        matrices.translate(0.0, 0.0, 200.0)
        renderCosmeticSlot(context, mouseX, mouseY)
        renderHeldSlot(context, mouseX, mouseY)
        matrices.popPose()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (handleCosmeticClick(mouseX, mouseY, button)) {
            return true
        }
        if (handleHeldClick(mouseX, mouseY, button)) {
            return true
        }
        val overLevel = levelEditBox.isMouseOver(mouseX, mouseY)
        levelEditBox.isFocused = overLevel
        if (overLevel) {
            return levelEditBox.mouseClicked(mouseX, mouseY, button)
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (cosmeticMenuOpen && isMouseOverCosmeticMenu(mouseX, mouseY)) {
            val maxRow = getCosmeticMaxScrollRow()
            if (maxRow > 0) {
                val step = when {
                    verticalAmount > 0 -> -1
                    verticalAmount < 0 -> 1
                    else -> 0
                }
                if (step != 0) {
                    cosmeticScrollRow = (cosmeticScrollRow + step).coerceIn(0, maxRow)
                }
            }
            return true
        }
        if (heldMenuOpen && isMouseOverHeldMenu(mouseX, mouseY)) {
            val maxRow = getHeldMaxScrollRow()
            if (maxRow > 0) {
                val step = when {
                    verticalAmount > 0 -> -1
                    verticalAmount < 0 -> 1
                    else -> 0
                }
                if (step != 0) {
                    heldScrollRow = (heldScrollRow + step).coerceIn(0, maxRow)
                }
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun buildRenderable(): RenderablePokemon? {
        val species = draft.species
        val form = draft.form
        val aspects = mutableSetOf<String>()
        if (draft.shiny) aspects.add(SHINY_ASPECT.aspect)
        aspects.addAll(form.aspects)
        aspects.addAll(draft.forcedAspects)
        aspects.addAll(draft.flagFeatures)
        aspects.addAll(draft.cosmeticAspects)
        draft.choiceFeatures.forEach { (key, choice) ->
            choiceAspectId(key, choice)?.let { aspects.add(it) }
        }
        when (draft.gender) {
            Gender.MALE -> aspects.add("male")
            Gender.FEMALE -> aspects.add("female")
            Gender.GENDERLESS -> aspects.add("genderless")
        }
        val heldStack = draft.heldItem
            ?.let { ResourceLocation.tryParse(it) }
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(null) }
            ?.let { item -> ItemStack(item) }
            ?: ItemStack.EMPTY
        renderablePokemon = RenderablePokemon(species, aspects, heldStack).also { recalculatePoses(it) }
        return renderablePokemon
    }

    private fun refreshCosmeticChoicesIfNeeded() {
        val key = CosmeticKey(
            speciesId = draft.species.resourceIdentifier,
            formName = draft.form.name,
            gender = draft.gender,
            shiny = draft.shiny
        )
        if (lastCosmeticKey == key) {
            return
        }

        cachedCosmeticChoices = collectCosmeticChoices(
            draft.species,
            draft.form,
            draft.gender,
            draft.shiny
        )
        lastCosmeticKey = key
        cosmeticScrollRow = 0

        if (cachedCosmeticChoices.isEmpty()) {
            draft.cosmeticItem = null
            draft.cosmeticAspects = emptySet()
            cosmeticMenuOpen = false
            return
        }

        val selected = cachedCosmeticChoices.firstOrNull { it.itemId.toString() == draft.cosmeticItem }
        if (selected == null) {
            draft.cosmeticItem = null
            draft.cosmeticAspects = emptySet()
        } else {
            draft.cosmeticAspects = selected.aspects.toSet()
        }
    }

    private fun refreshHeldChoicesIfNeeded() {
        if (heldChoicesInitialized) {
            return
        }

        val registry = BuiltInRegistries.ITEM
        val tagItems = registry.getTag(CobblemonItemTags.ANY_HELD_ITEM).orElse(null)
        val allChoices = mutableListOf<HeldChoice>()
        if (tagItems != null) {
            tagItems.forEach { holder ->
                val item = holder.value()
                val id = registry.getKey(item) ?: return@forEach
                val label = item.defaultInstance.hoverName.copy()
                allChoices.add(HeldChoice(id, label))
            }
        }

        val filtered = allChoices.filter { it.itemId.namespace == Cobblemon.MODID }
        cachedHeldChoices = (if (filtered.isNotEmpty()) filtered else allChoices)
            .sortedBy { it.label.string.lowercase() }
        heldChoicesInitialized = true
        heldScrollRow = 0

        if (cachedHeldChoices.isEmpty()) {
            draft.heldItem = null
            heldMenuOpen = false
            return
        }

        if (draft.heldItem != null && cachedHeldChoices.none { it.itemId.toString() == draft.heldItem }) {
            draft.heldItem = null
        }
    }

    private fun renderCosmeticSlot(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (cachedCosmeticChoices.isEmpty()) {
            return
        }

        val slotX = cosmeticSlotX()
        val slotY = cosmeticSlotY()
        val hovered = mouseX in slotX..(slotX + COSMETIC_SLOT_SIZE) && mouseY in slotY..(slotY + COSMETIC_SLOT_SIZE)

        val border = if (hovered) 0xFF8AD5FF.toInt() else 0xFF2C2C2C.toInt()
        context.fill(slotX - 1, slotY - 1, slotX + COSMETIC_SLOT_SIZE + 1, slotY + COSMETIC_SLOT_SIZE + 1, border)
        context.fill(slotX, slotY, slotX + COSMETIC_SLOT_SIZE, slotY + COSMETIC_SLOT_SIZE, 0xFF5B5B5B.toInt())

        getSelectedCosmeticChoice()?.let { choice ->
            val item = BuiltInRegistries.ITEM.getOptional(choice.itemId).orElse(null)
            if (item != null) {
                val stack = ItemStack(item)
                context.renderItem(stack, slotX, slotY)
                context.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY)
            }
        }

        if (hovered) {
            context.renderTooltip(Minecraft.getInstance().font, lang("ui.cosmetic_slot"), mouseX, mouseY)
        }

        if (cosmeticMenuOpen) {
            renderCosmeticMenu(context, mouseX, mouseY)
        }
    }

    private fun renderCosmeticMenu(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (cachedCosmeticChoices.isEmpty()) {
            return
        }

        val layout = getCosmeticMenuLayout()
        val menuX = layout.menuX
        val menuY = layout.menuY
        val menuWidth = layout.menuWidth
        val menuHeight = layout.menuHeight

        context.fill(menuX - 1, menuY - 1, menuX + menuWidth + 1, menuY + menuHeight + 1, 0xFF1B1B1B.toInt())
        context.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, 0xFF3B3B3B.toInt())

        val startRow = cosmeticScrollRow
        val maxIndex = cachedCosmeticChoices.size
        val registry = BuiltInRegistries.ITEM

        for (row in 0 until layout.visibleRows) {
            val actualRow = row + startRow
            val baseIndex = actualRow * COSMETIC_MENU_COLUMNS
            if (baseIndex >= maxIndex) break
            for (col in 0 until COSMETIC_MENU_COLUMNS) {
                val index = baseIndex + col
                if (index >= maxIndex) break
                val slotX = menuX + COSMETIC_SLOT_PADDING + col * COSMETIC_MENU_SLOT_STEP
                val slotY = menuY + COSMETIC_SLOT_PADDING + row * COSMETIC_MENU_SLOT_STEP
                val choice = cachedCosmeticChoices[index]
                val selected = draft.cosmeticItem == choice.itemId.toString()
                val hovered = mouseX in slotX..(slotX + COSMETIC_SLOT_SIZE) && mouseY in slotY..(slotY + COSMETIC_SLOT_SIZE)
                val border = when {
                    selected -> 0xFF6BD46B.toInt()
                    hovered -> 0xFF8AD5FF.toInt()
                    else -> 0xFF2C2C2C.toInt()
                }
                context.fill(slotX - 1, slotY - 1, slotX + COSMETIC_SLOT_SIZE + 1, slotY + COSMETIC_SLOT_SIZE + 1, border)
                context.fill(slotX, slotY, slotX + COSMETIC_SLOT_SIZE, slotY + COSMETIC_SLOT_SIZE, 0xFF5B5B5B.toInt())

                val item = registry.getOptional(choice.itemId).orElse(null)
                if (item != null) {
                    val stack = ItemStack(item)
                    context.renderItem(stack, slotX, slotY)
                    context.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY)
                    if (hovered) {
                        context.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY)
                    }
                }
            }
        }

        renderScrollbar(
            context = context,
            menuX = menuX,
            menuY = menuY,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            totalRows = layout.totalRows,
            visibleRows = layout.visibleRows,
            scrollRow = cosmeticScrollRow
        )
    }

    private fun handleCosmeticClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (cachedCosmeticChoices.isEmpty()) {
            cosmeticMenuOpen = false
            return false
        }

        val slotX = cosmeticSlotX()
        val slotY = cosmeticSlotY()
        val clickedSlot = mouseX in slotX.toDouble()..(slotX + COSMETIC_SLOT_SIZE).toDouble()
            && mouseY in slotY.toDouble()..(slotY + COSMETIC_SLOT_SIZE).toDouble()

        if (clickedSlot) {
            cosmeticMenuOpen = !cosmeticMenuOpen
            if (cosmeticMenuOpen) {
                heldMenuOpen = false
            }
            return true
        }

        if (cosmeticMenuOpen) {
            val layout = getCosmeticMenuLayout()
            val menuX = layout.menuX
            val menuY = layout.menuY
            val menuW = layout.menuWidth
            val menuH = layout.menuHeight
            val insideMenu = mouseX in menuX.toDouble()..(menuX + menuW).toDouble()
                && mouseY in menuY.toDouble()..(menuY + menuH).toDouble()
            if (!insideMenu) {
                cosmeticMenuOpen = false
                return false
            }

            val localX = mouseX.toInt() - menuX - COSMETIC_SLOT_PADDING
            val localY = mouseY.toInt() - menuY - COSMETIC_SLOT_PADDING
            if (localX < 0 || localY < 0) return true
            val col = localX / COSMETIC_MENU_SLOT_STEP
            val row = localY / COSMETIC_MENU_SLOT_STEP
            if (col !in 0 until COSMETIC_MENU_COLUMNS || row !in 0 until layout.visibleRows) return true

            val index = (row + cosmeticScrollRow) * COSMETIC_MENU_COLUMNS + col
            if (index in cachedCosmeticChoices.indices) {
                val choice = cachedCosmeticChoices[index]
                if (draft.cosmeticItem == choice.itemId.toString()) {
                    draft.cosmeticItem = null
                    draft.cosmeticAspects = emptySet()
                } else {
                    draft.cosmeticItem = choice.itemId.toString()
                    draft.cosmeticAspects = choice.aspects.toSet()
                }
            } else {
                draft.cosmeticItem = null
                draft.cosmeticAspects = emptySet()
            }
            cosmeticMenuOpen = false
            return true
        }

        return false
    }

    private fun isMouseOverCosmeticMenu(mouseX: Double, mouseY: Double): Boolean {
        if (!cosmeticMenuOpen || cachedCosmeticChoices.isEmpty()) return false
        val layout = getCosmeticMenuLayout()
        return mouseX in layout.menuX.toDouble()..(layout.menuX + layout.menuWidth).toDouble()
            && mouseY in layout.menuY.toDouble()..(layout.menuY + layout.menuHeight).toDouble()
    }

    private fun getCosmeticMaxScrollRow(): Int {
        if (cachedCosmeticChoices.isEmpty()) return 0
        val totalRows = ((cachedCosmeticChoices.size + COSMETIC_MENU_COLUMNS - 1) / COSMETIC_MENU_COLUMNS)
        val visibleRows = minOf(COSMETIC_MENU_MAX_ROWS, totalRows)
        return (totalRows - visibleRows).coerceAtLeast(0)
    }

    private fun getCosmeticMenuLayout(): CosmeticMenuLayout {
        val totalRows = ((cachedCosmeticChoices.size + COSMETIC_MENU_COLUMNS - 1) / COSMETIC_MENU_COLUMNS)
        val visibleRows = minOf(COSMETIC_MENU_MAX_ROWS, totalRows)
        val menuWidth = COSMETIC_MENU_COLUMNS * COSMETIC_MENU_SLOT_STEP + COSMETIC_SLOT_PADDING * 2
        val menuHeight = visibleRows * COSMETIC_MENU_SLOT_STEP + COSMETIC_SLOT_PADDING * 2

        var menuX = cosmeticSlotX()
        val menuY = cosmeticSlotY() + COSMETIC_SLOT_SIZE + 4

        val maxX = x + HALF_OVERLAY_WIDTH - 4
        if (menuX + menuWidth > maxX) {
            menuX = maxX - menuWidth
        }

        return CosmeticMenuLayout(menuX, menuY, menuWidth, menuHeight, visibleRows, totalRows)
    }

    private fun cosmeticSlotX(): Int = x + 6

    private fun cosmeticSlotY(): Int = y + 25 + POKEMON_PORTRAIT_HEIGHT - COSMETIC_SLOT_SIZE - 4

    private fun getSelectedCosmeticChoice(): CosmeticChoice? {
        val id = draft.cosmeticItem ?: return null
        return cachedCosmeticChoices.firstOrNull { it.itemId.toString() == id }
    }

    private fun currentHeldStack(): ItemStack {
        return renderablePokemon?.heldItem ?: ItemStack.EMPTY
    }

    private data class CosmeticMenuLayout(
        val menuX: Int,
        val menuY: Int,
        val menuWidth: Int,
        val menuHeight: Int,
        val visibleRows: Int,
        val totalRows: Int
    )

    private fun renderHeldSlot(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (cachedHeldChoices.isEmpty()) {
            return
        }

        val slotX = heldSlotX()
        val slotY = heldSlotY()
        val hovered = mouseX in slotX..(slotX + COSMETIC_SLOT_SIZE) && mouseY in slotY..(slotY + COSMETIC_SLOT_SIZE)

        val border = if (hovered) 0xFF8AD5FF.toInt() else 0xFF2C2C2C.toInt()
        context.fill(slotX - 1, slotY - 1, slotX + COSMETIC_SLOT_SIZE + 1, slotY + COSMETIC_SLOT_SIZE + 1, border)
        context.fill(slotX, slotY, slotX + COSMETIC_SLOT_SIZE, slotY + COSMETIC_SLOT_SIZE, 0xFF5B5B5B.toInt())

        getSelectedHeldChoice()?.let { choice ->
            val item = BuiltInRegistries.ITEM.getOptional(choice.itemId).orElse(null)
            if (item != null) {
                val stack = ItemStack(item)
                context.renderItem(stack, slotX, slotY)
                context.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY)
            }
        }

        if (hovered) {
            context.renderTooltip(Minecraft.getInstance().font, lang("ui.held_item_slot"), mouseX, mouseY)
        }

        if (heldMenuOpen) {
            renderHeldMenu(context, mouseX, mouseY)
        }
    }

    private fun renderHeldMenu(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (cachedHeldChoices.isEmpty()) {
            return
        }

        val layout = getHeldMenuLayout()
        val menuX = layout.menuX
        val menuY = layout.menuY
        val menuWidth = layout.menuWidth
        val menuHeight = layout.menuHeight

        context.fill(menuX - 1, menuY - 1, menuX + menuWidth + 1, menuY + menuHeight + 1, 0xFF1B1B1B.toInt())
        context.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, 0xFF3B3B3B.toInt())

        val startRow = heldScrollRow
        val maxIndex = cachedHeldChoices.size
        val registry = BuiltInRegistries.ITEM

        for (row in 0 until layout.visibleRows) {
            val actualRow = row + startRow
            val baseIndex = actualRow * COSMETIC_MENU_COLUMNS
            if (baseIndex >= maxIndex) break
            for (col in 0 until COSMETIC_MENU_COLUMNS) {
                val index = baseIndex + col
                if (index >= maxIndex) break
                val slotX = menuX + COSMETIC_SLOT_PADDING + col * COSMETIC_MENU_SLOT_STEP
                val slotY = menuY + COSMETIC_SLOT_PADDING + row * COSMETIC_MENU_SLOT_STEP
                val choice = cachedHeldChoices[index]
                val selected = draft.heldItem == choice.itemId.toString()
                val hovered = mouseX in slotX..(slotX + COSMETIC_SLOT_SIZE) && mouseY in slotY..(slotY + COSMETIC_SLOT_SIZE)
                val border = when {
                    selected -> 0xFF6BD46B.toInt()
                    hovered -> 0xFF8AD5FF.toInt()
                    else -> 0xFF2C2C2C.toInt()
                }
                context.fill(slotX - 1, slotY - 1, slotX + COSMETIC_SLOT_SIZE + 1, slotY + COSMETIC_SLOT_SIZE + 1, border)
                context.fill(slotX, slotY, slotX + COSMETIC_SLOT_SIZE, slotY + COSMETIC_SLOT_SIZE, 0xFF5B5B5B.toInt())

                val item = registry.getOptional(choice.itemId).orElse(null)
                if (item != null) {
                    val stack = ItemStack(item)
                    context.renderItem(stack, slotX, slotY)
                    context.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY)
                    if (hovered) {
                        context.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY)
                    }
                }
            }
        }

        renderScrollbar(
            context = context,
            menuX = menuX,
            menuY = menuY,
            menuWidth = menuWidth,
            menuHeight = menuHeight,
            totalRows = layout.totalRows,
            visibleRows = layout.visibleRows,
            scrollRow = heldScrollRow
        )
    }

    private fun handleHeldClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (cachedHeldChoices.isEmpty()) {
            heldMenuOpen = false
            return false
        }

        val slotX = heldSlotX()
        val slotY = heldSlotY()
        val clickedSlot = mouseX in slotX.toDouble()..(slotX + COSMETIC_SLOT_SIZE).toDouble()
            && mouseY in slotY.toDouble()..(slotY + COSMETIC_SLOT_SIZE).toDouble()

        if (clickedSlot) {
            heldMenuOpen = !heldMenuOpen
            if (heldMenuOpen) {
                cosmeticMenuOpen = false
            }
            return true
        }

        if (heldMenuOpen) {
            val layout = getHeldMenuLayout()
            val menuX = layout.menuX
            val menuY = layout.menuY
            val menuW = layout.menuWidth
            val menuH = layout.menuHeight
            val insideMenu = mouseX in menuX.toDouble()..(menuX + menuW).toDouble()
                && mouseY in menuY.toDouble()..(menuY + menuH).toDouble()
            if (!insideMenu) {
                heldMenuOpen = false
                return false
            }

            val localX = mouseX.toInt() - menuX - COSMETIC_SLOT_PADDING
            val localY = mouseY.toInt() - menuY - COSMETIC_SLOT_PADDING
            if (localX < 0 || localY < 0) return true
            val col = localX / COSMETIC_MENU_SLOT_STEP
            val row = localY / COSMETIC_MENU_SLOT_STEP
            if (col !in 0 until COSMETIC_MENU_COLUMNS || row !in 0 until layout.visibleRows) return true

            val index = (row + heldScrollRow) * COSMETIC_MENU_COLUMNS + col
            if (index in cachedHeldChoices.indices) {
                val choice = cachedHeldChoices[index]
                draft.heldItem = if (draft.heldItem == choice.itemId.toString()) {
                    null
                } else {
                    choice.itemId.toString()
                }
            } else {
                draft.heldItem = null
            }
            heldMenuOpen = false
            return true
        }

        return false
    }

    private fun isMouseOverHeldMenu(mouseX: Double, mouseY: Double): Boolean {
        if (!heldMenuOpen || cachedHeldChoices.isEmpty()) return false
        val layout = getHeldMenuLayout()
        return mouseX in layout.menuX.toDouble()..(layout.menuX + layout.menuWidth).toDouble()
            && mouseY in layout.menuY.toDouble()..(layout.menuY + layout.menuHeight).toDouble()
    }

    private fun getHeldMaxScrollRow(): Int {
        if (cachedHeldChoices.isEmpty()) return 0
        val totalRows = ((cachedHeldChoices.size + COSMETIC_MENU_COLUMNS - 1) / COSMETIC_MENU_COLUMNS)
        val visibleRows = minOf(COSMETIC_MENU_MAX_ROWS, totalRows)
        return (totalRows - visibleRows).coerceAtLeast(0)
    }

    private fun getHeldMenuLayout(): HeldMenuLayout {
        val totalRows = ((cachedHeldChoices.size + COSMETIC_MENU_COLUMNS - 1) / COSMETIC_MENU_COLUMNS)
        val visibleRows = minOf(COSMETIC_MENU_MAX_ROWS, totalRows)
        val menuWidth = COSMETIC_MENU_COLUMNS * COSMETIC_MENU_SLOT_STEP + COSMETIC_SLOT_PADDING * 2
        val menuHeight = visibleRows * COSMETIC_MENU_SLOT_STEP + COSMETIC_SLOT_PADDING * 2

        var menuX = heldSlotX()
        val menuY = heldSlotY() + COSMETIC_SLOT_SIZE + 4

        val maxX = x + HALF_OVERLAY_WIDTH - 4
        if (menuX + menuWidth > maxX) {
            menuX = maxX - menuWidth
        }

        return HeldMenuLayout(menuX, menuY, menuWidth, menuHeight, visibleRows, totalRows)
    }

    private fun heldSlotX(): Int = cosmeticSlotX() + COSMETIC_SLOT_SIZE + COSMETIC_SLOT_PADDING

    private fun heldSlotY(): Int = cosmeticSlotY()

    private fun getSelectedHeldChoice(): HeldChoice? {
        val id = draft.heldItem ?: return null
        return cachedHeldChoices.firstOrNull { it.itemId.toString() == id }
    }

    private data class HeldMenuLayout(
        val menuX: Int,
        val menuY: Int,
        val menuWidth: Int,
        val menuHeight: Int,
        val visibleRows: Int,
        val totalRows: Int
    )

    private fun renderScrollbar(
        context: GuiGraphics,
        menuX: Int,
        menuY: Int,
        menuWidth: Int,
        menuHeight: Int,
        totalRows: Int,
        visibleRows: Int,
        scrollRow: Int
    ) {
        if (totalRows <= visibleRows) {
            return
        }

        val trackX = menuX + menuWidth - 4
        val trackY = menuY + COSMETIC_SLOT_PADDING
        val trackHeight = menuHeight - COSMETIC_SLOT_PADDING * 2
        context.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0xFF2C2C2C.toInt())

        val maxScroll = (totalRows - visibleRows).coerceAtLeast(1)
        val thumbHeight = maxOf(6, (trackHeight * visibleRows) / totalRows)
        val thumbY = trackY + ((trackHeight - thumbHeight) * scrollRow / maxScroll)
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF8AD5FF.toInt())
    }

    private fun switchForm(nextIndex: Boolean) {
        val forms = if (draft.species.forms.isEmpty()) listOf(draft.species.standardForm) else draft.species.forms
        val index = forms.indexOf(draft.form).coerceAtLeast(0)
        val next = if (nextIndex) {
            if (index < forms.lastIndex) index + 1 else 0
        } else {
            if (index > 0) index - 1 else forms.lastIndex
        }
        draft.form = forms[next]
        draft.gender = PokeEditDraft.defaultGenderFor(draft.form)
        onDraftChanged()
    }

    private fun toggleGender() {
        val ratio = draft.form.maleRatio
        if (ratio !in 0F..1F) {
            draft.gender = Gender.GENDERLESS
            return
        }
        draft.gender = if (draft.gender == Gender.MALE) Gender.FEMALE else Gender.MALE
        genderButton.resource = if (draft.gender == Gender.FEMALE) buttonGenderFemale else buttonGenderMale
    }

    private fun toggleShiny() {
        draft.shiny = !draft.shiny
        shinyButton.resource = if (draft.shiny) buttonShiny else buttonNone
    }

    private fun switchPose() {
        selectedPoseIndex = if (selectedPoseIndex < poseList.lastIndex) selectedPoseIndex + 1 else 0
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(com.cobblemon.mod.common.CobblemonSounds.GUI_CLICK, 1.0F))
    }

    private fun getPlatformResource(primaryTypeId: String?): ResourceLocation? {
        if (primaryTypeId == null) return null
        return try {
            cobblemonResource("textures/gui/pokedex/platform_base_${primaryTypeId}.png")
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private fun recalculatePoses(renderablePokemon: RenderablePokemon) {
        val poseState = FloatingState().also { it.currentAspects = renderablePokemon.aspects }
        val poser = VaryingModelRepository.getPoser(renderablePokemon.species.resourceIdentifier, poseState)
        poseState.currentModel = poser
        poseList = poser.poses
            .map { it.value.poseTypes.minBy { it.ordinal } }
            .toSet()
            .filterNot { it in PoseType.SHOULDER_POSES }
            .sortedBy { it.ordinal }
            .toTypedArray()
    }
}
