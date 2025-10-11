package com.cobblemon.mod.common.client.gui.pokedex.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokedex.entry.PokedexForm
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCALE
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.SCROLL_SLOT_SIZE
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphics
import org.joml.Quaternionf
import org.joml.Vector3f

abstract class PokemonSlotWidget {
    companion object {
        private val slotResource = cobblemonResource("textures/gui/pokedex/pokedex_slot.png")
        private val slotHighlight = cobblemonResource("textures/gui/pokedex/slot_select.png")
        private val caughtIcon = cobblemonResource("textures/gui/pokedex/caught_icon_small.png")
        private val unknownIcon = cobblemonResource("textures/gui/pokedex/pokedex_slot_unknown.png")
        private val unimplementedIcon = cobblemonResource("textures/gui/pokedex/pokedex_slot_unimplemented.png")

        fun render(
            context: GuiGraphics,
            x: Int,
            y: Int,
            hovered: Boolean,
            discoveryLevel: PokedexEntryProgress,
            pokedexEntry: PokedexEntry,
            pokedexForm: PokedexForm? = null,
            showDexNumber: Boolean = true,
            showCaughtIcon: Boolean = true,
            gender: Gender? = null,
            shiny: Boolean? = null,
            aspects: Set<String>? = null,
        ) {
            val state = FloatingState()
            val species = PokemonSpecies.getByIdentifier(pokedexEntry.speciesId)
            // FIXME: This may not work properly when accounting for custom pokemon with the same dex number
            val pokemonNumber = (species?.nationalPokedexNumber?.toString() ?: "0").padStart(4, '0')

            val speciesNumber = pokemonNumber.text()
            val formToRender = pokedexForm ?: CobblemonClient.clientPokedexData.getEncounteredForms(pokedexEntry).firstOrNull()
            val shouldDrawMon = formToRender != null

            if (species == null) {
                return
            }

            val matrices = context.pose()

            blitk(
                matrixStack = matrices,
                texture = slotResource,
                x = x,
                y = y,
                width = SCROLL_SLOT_SIZE,
                height = SCROLL_SLOT_SIZE
            )

            if (hovered) {
                blitk(
                    matrixStack = matrices,
                    texture = slotHighlight,
                    x = x,
                    y = y,
                    width = SCROLL_SLOT_SIZE,
                    height = SCROLL_SLOT_SIZE,
                    vOffset = SCROLL_SLOT_SIZE,
                    textureHeight = SCROLL_SLOT_SIZE * 2
                )
            }

            if (shouldDrawMon) {
                val firstVisibleGender = gender ?: CobblemonClient.clientPokedexData.getSeenGenders(pokedexEntry, formToRender).firstOrNull()
                val shinyStates = CobblemonClient.clientPokedexData.getSeenShinyStates(pokedexEntry, formToRender)
                val firstVisibleShiny = shiny ?: (shinyStates.count() == 1 && shinyStates.first() == "shiny")
                val formAspects = species.forms.firstOrNull { it.name.equals(formToRender.displayForm, ignoreCase = true) }?.aspects ?: species.standardForm.aspects
                val seenAspects = CobblemonClient.clientPokedexData.getSeenAspects(pokedexEntry)
                val variationAspects = pokedexEntry.variations.mapNotNull { it.aspects.firstOrNull { it in seenAspects } }
                context.enableScissor(
                    x + 1,
                    y + 1,
                    x + SCROLL_SLOT_SIZE - 1,
                    y + SCROLL_SLOT_SIZE - 2
                )
                val aspectsToDraw = (pokedexEntry.displayAspects + variationAspects + formAspects + (firstVisibleGender ?: Gender.GENDERLESS).name.lowercase()).toMutableSet()
                if (firstVisibleShiny) {
                    aspectsToDraw.add("shiny")
                }
                matrices.pushPose()
                matrices.translate(x + (SCROLL_SLOT_SIZE / 2.0), y + 1.0, 0.0)
                matrices.scale(2.5F, 2.5F, 1F)
                drawProfilePokemon(
                    renderablePokemon = RenderablePokemon(species, aspects ?: aspectsToDraw),
                    matrixStack = matrices,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    partialTicks = 0F,
                    scale = 4.5F,
                )
                matrices.popPose()
                context.disableScissor()
            } else {
                blitk(
                    matrixStack = matrices,
                    texture = unknownIcon,
                    x = x + 8.5,
                    y = y + 9,
                    width = 8,
                    height = 10
                )

                if (!species.implemented) {
                    blitk(
                        matrixStack = matrices,
                        texture = unimplementedIcon,
                        x = (x + 14) / SCALE,
                        y = (y + 15.5) / SCALE,
                        width = 7,
                        height = 7,
                        scale = SCALE
                    )
                }
            }

            // Ensure elements are not hidden behind Pokémon render
            matrices.pushPose()
            matrices.translate(0.0, 0.0, 100.0)

            if (showDexNumber) {
                drawScaledText(
                    context = context,
                    text = speciesNumber,
                    x = x + 1.5,
                    y = y + 2.5,
                    shadow = true,
                    scale = SCALE
                )
            }

            if (showCaughtIcon && discoveryLevel == PokedexEntryProgress.CAUGHT) {
                blitk(
                    matrixStack = matrices,
                    texture = caughtIcon,
                    x = (x + 18) / SCALE,
                    y = (y + 1.5) / SCALE,
                    width = 11,
                    height = 11,
                    scale = SCALE
                )
            }

            matrices.popPose()
        }
    }
}