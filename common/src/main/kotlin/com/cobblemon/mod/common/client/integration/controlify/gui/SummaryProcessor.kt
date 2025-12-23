package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.screenop.ScreenControllerEventListener
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Direction

class SummaryProcessor(override val screen: Summary) : CobblemonGUIProcessor(screen) {
    companion object {
        const val LEFT_PANEL = 0
        const val CENTER_PANEL = 1
        const val RIGHT_PANEL = 2
    }

    override val useBase: Boolean
        get() = false

    var panelFocus = CENTER_PANEL

    var selectedMoveIndex = 0

    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DEFAULT
    }

    override fun handleBack(controller: ControllerEntity) {
        screen.playSound(CobblemonSounds.GUI_CLICK)
        screen.saveActiveMark()
        screen.saveMarkings()
        Minecraft.getInstance().setScreen(null)
    }

    override fun handleGUINav(controller: ControllerEntity, direction: Direction) {
        when (panelFocus) {
            LEFT_PANEL -> {}
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> {
                        handleMovesNav(direction)
                    }
                    else -> {
                        if (direction == Direction.EAST) {
                            panelFocus = LEFT_PANEL
                        } else if (direction == Direction.WEST) {
                            panelFocus = RIGHT_PANEL
                        }
                    }
                }
            }
        }
    }

    private fun handleMovesNav(direction: Direction) {
        if (direction == Direction.EAST) {
            panelFocus = LEFT_PANEL
            return
        }
        if (direction == Direction.WEST) {
            panelFocus = RIGHT_PANEL
            return
        }

        val movesWidget = screen.mainScreen as? MovesWidget ?: return

        var moveIndexOffset: Int? = null
        if (direction == Direction.NORTH) {
            moveIndexOffset = -1
        } else if (direction == Direction.SOUTH) {
            moveIndexOffset = 1
        }

        if (moveIndexOffset != null) {
            var selectedMoveIndex = movesWidget
        }
    }

    override fun handleBaseTabNav(controller: ControllerEntity, input: Int) {
        var newTab = screen.mainScreenIndex + input
        if (newTab < 0) {
            newTab = screen.summaryTabs.size - 1
        } else if (newTab > screen.summaryTabs.size - 1) {
            newTab = 0
        }
        screen.playSound(CobblemonSounds.GUI_CLICK)
        screen.displayMainScreen(newTab)
    }

    override fun handleSecondaryTabNav(controller: ControllerEntity, input: Int) {
        val pokemonIndex = screen.party.indexOf(screen.selectedPokemon)
        val indexList = screen.party.filter { it != null }.map { screen.party.indexOf(it) }.toMutableList()
        var index = indexList.indexOf(pokemonIndex) + input
        if (index < 0) {
            index = indexList.size - 1
        } else if (index > indexList.size - 1) {
            index = 0
        }
        screen.playSound(CobblemonSounds.GUI_CLICK)
        screen.switchSelection(indexList[index])
    }

    override fun handleComponentNavigation(controller: ControllerEntity) {} //Reset
}