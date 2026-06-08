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
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.pokedex.photo.PokedexPhotoAnimationTypes
import com.cobblemon.mod.common.client.pokedex.PokedexScannerRenderer
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.client.render.DeferredRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockPoseAnimation
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.api.pokedex.scan.BlockScanProviderRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoQuality
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
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
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL40
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.exp
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

class PokedexUsageContext {
    data class PhotoCaptureBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    ) {
        val right: Int = left + width
        val bottom: Int = top + height
        val centerX: Int = left + (width / 2)
        val centerY: Int = top + (height / 2)
    }

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
        private const val BASE_PHOTO_RAYCAST_COUNT = 6
        private const val PHOTO_DEBUG_RAY_DURATION_MS = 10_000L
        private const val PHOTO_SCORE_MAX_SUBJECTS = 5
        private const val PHOTO_PERIMETER_OFFSET = 1.01
        const val PHOTO_CAPTURE_FRAME_WIDTH = 158
        const val PHOTO_CAPTURE_FRAME_HEIGHT = 158
        const val PHOTO_CAPTURE_FRAME_OFFSET_X = 0
        const val PHOTO_CAPTURE_FRAME_OFFSET_Y = 0

        fun photoCaptureBoundsForGui(screenWidth: Int, screenHeight: Int): PhotoCaptureBounds {
            val width = PHOTO_CAPTURE_FRAME_WIDTH.coerceIn(2, screenWidth.coerceAtLeast(2))
            val height = PHOTO_CAPTURE_FRAME_HEIGHT.coerceIn(2, screenHeight.coerceAtLeast(2))
            val centeredLeft = (screenWidth / 2) + PHOTO_CAPTURE_FRAME_OFFSET_X - (width / 2)
            val centeredTop = (screenHeight / 2) + PHOTO_CAPTURE_FRAME_OFFSET_Y - (height / 2)
            val left = centeredLeft.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            val top = centeredTop.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
            return PhotoCaptureBounds(left, top, width, height)
        }
    }

    enum class ScannerMode {
        SCANNING,
        CAMERA
    }

    var infoGuiOpen: Boolean = false
    var scanningGuiOpen: Boolean = false
    var previousScanningMode: ScannerMode = ScannerMode.SCANNING
    var scannerMode: ScannerMode = previousScanningMode
    var isPokemonInFocusOwned: Boolean = false
    var registerCompleted: Boolean = false
    var scannedSpecies: ResourceLocation? = null
    var scannableEntityInFocus: ScannableEntity? = null
    var blockEntityInFocus: BlockEntity? = null
    var blockPosInFocus: BlockPos? = null
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
    private var blockEntityScanCompleted = false
    private var previousAttackInput = false
    private var previousModeToggleInput = false
    private var pictureBuffer: RenderTarget? = null
    private var shutterForceDrawNextFrame = false
    private var shutterAnimationStartedAt = 0L
    private var shutterAnimationDurationMs = 0L
    private var capturedDebugRays: List<CapturedDebugRay> = emptyList()
    private var capturedDebugRaysExpireAt: Long = 0L
    var showDebugPhotoScore = false
    var showDebugPhotoRaycast = false

    private fun hasAnyFocus(): Boolean = scannableEntityInFocus != null || blockEntityInFocus != null

    private data class PhotoCaptureMetadata(
        val detectedSpecies: List<String>,
        val biome: String,
        val dimension: String,
        val timeOfDayTicks: Long,
        val raining: Boolean,
        val thundering: Boolean,
        val qualityScore: Int,
        val qualitySubjects: List<PokedexPhotoSubjectQuality>
    ) {
        companion object {
            val EMPTY = PhotoCaptureMetadata(
                detectedSpecies = emptyList(),
                biome = "minecraft:unknown",
                dimension = "minecraft:overworld",
                timeOfDayTicks = 0L,
                raining = false,
                thundering = false,
                qualityScore = 0,
                qualitySubjects = emptyList()
            )
        }
    }

    private data class RaycastCandidate(
        val entity: Entity,
        val scannable: ScannableEntity,
        val entityBox: AABB
    )

    private data class PhotoRaySample(
        val screenX: Double,
        val screenY: Double,
        val direction: Vec3,
        val perimeter: Boolean = false
    )

    private data class CapturedDebugRay(
        val start: Vec3,
        val hit: Vec3?,
        val end: Vec3,
        val perimeter: Boolean
    )

    private data class PhotoProjection(
        val eyePos: Vec3,
        val forward: Vec3,
        val right: Vec3,
        val up: Vec3,
        val tanHalfHorizontal: Double,
        val tanHalfVertical: Double,
        val captureCenterX: Double,
        val captureCenterY: Double,
        val captureScaleX: Double,
        val captureScaleY: Double
    )

    private data class PhotoRayHit(
        val entity: Entity,
        val scannable: ScannableEntity,
        val point: Vec3,
        val distance: Double
    )

    private data class PhotoDetectedHit(
        val entity: Entity,
        val species: ResourceLocation,
        val point: Vec3,
        val distance: Double
    )

    private class PhotoSubjectAccumulator(
        val entity: Entity,
        val species: ResourceLocation
    ) {
        var rayHits = 0
        var perimeterHits = 0
        var projectedInsidePhotoBounds = true
        var sampleXSum = 0.0
        var sampleYSum = 0.0
        var distanceValueSum = 0.0

        fun addInFrameHit(sample: PhotoRaySample, distanceValue: Double) {
            rayHits += 1
            sampleXSum += sample.screenX
            sampleYSum += sample.screenY
            distanceValueSum += distanceValue
        }
    }

    private data class PhotoSubjectStats(
        val accumulator: PhotoSubjectAccumulator,
        val rayCoverage: Double,
        val centerDistance: Double,
        val distanceScore: Int
    )

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
            innerRingRotation = (if (hasAnyFocus()) (innerRingRotation + (updateInterval * 10F)) else (innerRingRotation + updateInterval)) % 360
            usageIntervals += updateInterval
        } else {
            if (transitionIntervals > 0) {
                if (transitionIntervals == TRANSITION_INTERVALS) playSound(CobblemonSounds.POKEDEX_SCAN_CLOSE)
                transitionIntervals = max(transitionIntervals - updateInterval, 0F)
                if (transitionIntervals <= 0) {
                    if (viewInfoTicks >= VIEW_INFO_BUFFER_TICKS) {
                        scannableEntityInFocus?.resolvePokemonScan()?.getApparentSpecies()?.resourceIdentifier?.let { species ->
                            openPokedexGUI(type, species)
                        }
                    }
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
        } else if (blockEntityInFocus != null) {
            if (scanningProgress < (MAX_SCAN_PROGRESS + CENTER_INFO_DISPLAY_INTERVALS)) {
                scanningProgress += updateInterval
            }
            if (!blockEntityScanCompleted && scanningProgress >= MAX_SCAN_PROGRESS) {
                blockEntityScanCompleted = true
                val be = blockEntityInFocus
                val pos = blockPosInFocus
                if (be != null && pos != null) {
                    val typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.type)
                    if (typeId != null) BlockScanProviderRegistry.get(typeId)?.onScanComplete(pos, zoomLevel.toInt())
                }
            }
            if (focusIntervals < FOCUS_INTERVALS) focusIntervals = min(focusIntervals + updateInterval, FOCUS_INTERVALS)
        } else {
            if (hasAnyFocus()) {
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
        if (scannerMode == ScannerMode.SCANNING && ((scannedSpecies !== null && scannableEntityInFocus !== null) || blockEntityInFocus != null)) {
            playSound(CobblemonSounds.POKEDEX_SCAN_LOOP)
        }
    }

    fun tryOpenScanGui(ticksInUse: Int, inUse: Boolean) {
        if (inUse && ticksInUse == OPEN_SCANNER_BUFFER_TICKS) {
            scannerMode = previousScanningMode
            scanningGuiOpen = true
            playSound(CobblemonSounds.POKEDEX_SCAN_OPEN)
        }
    }

    fun openPokedexGUI(types: PokedexType = PokedexType.RED, speciesId: ResourceLocation? = null) {
        PokedexGUI.open(CobblemonClient.clientPokedexData, types, speciesId)
        playSound(CobblemonSounds.POKEDEX_OPEN)
    }

    fun attackKeyHeld(isHeld: Boolean) {
        if (isHeld && hasAnyFocus() && viewInfoTicks < VIEW_INFO_BUFFER_TICKS && scanningProgress == 0F) {
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
        previousScanningMode = scannerMode

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
        // Pokémon/entity scan
        val targetScannableEntity = PokemonScanner.findScannableEntity(user, zoomLevel.toInt())
        if (targetScannableEntity != null) {
            blockEntityInFocus = null
            blockPosInFocus = null
            blockEntityScanCompleted = false
            if (targetScannableEntity != scannableEntityInFocus) {
                resetFocusedPokemonState()
                scannableEntityInFocus = targetScannableEntity
                val resolvedPokemon = scannableEntityInFocus?.resolvePokemonScan()
                if (resolvedPokemon == null) {
                    resetFocusedPokemonState()
                    return
                }

                // Check if Pokémon in focus is owned
                isPokemonInFocusOwned = CobblemonClient.clientPokedexData.getHighestKnowledgeForSpecies(resolvedPokemon.getApparentSpecies().resourceIdentifier) == PokedexEntryProgress.CAUGHT

                // Randomize info frames for render
                if (focusIntervals == 0F) {
                    availableInfoFrames = mutableListOf(null, null, null, null)
                    for (i in 0..3) {
                        var randomIndex = Random.nextInt(availableInfoFrames.size)
                        if (availableInfoFrames[randomIndex] != null) randomIndex = availableInfoFrames.indexOfFirst { it == null }
                        availableInfoFrames.set(randomIndex, Random.nextBoolean())
                    }
                }

                // Check if Pokémon in focus is new or has new data
                newPokemonInfo = CobblemonClient.clientPokedexData.getNewInformation(resolvedPokemon)
                if (newPokemonInfo == PokedexLearnedInformation.NONE) playSound(CobblemonSounds.POKEDEX_SCAN_DETAIL)
                else scannedSpecies = resolvedPokemon.getApparentSpecies().resourceIdentifier
            }
            return
        }

        // BlockEntity scan
        val blockHit = PokemonScanner.findScannableBlockEntity(user, zoomLevel.toInt())
        if (blockHit != null) {
            val (pos, be) = blockHit
            if (be != blockEntityInFocus || pos != blockPosInFocus) {
                resetFocusedPokemonState()
                blockEntityInFocus = be
                blockPosInFocus = pos
                blockEntityScanCompleted = false
                if (focusIntervals == 0F) {
                    availableInfoFrames = mutableListOf(null, null, null, null)
                    for (i in 0..3) {
                        var randomIndex = Random.nextInt(availableInfoFrames.size)
                        if (availableInfoFrames[randomIndex] != null) randomIndex = availableInfoFrames.indexOfFirst { it == null }
                        availableInfoFrames[randomIndex] = Random.nextBoolean()
                    }
                }
            }
            return
        }

        resetFocusedPokemonState()
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
        blockEntityInFocus = null
        blockPosInFocus = null
        blockEntityScanCompleted = false
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
        scannerMode = previousScanningMode
        zoomLevel = 0F
        zoomModifier = 1F
        focusIntervals = 0F
        previousAttackInput = false
        previousModeToggleInput = false
        shutterForceDrawNextFrame = false
        shutterAnimationStartedAt = 0L
        shutterAnimationDurationMs = 0L
        capturedDebugRays = emptyList()
        capturedDebugRaysExpireAt = 0L
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

    fun startShutterPulse(durationMs: Long = 180L) {
        shutterForceDrawNextFrame = true
        shutterAnimationStartedAt = System.currentTimeMillis()
        shutterAnimationDurationMs = durationMs.coerceAtLeast(1L)
    }

    fun getShutterBlinkClosure(): Float {
        if (shutterForceDrawNextFrame) {
            return 0.35F
        }

        if (shutterAnimationStartedAt == 0L || shutterAnimationDurationMs == 0L) {
            return 0F
        }

        val progress = ((System.currentTimeMillis() - shutterAnimationStartedAt).toFloat() / shutterAnimationDurationMs.toFloat()).coerceIn(0F, 1F)
        return when {
            progress < 0.35F -> easeOutCubic(progress / 0.35F)
            progress < 0.52F -> 1F
            progress < 1F -> 1F - easeInCubic((progress - 0.52F) / 0.48F)
            else -> 0F
        }
    }

    fun onShutterRendered() {
        shutterForceDrawNextFrame = false
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1F - value.coerceIn(0F, 1F)
        return 1F - (inverse * inverse * inverse)
    }

    private fun easeInCubic(value: Float): Float {
        val clamped = value.coerceIn(0F, 1F)
        return clamped * clamped * clamped
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
            resized = resizeToCaptureArea(image, targetSize)
            val player = Minecraft.getInstance().player
            if (player != null && showDebugPhotoRaycast) {
                captureDebugRays(player)
            }
            val metadata = if (player != null) collectPhotoMetadata(player) else PhotoCaptureMetadata.EMPTY
            showPhotoDetectionMessage(metadata)
            PokedexPhotoUploadPacket(
                width = targetSize,
                height = targetSize,
                detectedSpecies = metadata.detectedSpecies,
                biome = metadata.biome,
                dimension = metadata.dimension,
                timeOfDayTicks = metadata.timeOfDayTicks,
                raining = metadata.raining,
                thundering = metadata.thundering,
                qualityScore = metadata.qualityScore,
                qualitySubjects = metadata.qualitySubjects,
                pngData = resized.asByteArray()
            ).sendToServer()
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

    private fun showPhotoDetectionMessage(metadata: PhotoCaptureMetadata) {
        val player = Minecraft.getInstance().player ?: return

        if (metadata.detectedSpecies.isEmpty()) {
            if (showDebugPhotoScore)
                player.displayClientMessage(Component.literal("Photo scan detected: none (score ${metadata.qualityScore})"), false)

            if (showDebugPhotoScore)
                showPhotoScoringDebug(player, metadata)
            return
        }

        val speciesNames = metadata.detectedSpecies.map { speciesId ->
            val identifier = ResourceLocation.tryParse(speciesId)
            val species = identifier?.let(PokemonSpecies::getByIdentifier)
            species?.translatedName?.string ?: speciesId.substringAfter(':')
        }

        val maxShown = 12
        val shown = speciesNames.take(maxShown)
        val overflow = speciesNames.size - shown.size
        val suffix = if (overflow > 0) ", +$overflow more" else ""
        if (showDebugPhotoScore)
            player.displayClientMessage(
                Component.literal("Photo scan detected (${speciesNames.size}, score ${metadata.qualityScore}): ${shown.joinToString(", ")}$suffix"),
                false
            )
            showPhotoScoringDebug(player, metadata)
    }

    private fun showPhotoScoringDebug(player: LocalPlayer, metadata: PhotoCaptureMetadata) {
        player.displayClientMessage(Component.literal("[Photo Score Debug] client base score: ${metadata.qualityScore}/5000"), false)

        if (metadata.qualitySubjects.isEmpty()) {
            player.displayClientMessage(Component.literal("[Photo Score Debug] no in-frame Pokemon ray hits."), false)
            return
        }

        metadata.qualitySubjects.forEachIndexed { index, subject ->
            val role = if (subject.primarySubject) "PRIMARY" else "other #${index + 1}"
            val rawCategoryScore = subject.sizeScore +
                    subject.placementScore +
                    subject.directionScore +
                    subject.poseScore +
                    subject.otherPokemonScore +
                    subject.herdScore +
                    subject.habitatScore +
                    subject.timeOfDayScore +
                    subject.weatherScore +
                    subject.shinyScore +
                    subject.alphaScore +
                    subject.wildScore +
                    subject.formScore

            val animationText = if (subject.activeAnimations.isEmpty()) {
                "none"
            } else {
                subject.activeAnimations.joinToString(", ")
            }

            val clippingText = if (subject.wholeBodyInFrame) {
                "full body +${subject.fullBodyScore}"
            } else {
                "OUT OF FRAME: x${formatMultiplier(subject.clippingMultiplier)} (${subject.perimeterHits} perimeter hits)"
            }

            val boostText = listOf(
                "size +${subject.sizeScore}",
                "centering +${subject.placementScore}",
                "look-at-camera +${subject.directionScore}",
                "herd +${subject.herdScore}",
                "wild +${subject.wildScore}",
                "form +${subject.formScore}",
                "animation pose +${subject.poseScore}",
                "habitat +${subject.habitatScore}",
                "time of day +${subject.timeOfDayScore}",
                "weather +${subject.weatherScore}",
                clippingText,
                "other Pokemon +${subject.otherPokemonScore}",
                "shiny +${subject.shinyScore}",
                "alpha +${subject.alphaScore}",
                "species rarity x${formatMultiplier(subject.rarityMultiplier)}"
            ).joinToString(", ")

            player.displayClientMessage(
                Component.literal(
                    "[Photo Score Debug] $role ${subject.species}: score=${subject.subjectScore}, raw=$rawCategoryScore, " +
                            "size=${subject.sizeScore}, placement=${subject.placementScore}, look=${subject.directionScore}, " +
                            "pose=${subject.poseScore}, center=${formatPercent(1F - subject.centerDistance)}, distance=${subject.distanceScore}, " +
                            clippingText
                ),
                false
            )

            player.displayClientMessage(
                Component.literal(
                    "[Photo Score Debug]   rays=${subject.rayHits}, coverage=${formatPercent(subject.rayCoverage)}, " +
                            "boosts=[$boostText], animations=[$animationText]"
                ),
                false
            )
        }

        val primary = metadata.qualitySubjects.firstOrNull { it.primarySubject }
        if (primary != null) {
            val finalRawScore = primary.sizeScore +
                    primary.placementScore +
                    primary.directionScore +
                    primary.poseScore +
                    primary.otherPokemonScore +
                    primary.herdScore +
                    primary.wildScore +
                    primary.formScore +
                    primary.habitatScore +
                    primary.timeOfDayScore +
                    primary.weatherScore +
                    primary.shinyScore +
                    primary.alphaScore

            val expectedFinalScore = (finalRawScore *
                    primary.rarityMultiplier *
                    primary.clippingMultiplier
                    ).roundToInt().coerceIn(0, 5000)

            player.displayClientMessage(
                Component.literal(
                    "[Photo Score Debug]   -> FINAL subject=${primary.species}, score=${primary.subjectScore}/5000, " +
                            "formula=(${primary.sizeScore} size + ${primary.placementScore} center + " +
                            "${primary.directionScore} look + ${primary.poseScore} pose + " +
                            "${primary.otherPokemonScore} extra + ${primary.herdScore} herd + " +
                            "${primary.wildScore} wild + ${primary.formScore} form + " +
                            "${primary.habitatScore} habitat + ${primary.timeOfDayScore} time + " +
                            "${primary.weatherScore} weather + ${primary.shinyScore} shiny + " +
                            "${primary.alphaScore} alpha) = $finalRawScore, " +
                            "multipliers=[clip x${formatMultiplier(primary.clippingMultiplier)}, " +
                            "rarity x${formatMultiplier(primary.rarityMultiplier)}], " +
                            "expected=$expectedFinalScore, rays=${primary.rayHits}, coverage=${formatPercent(primary.rayCoverage)}, " +
                            "center=${formatPercent(1F - primary.centerDistance)}"
                ),
                false
            )
        }
    }

    private fun formatMultiplier(value: Float): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

    private fun formatPercent(value: Float): String {
        return String.format(Locale.ROOT, "%.1f%%", value * 100F)
    }

    private fun collectPhotoMetadata(player: LocalPlayer): PhotoCaptureMetadata {
        val level = player.level()
        val eyePos = player.getEyePosition(1.0F)
        val raycastDistance = ServerSettings.pokedexScannerPhotoRaycastDistance.coerceIn(4.0, 512.0)
        val projection = buildPhotoProjection(player)
        val raySamples = computePhotoRaySamples(player, projection)
        val inFrameRayCount = raySamples.count { !it.perimeter }.coerceAtLeast(1)
        val candidates = collectRaycastCandidates(player, raycastDistance)
        val seenSpecies = LinkedHashSet<String>()
        val subjectsByEntity = linkedMapOf<Int, PhotoSubjectAccumulator>()

        raySamples.forEach { sample ->
            val hit = detectPhotoHitAlongRay(player, eyePos, sample, raycastDistance, candidates) ?: return@forEach
            val subject = subjectsByEntity.getOrPut(hit.entity.id) {
                PhotoSubjectAccumulator(
                    entity = hit.entity,
                    species = hit.species
                )
            }.also { subject ->
                subject.projectedInsidePhotoBounds = subject.projectedInsidePhotoBounds && isEntityInsidePhotoBounds(hit.entity, projection)
            }

            if (sample.perimeter) {
                subject.perimeterHits += 1
                return@forEach
            }

            seenSpecies.add(hit.species.toString())
            subject.addInFrameHit(
                sample = sample,
                distanceValue = (1.0 - (hit.distance / raycastDistance).coerceIn(0.0, 1.0))
            )
        }

        val qualitySubjects = buildQualitySubjects(subjectsByEntity.values, inFrameRayCount)

        val biome = level.getBiome(player.blockPosition()).unwrapKey()
            .map { it.location().toString() }
            .orElse("minecraft:unknown")

        return PhotoCaptureMetadata(
            detectedSpecies = seenSpecies.toList(),
            biome = biome,
            dimension = level.dimension().location().toString(),
            timeOfDayTicks = ((level.dayTime % 24000L) + 24000L) % 24000L,
            raining = level.isRaining,
            thundering = level.isThundering,
            qualityScore = calculatePhotoQualityScore(qualitySubjects),
            qualitySubjects = qualitySubjects
        )
    }

    private fun buildPhotoRaySamples(): List<Pair<Double, Double>> {
        val density = ServerSettings.pokedexScannerPhotoRaycastDensity.coerceIn(1, 6)
        val rayCount = BASE_PHOTO_RAYCAST_COUNT * (1 shl (density - 1))
        val samples = ArrayList<Pair<Double, Double>>(rayCount * 2)
        val baseSamples = ArrayList<Pair<Double, Double>>(rayCount)
        val horizontalStep = 2.0 / rayCount.toDouble()

        repeat(rayCount) { index ->
            val horizontal = (((index + 0.5) / rayCount.toDouble()) * 2.0) - 1.0
            val vertical = (radicalInverseBase2(index + 1) * 2.0) - 1.0
            baseSamples += horizontal.coerceIn(-1.0, 1.0) to vertical.coerceIn(-1.0, 1.0)
        }

        samples += baseSamples
        baseSamples.forEachIndexed { index, (sampleX, sampleY) ->
            val nextY = baseSamples[(index + 1) % baseSamples.size].second
            val interstitialX = wrapPhotoSample(sampleX + (horizontalStep * 0.5))
            val interstitialY = ((sampleY + nextY) * 0.5).coerceIn(-1.0, 1.0)
            samples += interstitialX to interstitialY
        }

        if (samples.isNotEmpty()) {
            samples[0] = 0.0 to 0.0
        }
        return samples
    }

    private fun buildPhotoPerimeterRaySamples(): List<Pair<Double, Double>> {
        val density = ServerSettings.pokedexScannerPhotoPerimeterRaycastDensity.coerceIn(1, 6)
        val edgeSamples = (density * 6).coerceAtLeast(8)
        val samples = ArrayList<Pair<Double, Double>>(edgeSamples * 4)

        repeat(edgeSamples) { index ->
            val position = if (edgeSamples == 1) 0.0 else -1.0 + (2.0 * index / (edgeSamples - 1).toDouble())
            samples += position to -PHOTO_PERIMETER_OFFSET
            samples += position to PHOTO_PERIMETER_OFFSET
            samples += -PHOTO_PERIMETER_OFFSET to position
            samples += PHOTO_PERIMETER_OFFSET to position
        }

        return samples.distinct()
    }

    private fun wrapPhotoSample(value: Double): Double {
        return when {
            value > 1.0 -> value - 2.0
            value < -1.0 -> value + 2.0
            else -> value
        }.coerceIn(-1.0, 1.0)
    }

    private fun radicalInverseBase2(value: Int): Double {
        var current = value
        var inversePower = 0.5
        var result = 0.0

        while (current > 0) {
            if ((current and 1) == 1) {
                result += inversePower
            }
            inversePower *= 0.5
            current = current ushr 1
        }

        return result
    }

    private fun computePhotoRayDirections(player: LocalPlayer): List<Vec3> {
        return computePhotoRaySamples(player).map { it.direction }
    }

    private fun computePhotoRaySamples(player: LocalPlayer): List<PhotoRaySample> {
        return computePhotoRaySamples(player, buildPhotoProjection(player))
    }

    private fun computePhotoRaySamples(player: LocalPlayer, projection: PhotoProjection): List<PhotoRaySample> {
        val samples = buildPhotoRaySamples().map { (sampleX, sampleY) -> sampleX to sampleY to false } +
                buildPhotoPerimeterRaySamples().map { (sampleX, sampleY) -> sampleX to sampleY to true }

        return samples.map { sample ->
            val sampleX = sample.first.first
            val sampleY = sample.first.second
            val perimeter = sample.second
            val boundedSampleX = projection.captureCenterX + (sampleX * projection.captureScaleX)
            val boundedSampleY = projection.captureCenterY + (sampleY * projection.captureScaleY)
            val direction = projection.forward
                .add(projection.right.scale(projection.tanHalfHorizontal * boundedSampleX))
                .add(projection.up.scale(projection.tanHalfVertical * boundedSampleY))
                .normalize()
            PhotoRaySample(
                screenX = sampleX,
                screenY = sampleY,
                direction = direction,
                perimeter = perimeter
            )
        }
    }

    private fun buildPhotoProjection(player: LocalPlayer): PhotoProjection {
        val renderTarget = Minecraft.getInstance().mainRenderTarget
        val aspectRatio = (renderTarget.width.toDouble() / max(1, renderTarget.height).toDouble()).coerceIn(0.5, 4.0)
        val captureBounds = photoCaptureBoundsForImage(renderTarget.width, renderTarget.height)
        val captureCenterX = (((captureBounds.left + (captureBounds.width / 2.0)) / renderTarget.width.toDouble()) * 2.0) - 1.0
        val captureCenterY = 1.0 - (((captureBounds.top + (captureBounds.height / 2.0)) / renderTarget.height.toDouble()) * 2.0)
        val captureScaleX = captureBounds.width.toDouble() / renderTarget.width.toDouble()
        val captureScaleY = captureBounds.height.toDouble() / renderTarget.height.toDouble()
        val baseFov = Minecraft.getInstance().options.fov().get().toFloat().coerceIn(30F, 110F)
        val verticalFovRadians = Math.toRadians((baseFov * zoomModifier).coerceIn(10F, 170F).toDouble())
        val halfVerticalFov = verticalFovRadians * 0.5
        val halfHorizontalFov = atan(tan(halfVerticalFov) * aspectRatio)

        val forward = player.getViewVector(1.0F).normalize()
        val worldUp = Vec3(0.0, 1.0, 0.0)
        val fallbackUp = Vec3(0.0, 0.0, 1.0)
        var right = forward.cross(worldUp)
        if (right.lengthSqr() < 1.0E-6) {
            right = forward.cross(fallbackUp)
        }
        right = right.normalize()

        return PhotoProjection(
            eyePos = player.getEyePosition(1.0F),
            forward = forward,
            right = right,
            up = right.cross(forward).normalize(),
            tanHalfHorizontal = tan(halfHorizontalFov),
            tanHalfVertical = tan(halfVerticalFov),
            captureCenterX = captureCenterX,
            captureCenterY = captureCenterY,
            captureScaleX = captureScaleX,
            captureScaleY = captureScaleY
        )
    }

    private fun captureDebugRays(player: LocalPlayer) {
        val eyePos = player.getEyePosition(1.0F)
        val raycastDistance = ServerSettings.pokedexScannerPhotoRaycastDistance.coerceIn(4.0, 512.0)
        val candidates = collectRaycastCandidates(player, raycastDistance)

        capturedDebugRays = computePhotoRaySamples(player).map { sample ->
            val rayEnd = eyePos.add(sample.direction.scale(raycastDistance))
            val hit = detectPhotoHitAlongRay(
                player = player,
                eyePos = eyePos,
                sample = sample,
                maxDistance = raycastDistance,
                candidates = candidates
            )

            CapturedDebugRay(
                start = eyePos,
                hit = hit?.point,
                end = rayEnd,
                perimeter = sample.perimeter
            )
        }

        capturedDebugRaysExpireAt = System.currentTimeMillis() + PHOTO_DEBUG_RAY_DURATION_MS
    }

    fun enqueuePhotoDebugRender() {
        if (capturedDebugRays.isEmpty()) return
        if (System.currentTimeMillis() > capturedDebugRaysExpireAt) {
            capturedDebugRays = emptyList()
            capturedDebugRaysExpireAt = 0L
            return
        }
        val debugRays = capturedDebugRays

        DeferredRenderer.enqueue(RenderEvent.Stage.TRANSLUCENT) { event ->
            val poseStack = event.poseStack
            val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
            val vertexBuffer = bufferSource.getBuffer(RenderType.lines())

            poseStack.pushPose()
            poseStack.translate(-event.camera.position.x, -event.camera.position.y, -event.camera.position.z)
            val entry = poseStack.last()
            val pose = entry.pose()

            fun drawSegment(from: Vec3, to: Vec3, color: IntArray) {
                val dx = (to.x - from.x).toFloat()
                val dy = (to.y - from.y).toFloat()
                val dz = (to.z - from.z).toFloat()
                val length = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

                if (length <= 0.0001F) {
                    return
                }

                val nx = dx / length
                val ny = dy / length
                val nz = dz / length

                vertexBuffer
                    .addVertex(pose, from.x.toFloat(), from.y.toFloat(), from.z.toFloat())
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(entry, nx, ny, nz)
                vertexBuffer
                    .addVertex(pose, to.x.toFloat(), to.y.toFloat(), to.z.toFloat())
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(entry, nx, ny, nz)
            }

            debugRays.forEach { ray ->
                val beforeHitColor = if (ray.perimeter) {
                    intArrayOf(70, 160, 255, 150)
                } else {
                    intArrayOf(70, 210, 255, 220)
                }

                val afterHitColor = if (ray.perimeter) {
                    intArrayOf(255, 120, 70, 150)
                } else {
                    intArrayOf(255, 70, 70, 230)
                }

                val hit = ray.hit

                if (hit == null) {
                    drawSegment(ray.start, ray.end, beforeHitColor)
                } else {
                    drawSegment(ray.start, hit, beforeHitColor)
                    drawSegment(hit, ray.end, afterHitColor)
                }
            }

            poseStack.popPose()
            bufferSource.endBatch(RenderType.lines())
        }
    }

    private fun collectRaycastCandidates(player: LocalPlayer, maxDistance: Double): List<RaycastCandidate> {
        val boundingBoxSize = maxDistance + 2.0
        val boundingBox = AABB(
            player.x - boundingBoxSize,
            player.y - boundingBoxSize,
            player.z - boundingBoxSize,
            player.x + boundingBoxSize,
            player.y + boundingBoxSize,
            player.z + boundingBoxSize
        )

        return player.level().getEntitiesOfClass(Entity::class.java, boundingBox) { it !== player && !it.isSpectator && it is ScannableEntity }
            .mapNotNull { entity ->
                val scannable = entity as? ScannableEntity ?: return@mapNotNull null
                RaycastCandidate(entity = entity, scannable = scannable, entityBox = entity.boundingBox)
            }
    }

    private fun inflateScannerHitbox(entityBox: AABB): AABB {
        val boxWidth = entityBox.xsize
        val boxHeight = entityBox.ysize
        val boxDepth = entityBox.zsize
        val boxVolume = boxWidth * boxHeight * boxDepth

        val minSize = 0.2
        val maxSize = 3.0
        val minSizeScale = 2.0
        val maxSizeScale = 1.0
        val steepCoefficient = 20.0

        val normalizedSize = (boxVolume - minSize) / (maxSize - minSize).coerceAtLeast(0.01)
        val inflationFactor = maxSizeScale + (minSizeScale - maxSizeScale) * Math.exp(-steepCoefficient * normalizedSize)

        return entityBox.inflate(
            (inflationFactor - 1) * boxWidth / 2,
            (inflationFactor - 1) * boxHeight / 2,
            (inflationFactor - 1) * boxDepth / 2
        )
    }

    private fun detectPhotoHitAlongRay(
        player: LocalPlayer,
        eyePos: Vec3,
        sample: PhotoRaySample,
        maxDistance: Double,
        candidates: List<RaycastCandidate>
    ): PhotoDetectedHit? {
        if (candidates.isEmpty()) return null

        val rayEnd = eyePos.add(sample.direction.scale(maxDistance))
        val hits = candidates.asSequence().mapNotNull { candidate ->
            val intersection = candidate.entityBox.clip(eyePos, rayEnd)
            if (intersection.isEmpty) {
                return@mapNotNull null
            }

            val point = intersection.get()
            PhotoRayHit(candidate.entity, candidate.scannable, point, eyePos.distanceTo(point))
        }.sortedBy { it.distance }

        for (hit in hits) {
            val blockHit = player.level().clip(
                ClipContext(
                    eyePos,
                    hit.point,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )

            if (blockHit.type == HitResult.Type.BLOCK) {
                val hitResult = blockHit as? BlockHitResult
                if (hitResult == null) continue
                val state = player.level().getBlockState(hitResult.blockPos)
                val blocksPhotoRay = state.canOcclude() && state.isSolidRender(player.level(), hitResult.blockPos)
                if (blocksPhotoRay) continue
            }

            val species = hit.scannable.resolvePokemonScan()?.getApparentSpecies()?.resourceIdentifier
            if (species != null) {
                return PhotoDetectedHit(
                    entity = hit.entity,
                    species = species,
                    point = hit.point,
                    distance = hit.distance
                )
            }
        }

        return null
    }

    private fun buildQualitySubjects(
        subjects: Collection<PhotoSubjectAccumulator>,
        inFrameRayCount: Int
    ): List<PokedexPhotoSubjectQuality> {
        val subjectStats = subjects
            .asSequence()
            .filter { it.rayHits > 0 }
            .map { subject -> buildSubjectStats(subject, inFrameRayCount) }
            .toList()
        val primaryStats = subjectStats.maxWithOrNull(
            compareBy<PhotoSubjectStats> { it.accumulator.rayHits }
                .thenBy { it.rayCoverage }
                .thenBy { -it.centerDistance }
                .thenBy { it.distanceScore }
        ) ?: return emptyList()
        val primary = buildPrimarySubjectQuality(primaryStats)
        val (otherPokemonBonus, herdBonus) = calculateOtherPokemonBonuses(primary, subjectStats)
        val familyCoverage = calculateFamilyCoverage(primary, subjectStats)
        val scoredPrimary = applyPrimarySubjectScoring(primary, otherPokemonBonus, herdBonus, familyCoverage)

        return subjectStats.map { stats ->
            if (stats === primaryStats) {
                scoredPrimary
            } else {
                buildSecondarySubjectMetadata(stats)
            }
        }.sortedWith(compareByDescending<PokedexPhotoSubjectQuality> { it.primarySubject }.thenByDescending { it.rayHits })
    }

    private fun buildSubjectStats(subject: PhotoSubjectAccumulator, inFrameRayCount: Int): PhotoSubjectStats {
        val rayCoverage = (subject.rayHits.toDouble() / inFrameRayCount.toDouble()).coerceIn(0.0, 1.0)
        val centroidX = subject.sampleXSum / subject.rayHits
        val centroidY = subject.sampleYSum / subject.rayHits
        val centerDistance = calculateCenterDistance(centroidX, centroidY)
        val distanceScore = calculateDistanceScore(subject.distanceValueSum / subject.rayHits)
        return PhotoSubjectStats(
            accumulator = subject,
            rayCoverage = rayCoverage,
            centerDistance = centerDistance,
            distanceScore = distanceScore
        )
    }

    private fun buildPrimarySubjectQuality(stats: PhotoSubjectStats): PokedexPhotoSubjectQuality {
        val subject = stats.accumulator
        val activeAnimations = getActiveAnimationNames(subject.entity).toList()
        val animationType = PokedexPhotoAnimationTypes.bestMatchFor(activeAnimations)
        val pokemon = (subject.entity as? PokemonEntity)?.pokemon
        val shiny = pokemon?.shiny == true
        val alpha = pokemon?.aspects?.contains("alpha") == true
        val wild = pokemon?.isWild() == true
        val alternateForm = pokemon?.let { it.form.name != it.species.standardForm.name } == true
        val sizeScore = calculateSizeScore(stats.rayCoverage, true)
        val placementScore = calculatePlacementScore(stats.centerDistance)
        val poseScore = animationType?.poseScore?.coerceIn(0, 800) ?: 0
        val shinyScore = if (shiny) ServerSettings.pokedexScannerPhotoShinyBonus.coerceAtLeast(0) else 0
        val alphaScore = if (alpha) ServerSettings.pokedexScannerPhotoAlphaBonus.coerceAtLeast(0) else 0
        val wildScore = if (wild) ServerSettings.pokedexScannerPhotoWildBonus.coerceAtLeast(0) else 0
        val formScore = if (alternateForm) ServerSettings.pokedexScannerPhotoFormBonus.coerceAtLeast(0) else 0
        val subjectScore = (sizeScore + placementScore + poseScore + shinyScore + alphaScore + wildScore + formScore).coerceIn(0, 5000)

        return PokedexPhotoSubjectQuality(
            species = subject.species.toString(),
            entityUuid = subject.entity.uuid,
            subjectScore = subjectScore,
            sizeScore = sizeScore,
            placementScore = placementScore,
            centerDistance = stats.centerDistance.toFloat(),
            distanceScore = stats.distanceScore,
            directionScore = 0,
            poseScore = poseScore,
            fullBodyScore = 0,
            rayHits = subject.rayHits,
            rayCoverage = stats.rayCoverage.toFloat(),
            perimeterHits = subject.perimeterHits,
            wholeBodyInFrame = true,
            clippingMultiplier = 1F,
            activeAnimations = activeAnimations,
            shiny = shiny,
            shinyMultiplier = 1F,
            shinyScore = shinyScore,
            alpha = alpha,
            alphaScore = alphaScore,
            wild = wild,
            wildScore = wildScore,
            alternateForm = alternateForm,
            formScore = formScore,
            rarityMultiplier = PokedexPhotoQuality.speciesRarityMultiplier(
                species = subject.species,
                uncommonMultiplier = ServerSettings.pokedexScannerPhotoUncommonSpeciesMultiplier,
                rareMultiplier = ServerSettings.pokedexScannerPhotoRareSpeciesMultiplier,
                ultraRareMultiplier = ServerSettings.pokedexScannerPhotoUltraRareSpeciesMultiplier
            )
        )
    }

    private fun buildSecondarySubjectMetadata(stats: PhotoSubjectStats): PokedexPhotoSubjectQuality {
        val subject = stats.accumulator
        return PokedexPhotoSubjectQuality(
            species = subject.species.toString(),
            entityUuid = subject.entity.uuid,
            primarySubject = false,
            subjectScore = 0,
            sizeScore = 0,
            placementScore = 0,
            centerDistance = stats.centerDistance.toFloat(),
            distanceScore = stats.distanceScore,
            directionScore = 0,
            poseScore = 0,
            fullBodyScore = 0,
            otherPokemonScore = 0,
            rayHits = subject.rayHits,
            rayCoverage = stats.rayCoverage.toFloat(),
            perimeterHits = subject.perimeterHits,
            wholeBodyInFrame = subject.perimeterHits == 0,
            clippingMultiplier = 1F,
            activeAnimations = emptyList(),
            shiny = false,
            shinyMultiplier = 1F,
            alpha = false,
            wild = false,
            alternateForm = false,
            rarityMultiplier = 1F
        )
    }

    private fun applyPrimarySubjectScoring(
        primary: PokedexPhotoSubjectQuality,
        otherPokemonBonus: Int,
        herdBonus: Int,
        familyCoverage: Float
    ): PokedexPhotoSubjectQuality {
        val wholeBodyInFrame = primary.perimeterHits == 0
        val scoringCoverage = (primary.rayCoverage + familyCoverage).coerceIn(0F, 1F)
        val sizeScore = calculateSizeScore(scoringCoverage.toDouble(), wholeBodyInFrame)
        val clippingMultiplier = if (wholeBodyInFrame) {
            1.0
        } else {
            ServerSettings.pokedexScannerPhotoOutOfFrameMultiplier.coerceIn(0F, 1F).toDouble()
        }
        val rawScore = sizeScore + primary.placementScore + otherPokemonBonus + primary.directionScore + herdBonus +
                primary.wildScore + primary.formScore + primary.poseScore +
                primary.habitatScore + primary.timeOfDayScore + primary.weatherScore +
                primary.shinyScore + primary.alphaScore
        val subjectScore = (rawScore * primary.rarityMultiplier * clippingMultiplier)
            .roundToInt()
            .coerceIn(0, 5000)

        return primary.copy(
            primarySubject = true,
            subjectScore = subjectScore,
            sizeScore = sizeScore,
            fullBodyScore = 0,
            otherPokemonScore = otherPokemonBonus,
            herdScore = herdBonus,
            wholeBodyInFrame = wholeBodyInFrame,
            clippingMultiplier = clippingMultiplier.toFloat()
        )
    }

    private fun calculatePhotoQualityScore(subjects: List<PokedexPhotoSubjectQuality>): Int {
        return subjects.firstOrNull { it.primarySubject }?.subjectScore ?: 0
    }

    private fun calculateSizeScore(rayCoverage: Double, wholeBodyInFrame: Boolean): Int {
        val scoreCap = ServerSettings.pokedexScannerPhotoCoverageScoreCap.coerceIn(0, 5000)
        val capAtCoverage = ServerSettings.pokedexScannerPhotoCoverageScoreCapAtPercent.toDouble().coerceIn(0.01, 1.0)
        val normalizedCoverage = (rayCoverage / capAtCoverage).coerceIn(0.0, 1.0)
        val coverageFactor = Math.pow(normalizedCoverage, 2.4)
        val tooLargeFactor = if (rayCoverage <= 0.55 || wholeBodyInFrame) {
            1.0
        } else {
            (1.0 - ((rayCoverage - 0.55) / 0.35 * 0.2)).coerceIn(0.8, 1.0)
        }
        return (scoreCap.toDouble() * coverageFactor * tooLargeFactor)
            .roundToInt()
            .coerceIn(0, scoreCap)
    }

    private fun calculateCenterDistance(centroidX: Double, centroidY: Double): Double {
        return (sqrt((centroidX * centroidX) + (centroidY * centroidY)) / sqrt(2.0)).coerceIn(0.0, 1.0)
    }

    private fun calculatePlacementScore(centerDistance: Double): Int {
        val placement = (1.0 - centerDistance).coerceIn(0.0, 1.0)
        val scoreCap = ServerSettings.pokedexScannerPhotoCenteredScoreCap.coerceIn(0, 5000)
        return (scoreCap.toDouble() * Math.pow(placement, 2.2)).roundToInt().coerceIn(0, scoreCap)
    }

    private fun calculateDistanceScore(distanceValue: Double): Int {
        return (1000.0 * distanceValue.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, 1000)
    }

    private fun isEntityInsidePhotoBounds(entity: Entity, projection: PhotoProjection): Boolean {
        val box = entity.boundingBox.inflate(0.01)
        val corners = arrayOf(
            Vec3(box.minX, box.minY, box.minZ),
            Vec3(box.minX, box.minY, box.maxZ),
            Vec3(box.minX, box.maxY, box.minZ),
            Vec3(box.minX, box.maxY, box.maxZ),
            Vec3(box.maxX, box.minY, box.minZ),
            Vec3(box.maxX, box.minY, box.maxZ),
            Vec3(box.maxX, box.maxY, box.minZ),
            Vec3(box.maxX, box.maxY, box.maxZ)
        )

        for (corner in corners) {
            val toCorner = corner.subtract(projection.eyePos)
            val depth = toCorner.dot(projection.forward)
            if (depth <= 0.05) return false

            val projectedX = toCorner.dot(projection.right) / (depth * projection.tanHalfHorizontal)
            val projectedY = toCorner.dot(projection.up) / (depth * projection.tanHalfVertical)
            val photoX = (projectedX - projection.captureCenterX) / projection.captureScaleX
            val photoY = (projectedY - projection.captureCenterY) / projection.captureScaleY
            if (photoX < -1.0 || photoX > 1.0 || photoY < -1.0 || photoY > 1.0) {
                return false
            }
        }

        return true
    }

    private fun calculateOtherPokemonBonuses(
        primary: PokedexPhotoSubjectQuality,
        subjects: List<PhotoSubjectStats>
    ): Pair<Int, Int> {
        val secondarySubjects = subjects
            .asSequence()
            .filter { it.accumulator.species.toString() != primary.species || it.accumulator.entity.uuid != primary.entityUuid }
            .toList()
        val rawBonus = secondarySubjects.sumOf(::calculateSecondaryPokemonBonus)
        val herdBonus = secondarySubjects.sumOf { subject -> calculateFamilyBonus(primary, subject) }
        val configuredCap = ServerSettings.pokedexScannerPhotoExtraPokemonBonusCap.coerceAtLeast(0)
        return rawBonus.coerceIn(0, configuredCap) to herdBonus.coerceAtLeast(0)
    }

    private fun calculateFamilyCoverage(primary: PokedexPhotoSubjectQuality, subjects: List<PhotoSubjectStats>): Float {
        return subjects
            .asSequence()
            .filter { it.accumulator.species.toString() != primary.species || it.accumulator.entity.uuid != primary.entityUuid }
            .filter { subject -> isFamilyMember(primary.species, subject.accumulator.species.toString()) }
            .sumOf { it.rayCoverage }
            .toFloat()
            .coerceIn(0F, 1F)
    }

    private fun calculateSecondaryPokemonBonus(subject: PhotoSubjectStats): Int {
        if (subject.accumulator.rayHits <= 0) return 0
        val visibility = (subject.rayCoverage.toFloat() / 0.08F).coerceIn(0.25F, 1F)
        val placement = (1F - subject.centerDistance.toFloat()).coerceIn(0.35F, 1F)
        val scoreCap = ServerSettings.pokedexScannerPhotoExtraPokemonBonusPerSubject.coerceAtLeast(0)
        return (scoreCap * visibility * placement).roundToInt().coerceIn(0, scoreCap)
    }

    private fun calculateFamilyBonus(primary: PokedexPhotoSubjectQuality, subject: PhotoSubjectStats): Int {
        if (!isFamilyMember(primary.species, subject.accumulator.species.toString())) return 0
        val visibility = (subject.rayCoverage.toFloat() / 0.12F).coerceIn(0F, 1F)
        val placement = (1F - subject.centerDistance.toFloat()).coerceIn(0F, 1F)
        val scoreCap = ServerSettings.pokedexScannerPhotoFamilyBonus.coerceAtLeast(0)
        return (scoreCap * visibility * placement).roundToInt().coerceIn(0, scoreCap)
    }

    private fun isFamilyMember(primarySpecies: String, subjectSpecies: String): Boolean {
        return primarySpecies == subjectSpecies || areSpeciesInSameEvolutionLine(primarySpecies, subjectSpecies)
    }

    private fun areSpeciesInSameEvolutionLine(firstSpecies: String, secondSpecies: String): Boolean {
        val firstId = ResourceLocation.tryParse(firstSpecies) ?: return false
        val secondId = ResourceLocation.tryParse(secondSpecies) ?: return false
        if (firstId == secondId) return true
        return secondId in collectEvolutionFamily(firstId)
    }

    private fun collectEvolutionFamily(speciesId: ResourceLocation): Set<ResourceLocation> {
        val rootSpecies = PokemonSpecies.getByIdentifier(speciesId) ?: return setOf(speciesId)
        val family = linkedSetOf<ResourceLocation>()
        val processed = hashSetOf<ResourceLocation>()
        val queue = ArrayDeque<Species>()
        queue.add(rootSpecies)
        var preEvolution = rootSpecies.preEvolution?.species
        while (preEvolution != null && family.add(preEvolution.resourceIdentifier)) {
            queue.add(preEvolution)
            preEvolution = preEvolution.preEvolution?.species
        }

        while (queue.isNotEmpty()) {
            val species = queue.removeFirst()
            if (!processed.add(species.resourceIdentifier)) continue
            family.add(species.resourceIdentifier)
            species.evolutions
                .asSequence()
                .mapNotNull { evolution -> evolution.result.species?.asIdentifierDefaultingNamespace() }
                .mapNotNull(PokemonSpecies::getByIdentifier)
                .forEach(queue::add)
        }
        return family
    }

    private fun getActiveAnimationNames(entity: Entity): Set<String> {
        val delegate = (entity as? PokemonEntity)?.delegate as? PokemonClientDelegate ?: return emptySet()
        val names = linkedSetOf<String>()
        val currentPose = delegate.currentPose
        if (currentPose != null) {
            names += normalizeAnimationName(currentPose)
            delegate.currentModel
                ?.poses
                ?.get(currentPose)
                ?.animations
                ?.filterIsInstance<BedrockPoseAnimation>()
                ?.map { it.animation.name }
                ?.mapTo(names, ::normalizeAnimationName)
        }

        (listOfNotNull(delegate.primaryAnimation) + delegate.allActiveAnimations)
            .mapNotNull(::getBedrockAnimationName)
            .map(::normalizeAnimationName)
            .filterTo(names) { it.isNotBlank() }

        return names
    }

    private fun getBedrockAnimationName(animation: ActiveAnimation): String? {
        return when (animation) {
            is PrimaryAnimation -> getBedrockAnimationName(animation.animation)
            is BedrockActiveAnimation -> animation.animation.name
            else -> null
        }
    }

    private fun normalizeAnimationName(name: String): String {
        return name.lowercase(Locale.ROOT).substringAfterLast('.')
    }

    private fun photoCaptureBoundsForImage(sourceWidth: Int, sourceHeight: Int): PhotoCaptureBounds {
        val window = Minecraft.getInstance().window
        val guiWidth = window.guiScaledWidth.coerceAtLeast(1)
        val guiHeight = window.guiScaledHeight.coerceAtLeast(1)
        val guiBounds = photoCaptureBoundsForGui(guiWidth, guiHeight)
        val scaleX = sourceWidth.toDouble() / guiWidth.toDouble()
        val scaleY = sourceHeight.toDouble() / guiHeight.toDouble()
        val width = (guiBounds.width * scaleX).roundToInt().coerceIn(1, sourceWidth.coerceAtLeast(1))
        val height = (guiBounds.height * scaleY).roundToInt().coerceIn(1, sourceHeight.coerceAtLeast(1))
        val centerX = (sourceWidth / 2.0) + (PHOTO_CAPTURE_FRAME_OFFSET_X * scaleX)
        val centerY = (sourceHeight / 2.0) + (PHOTO_CAPTURE_FRAME_OFFSET_Y * scaleY)
        val left = (centerX - (width / 2.0)).roundToInt().coerceIn(0, (sourceWidth - width).coerceAtLeast(0))
        val top = (centerY - (height / 2.0)).roundToInt().coerceIn(0, (sourceHeight - height).coerceAtLeast(0))
        return PhotoCaptureBounds(left, top, width, height)
    }

    private fun resizeToCaptureArea(image: NativeImage, size: Int): NativeImage {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val bounds = photoCaptureBoundsForImage(sourceWidth, sourceHeight)

        val resized = NativeImage(size, size, false)
        for (y in 0 until size) {
            val sourceY = bounds.top + ((y * bounds.height) / size)
            for (x in 0 until size) {
                val sourceX = bounds.left + ((x * bounds.width) / size)
                resized.setPixelRGBA(x, y, image.getPixelRGBA(sourceX, sourceY))
            }
        }
        return resized
    }
}
