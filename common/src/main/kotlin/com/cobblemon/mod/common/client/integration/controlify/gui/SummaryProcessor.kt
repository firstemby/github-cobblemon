package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.PartyWidget
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

    // Main Screens
    var selectedMoveIndex = 0

    //Side Screens
    var selectedPartyIndex = 0

    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DEFAULT
    }

    override fun handleBack(controller: ControllerEntity) {
        screen.playSound(CobblemonSounds.GUI_CLICK)
        screen.saveActiveMark()
        screen.saveMarkings()
        Minecraft.getInstance().setScreen(null)
    }

    override fun handlePress(controller: ControllerEntity) {
        when (panelFocus) {
            RIGHT_PANEL -> {
                when (screen.sideScreenIndex) {
                    Summary.PARTY -> {
                        screen.switchSelection(selectedPartyIndex)
                        screen.playSound(CobblemonSounds.GUI_CLICK)
                    }
                }
            }
        }
    }

    override fun handleGUINav(controller: ControllerEntity, direction: Direction) {
        when (panelFocus) {
            LEFT_PANEL -> {}
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> handleMovesNav(direction)
                    else -> {
                        if (direction == Direction.EAST) {
                            panelFocus = LEFT_PANEL
                        } else if (direction == Direction.WEST) {
                            panelFocus = RIGHT_PANEL
                        }
                    }
                }
            }
            RIGHT_PANEL -> {
                when (screen.sideScreenIndex) {
                    Summary.PARTY -> handlePartyNavigation(direction)
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
            selectedMoveIndex += moveIndexOffset
            if (selectedMoveIndex < 0) {
                selectedMoveIndex = movesWidget.moves.size - 1
            } else if (selectedMoveIndex > movesWidget.moves.size - 1) {
                selectedMoveIndex = 0
            }

            movesWidget.selectMove(movesWidget.moves[selectedMoveIndex].move)
            screen.playSound(CobblemonSounds.GUI_CLICK)
        }
    }

    private fun handlePartyNavigation(direction: Direction) {
        if (direction == Direction.EAST) {
            if (selectedPartyIndex.mod(2) == 0) {
                panelFocus = CENTER_PANEL
            } else {
                selectedPartyIndex -= 1
            }
            return
        }
        if (direction == Direction.WEST) {
            if (selectedPartyIndex.mod(2) == 0) {
                selectedPartyIndex += 1
            }
            return
        }

        if (direction == Direction.NORTH) {
            selectedPartyIndex -= 2
            if (selectedPartyIndex < 0) selectedPartyIndex += 2
        } else if (direction == Direction.SOUTH) {
            selectedPartyIndex += 2
            if (selectedPartyIndex > 5) selectedPartyIndex -= 2
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
        ensureMoveSelected()
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
        ensureMoveSelected()
    }

    private fun ensureMoveSelected() {
        val movesWidget = screen.mainScreen as? MovesWidget ?: return
        movesWidget.selectMove(movesWidget.moves[selectedMoveIndex].move)
    }
}