/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.bag

import com.cobblemon.mod.common.CobblemonNetwork.sendToServer
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.bag.BagCategories
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.drawScaledTextJustifiedRight
import com.cobblemon.mod.common.client.render.getDepletableRedGreen
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.item.bag.CobblemonBagMenu
import com.cobblemon.mod.common.net.messages.client.bag.UpdateBagMenuDataPacket
import com.cobblemon.mod.common.net.messages.client.bag.UseItemOnPokemonFromBagPacket
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import juuxel.adorn.util.Colors
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.min
import kotlin.math.roundToInt

@Environment(EnvType.CLIENT)
class CobblemonBagScreen(
    menu: CobblemonBagMenu,
    playerInventory: Inventory,
    title: Component
): AbstractContainerScreen<CobblemonBagMenu>(
    menu,
    playerInventory,
    Component.translatable("cobblemon.container.bag")
) {
    companion object {
        private const val BACKGROUND_HEIGHT = 197
        private const val BACKGROUND_WIDTH = 184

        private val BACKGROUND = cobblemonResource("textures/gui/bag/bag_base.png")
        private val SCROLL_TRACK = cobblemonResource("textures/gui/bag/scrollbar_track.png")
        private val SCROLL_SLIDE = cobblemonResource("textures/gui/bag/scrollbar_slide.png")
        private val PARTY_SLOT_BASE = cobblemonResource("textures/gui/bag/party_slot_base.png")
        private val PARTY_SLOT_COLLAPSED = cobblemonResource("textures/gui/bag/party_slot_collapsed.png")
        private val PARTY_SLOT = cobblemonResource("textures/gui/bag/party_slot.png")
        private val PARTY_SLOT_FAINTED = cobblemonResource("textures/gui/bag/party_slot_fainted.png")
        private val GENDER_ICON_MALE = cobblemonResource("textures/gui/party/party_gender_male.png")
        private val GENDER_ICON_FEMALE = cobblemonResource("textures/gui/party/party_gender_female.png")
    }

    val categoriesWidgets = mutableListOf<BagCategoryButton>()
    lateinit var allItemsCategoryWidget: BagCategoryButton
    lateinit var leftCategoryPageButton: BagCategoryPageButton
    lateinit var rightCategoryPageButton: BagCategoryPageButton
    var currentCategoryPage = 0
    var lastCategory = menu.currentlySelectedCategory
    var lastScroll = menu.scroll
    var scrolling = false

    init {
        this.imageWidth = BACKGROUND_WIDTH + 69
        this.imageHeight = BACKGROUND_HEIGHT + 27
    }

    override fun init() {
        super.init()
        this.leftPos -= 34

        for (i in 0..3) {
            if (i < BagCategories.getOrderedCategoriesList().size) {
                categoriesWidgets.add(
                    BagCategoryButton(leftPos + 76 + i * 33, topPos, this, i) {
                        if (menu.currentlySelectedCategory != i) {
                            menu.changeCategory(i)
                            playSound(CobblemonSounds.GUI_CLICK)
                        }
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }

        allItemsCategoryWidget = BagCategoryButton(leftPos + 214, topPos, this, -1) {
            if (menu.currentlySelectedCategory != -1) {
                menu.changeCategory(-1)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }

        leftCategoryPageButton = BagCategoryPageButton(leftPos + 218, topPos + 32, this, true) {
            if (leftCategoryPageButton.enabled) {
                cycleCategoryPage(false)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }
        rightCategoryPageButton = BagCategoryPageButton(leftPos + 229, topPos + 32, this, false) {
            if (rightCategoryPageButton.enabled) {
                cycleCategoryPage(true)
                playSound(CobblemonSounds.GUI_CLICK)
            }
        }.also { addRenderableWidget(it) }
    }

    override fun renderBg(context: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {}
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {}

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(context)
        
        val matrices = context.pose()

        // Background
        blitk(
            matrixStack = matrices,
            texture = BACKGROUND,
            x = leftPos + 69, y = topPos + 27,
            width = BACKGROUND_WIDTH, height = BACKGROUND_HEIGHT
        )

        //Scroll
        val scrollYOffset = if (menu.maxScroll == 0) {
            if (menu.scroll == 0) 0 else 53
        } else ((min(menu.scroll, menu.maxScroll).toFloat() / menu.maxScroll) * 53).toInt()
        val trackHeight = 71 - scrollYOffset - 13

        if (trackHeight > 0) {
            blitk(
                matrixStack = matrices,
                texture = SCROLL_TRACK,
                x = leftPos + 230, y = topPos + 49 + (71 - trackHeight),
                width = 9, height = trackHeight,
                textureHeight = 71,
                vOffset = 71 - trackHeight
            )
        }

        blitk(
            matrixStack = matrices,
            texture = SCROLL_SLIDE,
            x = leftPos + 230, y = topPos + 49 + scrollYOffset,
            width = 9, height = 17
        )

        // Pokemon
        val party = CobblemonClient.storage.party
        for (i in 0..5) {
            val pokemon = party.get(i)
            val slotYPosition = topPos + i * 33 + 27

            if (pokemon != null) {
                blitk(
                    matrixStack = matrices,
                    texture = PARTY_SLOT_BASE,
                    x = leftPos, y = slotYPosition,
                    width = 67, height = 31
                )

                val slotWidth = 32
                val slotLeft = leftPos + 11
                blitk(
                    matrixStack = matrices,
                    texture = if (pokemon.isFainted()) PARTY_SLOT_FAINTED else PARTY_SLOT,
                    x = slotLeft, y = slotYPosition,
                    width = slotWidth, height = 26,
                    textureHeight = 52,
                    vOffset = if (!menu.carried.isEmpty && partySlotHovered(i, mouseX, mouseY)) 26 else 0
                )

                val expForThisLevel = pokemon.experience - pokemon.experienceGroup.getExperience(pokemon.level)
                val expToNextLevel = pokemon.experienceGroup.getExperience(pokemon.level + 1) - pokemon.experienceGroup.getExperience(pokemon.level)
                val expRatio = expForThisLevel.toFloat() / expToNextLevel

                val expBarHeight = expRatio * 20

                if (expBarHeight > 0) {
                    blitk(
                        matrixStack = matrices,
                        texture = CobblemonResources.WHITE,
                        x = leftPos + 6, y = slotYPosition + 24 - expBarHeight,
                        width = 1, height = expBarHeight,
                        red = 0.2, green = 0.65, blue = 0.84
                    )
                }

                val hpRatio = pokemon.currentHealth.toFloat() / pokemon.maxHealth
                val hpBarHeight = hpRatio * 20
                val (red, green) = getDepletableRedGreen(hpRatio)

                if (hpBarHeight > 0) {
                    blitk(
                        matrixStack = matrices,
                        texture = CobblemonResources.WHITE,
                        x = leftPos + 8, y = slotYPosition + 24 - hpBarHeight,
                        width = 2, height = hpBarHeight,
                        red = red * 0.8F, green = green * 0.8F, blue = 0.27F
                    )
                }

                // Ball Icon
                val ballIcon = cobblemonResource("textures/gui/ball/" + pokemon.caughtBall.name.path + ".png")
                blitk(
                    matrixStack = matrices,
                    texture = ballIcon,
                    x = (leftPos + 3.5F) / 0.5F, y = (slotYPosition + 23) / 0.5F,
                    width = 18, height = 22,
                    textureHeight = 44,
                    scale = 0.5F
                )

                //Gender
                if (pokemon.gender != Gender.GENDERLESS) {
                    blitk(
                        matrixStack = matrices,
                        texture = if (pokemon.gender == Gender.MALE) GENDER_ICON_MALE else GENDER_ICON_FEMALE,
                        x = (leftPos + 62) / 0.5F, y = (slotYPosition + 26) / 0.5F,
                        width = 5, height = 7,
                        scale = 0.5F
                    )
                }

                //Status
                val status = pokemon.status?.status
                if (!pokemon.isFainted() && status != null) {
                    blitk(
                        matrixStack = matrices,
                        texture = cobblemonResource("textures/gui/bag/status_${status.showdownName}.png"),
                        x = leftPos + 1, y = slotYPosition + 9,
                        width = 4, height = 14
                    )
                }

                matrices.pushPose()
                matrices.translate(slotLeft + ((slotWidth - 8) / 2.0), slotYPosition.toDouble() - 6, 0.0)
                matrices.scale(3.5F, 3.5F, 1F)

                val state = FloatingState()
                state.currentAspects = pokemon.aspects
                drawProfilePokemon(
                    species = pokemon.species.resourceIdentifier,
                    matrixStack = matrices,
                    rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(13F, 35F, 0F)),
                    state = state,
                    scale = 4.5F,
                    partialTicks = 0F
                )
                matrices.popPose()

                matrices.pushPose()
                matrices.translate(0.0, 0.0, 100.0)
                // Level and Name and Gender
                drawScaledText(
                    context = context,
                    text = pokemon.getDisplayName(),
                    x = leftPos + 14, y = slotYPosition + 26,
                    scale = 0.5F
                )

                drawScaledTextJustifiedRight(
                    context = context,
                    text = lang("ui.lv"),
                    x = leftPos + 40, y = slotYPosition + 14,
                    shadow = true,
                    scale = 0.5F
                )

                drawScaledTextJustifiedRight(
                    context = context,
                    text = pokemon.level.toString().text(),
                    x = leftPos + 40, y = slotYPosition + 19,
                    shadow = true,
                    scale = 0.5F
                )
                matrices.popPose()
            } else {
                blitk(
                    matrixStack = matrices,
                    texture = PARTY_SLOT_COLLAPSED,
                    x = leftPos + 64, y = slotYPosition,
                    width = 5, height = 31
                )
            }
        }

        //Titles
        context.drawString(font, title, leftPos + 80, topPos + 33, Colors.WHITE, false)
        context.drawString(font, playerInventoryTitle, leftPos + 80, topPos + 129, 4210752, false)

        super.render(context, mouseX, mouseY, partialTick)
        renderTooltip(context, mouseX, mouseY)
    }

    fun partySlotHovered(index: Int, mouseX: Int, mouseY: Int): Boolean {
        val left = leftPos + 11
        val top = topPos + index * 33 + 27
        return mouseX in left..(left + 32) && mouseY in top..(top + 26)
    }

    fun cycleCategoryPage(next: Boolean) {
        val orderedCategories = BagCategories.getOrderedCategoriesList()
        currentCategoryPage = (currentCategoryPage + if (next) 1 else -1).coerceIn(0, (orderedCategories.size - 1) / 4)
        categoriesWidgets.forEach {
            removeWidget(it)
        }
        categoriesWidgets.clear()
        for (i in 0..3) {
            val adjustedIndex = i + 4 * currentCategoryPage
            if (adjustedIndex < orderedCategories.size) {
                categoriesWidgets.add(
                    BagCategoryButton(leftPos + 76 + i * 33, topPos, this, adjustedIndex) {
                        menu.changeCategory(adjustedIndex)
                    }.also { addRenderableWidget(it) }
                )
            } else break
        }
        leftCategoryPageButton.enabled = currentCategoryPage > 0
        rightCategoryPageButton.enabled = currentCategoryPage < (orderedCategories.size - 1) / 4
    }

    fun updateScrollValue(mouseY: Double) {
        val adjustedMouseY = mouseY - topPos - 49.0
        if (adjustedMouseY < 0) {
            menu.scroll = 0
        } else if (adjustedMouseY > 71) {
            menu.scroll = menu.maxScroll
        } else {
            menu.scroll = ((adjustedMouseY / 71.0) * (menu.maxScroll)).roundToInt()
        }
        menu.setupSlotsBasedOnContent()
    }

    override fun slotClicked(slot: Slot?, slotId: Int, mouseButton: Int, type: ClickType) {
        if (slotId in CobblemonBagMenu.BAG_SLOTS && (lastCategory != menu.currentlySelectedCategory || lastScroll != menu.scroll)) {
            lastCategory = menu.currentlySelectedCategory
            lastScroll = menu.scroll
            sendToServer(UpdateBagMenuDataPacket(menu.currentlySelectedCategory, menu.scroll)) // Synchronize this data
        }
        super.slotClicked(slot, slotId, mouseButton, type)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY < 0 && menu.scroll < menu.maxScroll) {
            menu.scroll += 1
        } else if (scrollY > 0 && menu.scroll > 0) {
            menu.scroll -= 1
        }
        menu.setupSlotsBasedOnContent()
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX in (leftPos + 229.0)..(leftPos + 240.0) && mouseY in (topPos + 49.0)..(topPos + 120.0)) {
            scrolling = true
            updateScrollValue(mouseY)
            return true
        }
        val party = CobblemonClient.storage.party
        for (i in 0..5) {
            if (party.get(i) != null && partySlotHovered(i, mouseX.toInt(), mouseY.toInt())) {
                sendToServer(UseItemOnPokemonFromBagPacket(i))
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        scrolling = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (scrolling) {
            updateScrollValue(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }
}