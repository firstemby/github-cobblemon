/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.bedrockk.molang.runtime.MoLangRuntime
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
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
    }

    private val runtime = MoLangRuntime().setupClient().setup().also {
        it.environment.query.addFunction("get_pokedex") { null }
    }

    private val state = FloatingState()
    private var rotationY = 30F
    private var poseList: Array<PoseType> = arrayOf(PoseType.PROFILE, PoseType.WALK, PoseType.SLEEP)
    private var selectedPoseIndex: Int = 0

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
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val overLevel = levelEditBox.isMouseOver(mouseX, mouseY)
        levelEditBox.isFocused = overLevel
        if (overLevel) {
            return levelEditBox.mouseClicked(mouseX, mouseY, button)
        }
        return super.mouseClicked(mouseX, mouseY, button)
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
        renderablePokemon = RenderablePokemon(species, aspects).also { recalculatePoses(it) }
        return renderablePokemon
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
