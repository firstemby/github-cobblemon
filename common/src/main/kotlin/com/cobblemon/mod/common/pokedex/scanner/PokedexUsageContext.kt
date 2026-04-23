/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.scanner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.PokedexLearnedInformation
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.pokedex.PokedexScannerRenderer
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import com.mojang.blaze3d.pipeline.MainTarget
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL40
import kotlin.math.exp
import kotlin.math.ln

class PokedexUsageContext {
    companion object {
        const val ZOOM_MAX_STEP = 9F
        const val ZOOM_TARGET_FOV = 10F // the FOV we get at max zoom (probably somewhat inaccurate, but irrelevant)
        const val ZOOM_BASE_FOV = 80F // getting FOV from MC is easy, but pin it for consistent step count/zoom dist
        const val BLOCK_LENGTH_PER_ZOOM_STAGE = 2
        const val OPEN_SCANNER_BUFFER_TICKS = 5 // Open scanner interface if usage ticks are above this threshold
        const val VIEW_INFO_BUFFER_TICKS = 10
        const val SUCCESS_SCAN_SERVER_TICKS = 15 // Used by FinishScanningHandler to determine if user has scanned for long enough to register
        const val MAX_SCAN_PROGRESS = 100
        const val TRANSITION_INTERVALS = 12F
        const val FOCUS_INTERVALS = 9F
        const val CENTER_INFO_DISPLAY_INTERVALS = 5F
        const val CENTER_INFO_LINGER_INTERVALS = 35F
        const val RENDER_UPDATES_PER_SECOND = (1/0.0175).toFloat() // How many times the render should update in a second
    }

    enum class ScannerMode {
        SCANNING,
        CAMERA
    }

    var infoGuiOpen: Boolean = false
    var scanningGuiOpen: Boolean = false
    var scannerMode: ScannerMode = ScannerMode.SCANNING
    var isPokemonInFocusOwned: Boolean = false
    var registerCompleted: Boolean = false
    var scannedSpecies: ResourceLocation? = null
    var scannableEntityInFocus: ScannableEntity? = null
    var viewInfoTicks: Int = 0
    var scanningProgress: Float = 0F
    var displayRegisterInfoIntervals: Float = 0F
    var transitionIntervals: Float = 0F
    var usageIntervals: Float = 0F
    var focusIntervals: Float = 0F
    var innerRingRotation: Float = 0F
    var zoomLevel: Float = 0F
    var zoomModifier: Float = 1F
    var newPokemonInfo: PokedexLearnedInformation = PokedexLearnedInformation.NONE
    var type: PokedexType = PokedexType.RED
    var availableInfoFrames: MutableList<Boolean?> = mutableListOf(null, null, null, null)
    val renderer: PokedexScannerRenderer = PokedexScannerRenderer()
    private var previousAttackInput = false
    private var previousModeToggleInput = false
    private var pictureBuffer: RenderTarget? = null
    private var shutterForceDrawNextFrame = false
    private var shutterForceDrawUntil = 0L

    fun stopUsing(ticksInUse: Int, speciesId: ResourceLocation? = null) {
        if (ticksInUse < OPEN_SCANNER_BUFFER_TICKS) {
            openPokedexGUI(type, speciesId)
            infoGuiOpen = true
        }
        resetState(false)
    }

    fun renderUpdate(graphics: GuiGraphics, tickCounter: DeltaTracker) {
        val tickDelta = tickCounter.realtimeDeltaTicks.takeIf { !Minecraft.getInstance().isPaused } ?: 0F
        val updateInterval = (tickDelta / 20) * RENDER_UPDATES_PER_SECOND

        if (scanningGuiOpen && viewInfoTicks < VIEW_INFO_BUFFER_TICKS) {
            if (transitionIntervals < TRANSITION_INTERVALS) transitionIntervals = min(transitionIntervals + updateInterval, TRANSITION_INTERVALS)
            innerRingRotation = (if (scannableEntityInFocus != null) (innerRingRotation + (updateInterval * 10F)) else (innerRingRotation + updateInterval)) % 360
            usageIntervals += updateInterval
        } else {
            if (transitionIntervals > 0) {
                if (transitionIntervals == TRANSITION_INTERVALS) playSound(CobblemonSounds.POKEDEX_SCAN_CLOSE)
                transitionIntervals = max(transitionIntervals - updateInterval, 0F)
                if (transitionIntervals <= 0) {
                    if (viewInfoTicks >= VIEW_INFO_BUFFER_TICKS) openPokedexGUI(type, scannableEntityInFocus!!.resolvePokemonScan()?.getApparentSpecies()?.resourceIdentifier)
                    resetState()
                }
            }
        }

        if (scannedSpecies !== null && scannableEntityInFocus != null) {
            val targetId = scannableEntityInFocus?.resolveEntityScan()?.id
            if (scanningProgress == 0F) targetId?.let { StartScanningPacket(it, zoomLevel.toInt()).sendToServer() }
            if (scanningProgress < (MAX_SCAN_PROGRESS + CENTER_INFO_DISPLAY_INTERVALS)) scanningProgress += updateInterval
            if (scanningProgress >= MAX_SCAN_PROGRESS) targetId?.let { FinishScanningPacket(it, zoomLevel.toInt()).sendToServer() }
            if (focusIntervals > 0) focusIntervals = max(0F, focusIntervals - updateInterval)
        } else {
            if (scannableEntityInFocus != null) {
                if (focusIntervals < FOCUS_INTERVALS) focusIntervals = min(focusIntervals + updateInterval, FOCUS_INTERVALS)
            } else {
                if (focusIntervals > 0) focusIntervals = max(0F, focusIntervals - updateInterval)
            }
        }

        if (registerCompleted) {
            if (displayRegisterInfoIntervals < (CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS)) displayRegisterInfoIntervals = min((CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS),displayRegisterInfoIntervals + updateInterval)
            if (displayRegisterInfoIntervals >= (CENTER_INFO_DISPLAY_INTERVALS + CENTER_INFO_LINGER_INTERVALS)) registerCompleted = false
        } else {
            if (displayRegisterInfoIntervals > 0) displayRegisterInfoIntervals = max(0F, displayRegisterInfoIntervals - updateInterval)
        }

        renderer.onRenderOverlay(graphics, tickCounter)
    }

    fun useTick(user: LocalPlayer, ticksInUse: Int, inUse: Boolean) {
        tryOpenScanGui(ticksInUse, inUse)
        if (scanningGuiOpen && scannerMode == ScannerMode.SCANNING) tryScanPokemon(user)
        if (scannerMode == ScannerMode.SCANNING && scannedSpecies !== null && scannableEntityInFocus !== null) {
            playSound(CobblemonSounds.POKEDEX_SCAN_LOOP)
        }
    }

    fun tryOpenScanGui(ticksInUse: Int, inUse: Boolean) {
        if (inUse && ticksInUse == OPEN_SCANNER_BUFFER_TICKS) {
            scanningGuiOpen = true
            playSound(CobblemonSounds.POKEDEX_SCAN_OPEN)
        }
    }

    fun openPokedexGUI(types: PokedexType = PokedexType.RED, speciesId: ResourceLocation? = null) {
        PokedexGUI.open(CobblemonClient.clientPokedexData, types, speciesId)
        playSound(CobblemonSounds.POKEDEX_OPEN)
    }

    fun attackKeyHeld(isHeld: Boolean) {
        if (isHeld && scannableEntityInFocus !== null && viewInfoTicks < VIEW_INFO_BUFFER_TICKS && scanningProgress == 0F) {
            viewInfoTicks++
            if (viewInfoTicks % 2 == 0) playSound(CobblemonSounds.POKEDEX_SCAN_LOOP)
        } else if (viewInfoTicks > 0 && viewInfoTicks < VIEW_INFO_BUFFER_TICKS) {
            viewInfoTicks--
        }
    }

    fun handleAttackInput(isHeld: Boolean) {
        if (scannerMode == ScannerMode.CAMERA) {
            if (isHeld && !previousAttackInput && scanningGuiOpen) {
                takeScanPicture()
            }
        } else {
            attackKeyHeld(isHeld)
        }
        previousAttackInput = isHeld
    }

    fun handleModeToggleInput(isHeld: Boolean) {
        if (isHeld && !previousModeToggleInput && scanningGuiOpen) {
            toggleScannerMode()
        }
        previousModeToggleInput = isHeld
    }

    fun handleScanInput(attackHeld: Boolean, modeToggleHeld: Boolean) {
        handleModeToggleInput(modeToggleHeld)
        handleAttackInput(attackHeld)
    }

    fun toggleScannerMode() {
        if (!scanningGuiOpen) return

        scannerMode = if (scannerMode == ScannerMode.SCANNING) {
            resetFocusedPokemonState()
            ScannerMode.CAMERA
        } else {
            ScannerMode.SCANNING
        }

        playSound(CobblemonSounds.POKEDEX_SCAN_MODE_SWAP)
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable(
                if (scannerMode == ScannerMode.CAMERA) {
                    "cobblemon.ui.pokedex.scan.mode.camera_enabled"
                } else {
                    "cobblemon.ui.pokedex.scan.mode.scanning_enabled"
                }
            ),
            true
        )
    }

    fun cacheScannerFrame() {
        if (!scanningGuiOpen) return

        val minecraft = Minecraft.getInstance()
        val mainTarget = minecraft.mainRenderTarget
        val buffer = ensurePictureBuffer(mainTarget.width, mainTarget.height)

        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, mainTarget.frameBufferId)
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, buffer.frameBufferId)
        GL40.glBlitFramebuffer(
            0,
            0,
            mainTarget.width,
            mainTarget.height,
            0,
            0,
            buffer.width,
            buffer.height,
            GL30C.GL_COLOR_BUFFER_BIT or GL30C.GL_DEPTH_BUFFER_BIT or GL30C.GL_STENCIL_BUFFER_BIT,
            GL30C.GL_NEAREST
        )
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, mainTarget.frameBufferId)
    }

    fun tryScanPokemon(user: LocalPlayer) {
        val targetScannableEntity = PokemonScanner.findScannableEntity(user, zoomLevel.toInt())
        if (targetScannableEntity != null) {
            if (targetScannableEntity != scannableEntityInFocus) {
                resetFocusedPokemonState()
                scannableEntityInFocus = targetScannableEntity
                val resolvedPokemon = scannableEntityInFocus?.resolvePokemonScan()
                if(resolvedPokemon == null) {
                    resetFocusedPokemonState()
                    return
                }

                // Check if Pokémon in focus is owned
                isPokemonInFocusOwned = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(resolvedPokemon.getApparentSpecies().resourceIdentifier) == PokedexEntryProgress.CAUGHT

                // Randomize info frames for render
                if (focusIntervals == 0F) {
                    availableInfoFrames = mutableListOf(null, null, null, null)
                    for (i in 0..2) {
                        var randomIndex = Random.nextInt(availableInfoFrames.size)
                        if (availableInfoFrames[randomIndex] !== null) randomIndex = availableInfoFrames.indexOfFirst { it == null }
                        availableInfoFrames.set(randomIndex, Random.nextBoolean())
                    }
                }

                // Check if Pokémon in focus is new or has new data
                newPokemonInfo = CobblemonClient.clientPokedexData.getNewInformation(resolvedPokemon)
                if (newPokemonInfo == PokedexLearnedInformation.NONE) playSound(CobblemonSounds.POKEDEX_SCAN_DETAIL)
                else scannedSpecies = resolvedPokemon.getApparentSpecies().resourceIdentifier
            }
        } else {
            resetFocusedPokemonState()
        }
    }

    fun onServerConfirmedRegister(packet: ServerConfirmedRegisterPacket) {
        if (scannedSpecies?.equals(packet.species) == true) {
            newPokemonInfo = packet.newInformation
            registerCompleted = true
            scannedSpecies = null
            scanningProgress = 0F
            playSound(
                if (newPokemonInfo == PokedexLearnedInformation.SPECIES) CobblemonSounds.POKEDEX_SCAN_REGISTER_POKEMON
                else CobblemonSounds.POKEDEX_SCAN_REGISTER_ASPECT
            )
        }
    }

    fun resetFocusedPokemonState() {
        scannableEntityInFocus = null
        scannedSpecies = null
        viewInfoTicks = 0
        scanningProgress = 0F
        displayRegisterInfoIntervals = 0F
        registerCompleted = false
        isPokemonInFocusOwned = false
        newPokemonInfo = PokedexLearnedInformation.NONE
    }

    fun resetState(resetAnimationStates: Boolean = true) {
        scanningGuiOpen = false
        scannerMode = ScannerMode.SCANNING
        zoomLevel = 0F
        zoomModifier = 1F
        focusIntervals = 0F
        previousAttackInput = false
        previousModeToggleInput = false
        shutterForceDrawNextFrame = false
        shutterForceDrawUntil = 0L
        resetFocusedPokemonState()

        if (resetAnimationStates) {
            innerRingRotation = 0F
            transitionIntervals = 0F
            usageIntervals = 0F
        }
    }

    fun adjustZoom(verticalScrollAmount: Double) {
        if ((zoomLevel <= 0F && verticalScrollAmount < 0) || (zoomLevel >= ZOOM_MAX_STEP && verticalScrollAmount > 0))
            return

        playSound(CobblemonSounds.POKEDEX_SCAN_ZOOM_INCREMENT)
        zoomLevel = (zoomLevel + verticalScrollAmount.toFloat()).coerceIn(0F, ZOOM_MAX_STEP)
        innerRingRotation = (innerRingRotation + 15) % 360
        val startLog = ln(ZOOM_BASE_FOV)
        val targetLog = ln(ZOOM_TARGET_FOV)
        val stepSize = (startLog - targetLog) / ZOOM_MAX_STEP
        val currentZoomLog = startLog - (zoomLevel * stepSize)
        val currentFov = exp(currentZoomLog)
        zoomModifier = currentFov / ZOOM_BASE_FOV
    }

    // 1 = normal FOV; 0 = max zoom; this runs on render update, best to keep logic out of here
    fun getFovMultiplier() = zoomModifier

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }

    fun startShutterPulse(durationMs: Long = 100L) {
        shutterForceDrawNextFrame = true
        shutterForceDrawUntil = System.currentTimeMillis() + durationMs
    }

    fun getShutterOverlayOpacity(): Float {
        if (shutterForceDrawNextFrame) {
            return 0.98F
        }

        val remaining = shutterForceDrawUntil - System.currentTimeMillis()
        if (remaining <= 0L) {
            return 0F
        }

        return 0.98F
    }

    fun onShutterRendered() {
        shutterForceDrawNextFrame = false
    }

    private fun ensurePictureBuffer(width: Int, height: Int): RenderTarget {
        val existing = pictureBuffer
        if (existing == null) {
            val created = MainTarget(width, height)
            pictureBuffer = created
            return created
        }

        if (existing.width != width || existing.height != height) {
            existing.resize(width, height, Minecraft.ON_OSX)
        }

        return existing
    }

    private fun takeScanPicture() {
        val buffer = pictureBuffer ?: return
        startShutterPulse()
        playSound(CobblemonSounds.POKEDEX_CAMERA_SNAP)
        val image = Screenshot.takeScreenshot(buffer)
        var resized: NativeImage? = null

        try {
            val targetSize = max(64, Cobblemon.config.pokedexScannerPhotoResolution)
            resized = resizeToSquare(image, targetSize)
            PokedexPhotoUploadPacket(targetSize, targetSize, resized.asByteArray()).sendToServer()
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to save Pokedex scan photo", e)
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("Failed to save Pokedex scan photo"),
                true
            )
        } finally {
            resized?.close()
            image.close()
        }
    }

    private fun resizeToSquare(image: NativeImage, size: Int): NativeImage {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val side = min(sourceWidth, sourceHeight)
        val startX = (sourceWidth - side) / 2
        val startY = (sourceHeight - side) / 2

        val resized = NativeImage(size, size, false)
        for (y in 0 until size) {
            val sourceY = startY + ((y * side) / size)
            for (x in 0 until size) {
                val sourceX = startX + ((x * side) / size)
                resized.setPixelRGBA(x, y, image.getPixelRGBA(sourceX, sourceY))
            }
        }
        return resized
    }
}
