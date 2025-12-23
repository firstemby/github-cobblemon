package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.EvolutionSelectScreen
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSwapScreen
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MovesWidget
import dev.isxander.controlify.Controlify
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.screenop.ComponentProcessorProvider
import dev.isxander.controlify.screenop.compat.vanilla.EditBoxComponentProcessor
import dev.isxander.controlify.screenop.keyboard.KeyboardOverlayScreen
import dev.isxander.controlify.screenop.keyboard.KeyboardWidget
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction

class SummaryProcessor(override val screen: Summary) : CobblemonGUIProcessor(screen) {
    companion object {
        const val LEFT_PANEL = 0
        const val CENTER_PANEL = 1
        const val RIGHT_PANEL = 2

        const val LP_NAME = 0
        const val LP_HELD_ITEM = 1
        const val LP_EVOLVE = 2

        const val LP_LIST_SIZE = 3
    }

    override val useBase: Boolean
        get() = false

    var keyboardWidget: KeyboardOverlayScreen? = null

    var panelFocus = CENTER_PANEL

    // Left Panel
    var leftPanelIndex = LP_NAME

    // Main Screens
    var selectedMoveIndex = 0
    var changeMoveOrder = false

    //Side Screens
    var selectedPartyIndex = 0
    var selectedMoveSwitchIndex = 0
    var selectedEvolutionIndex = 0

    override fun onWidgetRebuild() {
        screen.onMainScreenSet = {
            ensureMoveSelected()
        }
    }

    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DEFAULT
    }

    override fun onControllerUpdate(controller: ControllerEntity) {
        screen.nicknameEntryWidget.isFocused = false
        super.onControllerUpdate(controller)
    }

    override fun handleBack(controller: ControllerEntity) {
        if (changeMoveOrder) {
            changeMoveOrder = false
            screen.playSound(CobblemonSounds.GUI_CLICK)
            return
        }
        if (screen.sideScreenIndex == Summary.MOVE_SWAP) {
            screen.displaySideScreen(Summary.PARTY)
            focusPanel(CENTER_PANEL)
            screen.playSound(CobblemonSounds.GUI_CLICK)
            return
        }

        screen.playSound(CobblemonSounds.GUI_CLICK)
        screen.saveActiveMark()
        screen.saveMarkings()
        Minecraft.getInstance().setScreen(null)
    }

    override fun handlePress(controller: ControllerEntity) {
        when (panelFocus) {
            LEFT_PANEL -> {
                when (leftPanelIndex) {
                    LP_NAME -> {
                        screen.nicknameEntryWidget.isFocused = true
                        tryOpenKeyboard(controller, screen.nicknameEntryWidget)
                    }
                    LP_HELD_ITEM -> {
                        screen.heldItemVisibilityButton.let { it.clickAction.onPress(it) }
                    }
                    LP_EVOLVE -> {
                        if (evoAvaliable()) {
                            screen.displaySideScreen(Summary.EVOLVE)
                            focusPanel(RIGHT_PANEL)
                            selectedEvolutionIndex = 0
                        }
                    }
                }
            }
            RIGHT_PANEL -> {
                when (screen.sideScreenIndex) {
                    Summary.PARTY -> {
                        screen.switchSelection(selectedPartyIndex)
                        screen.playSound(CobblemonSounds.GUI_CLICK)
                    }
                    Summary.MOVE_SWAP -> {
                        val moveSwapScreen = screen.sideScreen as? MoveSwapScreen ?: return
                        val moves = moveSwapScreen.children().toMutableList()
                        moves[selectedMoveSwitchIndex].switchMove()
                        screen.playSound(CobblemonSounds.GUI_CLICK)
                        screen.displaySideScreen(Summary.PARTY)
                        focusPanel(CENTER_PANEL)
                    }
                    Summary.EVOLVE -> {
                        val evolutionSelectWidget = screen.sideScreen as? EvolutionSelectScreen ?: return
                        val evolutions = evolutionSelectWidget.children()
                        evolutions[selectedEvolutionIndex].selectButton.let { it.clickAction.onPress(it) }
                    }
                }
            }
        }
    }

    override fun handleSpecial1(controller: ControllerEntity) {
        when (panelFocus) {
            LEFT_PANEL -> {
                when (leftPanelIndex) {
                    LP_HELD_ITEM -> screen.heldItemSwitchTypeButton.let { it.clickAction.onPress(it) }
                }
            }
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> {
                        changeMoveOrder = false
                        val movesWidget = screen.mainScreen as? MovesWidget ?: return
                        if (selectedMoveIndex > movesWidget.moves.size - 1) return
                        screen.displaySideScreen(Summary.MOVE_SWAP, movesWidget.moves[selectedMoveIndex].move)
                        selectedMoveSwitchIndex = 0
                        focusPanel(RIGHT_PANEL)
                        screen.playSound(CobblemonSounds.GUI_CLICK)
                    }
                }
            }
            RIGHT_PANEL -> {
                when (screen.sideScreenIndex) {
                    Summary.MOVE_SWAP -> {
                        screen.displaySideScreen(Summary.PARTY)
                        focusPanel(CENTER_PANEL)
                        screen.playSound(CobblemonSounds.GUI_CLICK)
                    }
                }
            }
        }
    }

    override fun handleSpecial2(controller: ControllerEntity) {
        when (panelFocus) {
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> changeMoveOrder = !changeMoveOrder
                }
            }
        }
    }

    override fun handleGUINav(controller: ControllerEntity, direction: Direction) {
        when (panelFocus) {
            LEFT_PANEL -> handleLeftPanelNav(direction)
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> handleMovesNav(direction)
                    else -> {
                        if (direction == Direction.WEST) {
                            focusPanel(LEFT_PANEL)
                        } else if (direction == Direction.EAST) {
                            focusPanel(RIGHT_PANEL)
                        }
                    }
                }
            }
            RIGHT_PANEL -> {
                when (screen.sideScreenIndex) {
                    Summary.PARTY -> handlePartyNavigation(direction)
                    Summary.MOVE_SWAP -> handleMoveSwapNavigation(direction)
                    Summary.EVOLVE -> handleEvolutionSelectNav(direction)
                }
            }
        }
    }

    private fun handleLeftPanelNav(direction: Direction) {
        if (direction == Direction.EAST) {
            focusPanel(CENTER_PANEL)
            return
        }

        var offsetIndex: Int? = null
        if (direction == Direction.NORTH) {
            offsetIndex = -1
        } else if (direction == Direction.SOUTH) {
            offsetIndex = 1
        }

        if (offsetIndex != null) {
            val size = if (evoAvaliable()) LP_LIST_SIZE else LP_LIST_SIZE - 1
            leftPanelIndex += offsetIndex
            if (leftPanelIndex < 0) {
                leftPanelIndex = size - 1
            } else if (leftPanelIndex > size - 1) {
                leftPanelIndex = 0
            }
        }
    }

    private fun handleMovesNav(direction: Direction) {
        if (!changeMoveOrder) {
            if (direction == Direction.WEST) {
                focusPanel(LEFT_PANEL)
                return
            }
            if (direction == Direction.EAST) {
                focusPanel(RIGHT_PANEL)
                return
            }
        }

        val movesWidget = screen.mainScreen as? MovesWidget ?: return

        var moveIndexOffset: Int? = null
        if (direction == Direction.NORTH) {
            moveIndexOffset = -1
        } else if (direction == Direction.SOUTH) {
            moveIndexOffset = 1
        }

        if (moveIndexOffset != null) {
            if (selectedMoveIndex > movesWidget.moves.size - 1) {
                selectedMoveIndex = movesWidget.moves.size - 1
                movesWidget.selectMove(movesWidget.moves[selectedMoveIndex].move)
            } else {
                if (changeMoveOrder) {
                    movesWidget.reorderMove(movesWidget.moves[selectedMoveIndex], moveIndexOffset == -1)
                    screen.playSound(CobblemonSounds.GUI_CLICK)
                }
                selectedMoveIndex += moveIndexOffset
                if (selectedMoveIndex < 0) {
                    selectedMoveIndex = movesWidget.moves.size - 1
                } else if (selectedMoveIndex > movesWidget.moves.size - 1) {
                    selectedMoveIndex = 0
                }

                if (!changeMoveOrder) {
                    movesWidget.selectMove(movesWidget.moves[selectedMoveIndex].move)
                }
            }
        }
    }

    private fun handlePartyNavigation(direction: Direction) {
        if (direction == Direction.WEST) {
            if (selectedPartyIndex.mod(2) == 0) {
                focusPanel(CENTER_PANEL)
            } else {
                selectedPartyIndex -= 1
            }
            return
        }
        if (direction == Direction.EAST) {
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

    private fun handleMoveSwapNavigation(direction: Direction) {
        var indexOffset: Int? = null
        if (direction == Direction.NORTH) {
            indexOffset = -1
        } else if (direction == Direction.SOUTH) {
            indexOffset = 1
        } else if (direction == Direction.EAST) {
            indexOffset = 5
        } else if (direction == Direction.WEST) {
            indexOffset = -5
        }

        if (indexOffset == null) return

        val moveSwapScreen = screen.sideScreen as? MoveSwapScreen ?: return
        val moves = moveSwapScreen.children().toMutableList()

        selectedMoveSwitchIndex += indexOffset
        if (selectedMoveSwitchIndex < 0) {
            selectedMoveSwitchIndex = moves.size - 1
        } else if (selectedMoveSwitchIndex > moves.size - 1) {
            selectedMoveSwitchIndex = 0
        }
        moveSwapScreen.ensureVisible(moves[selectedMoveSwitchIndex])
    }

    private fun handleEvolutionSelectNav(direction: Direction) {
        var indexOffset: Int? = null
        if (direction == Direction.NORTH) {
            indexOffset = -1
        } else if (direction == Direction.SOUTH) {
            indexOffset = 1
        }

        if (indexOffset == null) return

        val evolutionSelectWidget = screen.sideScreen as? EvolutionSelectScreen ?: return
        val evolutions = evolutionSelectWidget.children()

        selectedEvolutionIndex += indexOffset
        if (selectedEvolutionIndex < 0) {
            selectedEvolutionIndex = evolutions.size - 1
        } else if (selectedEvolutionIndex > evolutions.size - 1) {
            selectedEvolutionIndex = 0
        }
        evolutionSelectWidget.ensureVisible(evolutions[selectedEvolutionIndex])
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

        changeMoveOrder = false
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

        changeMoveOrder = false
    }

    private fun ensureMoveSelected() {
        val movesWidget = screen.mainScreen as? MovesWidget ?: return
        movesWidget.selectMove(movesWidget.moves.getOrNull(selectedMoveIndex)?.move)
    }

    private fun focusPanel(focusedPanel: Int) {
        panelFocus = focusedPanel
        when (panelFocus) {
            CENTER_PANEL -> {
                when (screen.mainScreenIndex) {
                    Summary.MOVES -> {
                        val movesWidget = screen.mainScreen as? MovesWidget ?: return
                        if (selectedMoveIndex > movesWidget.moves.size - 1) selectedMoveIndex = movesWidget.moves.size - 1
                        ensureMoveSelected()
                    }
                }
            }
        }
    }

    private fun evoAvaliable(): Boolean {
        return screen.selectedPokemon.evolutionProxy.client().isNotEmpty()
                && CobblemonClient.battle == null
                && screen.selectedPokemon.heldItem.item != CobblemonItems.EVERSTONE
    }
}