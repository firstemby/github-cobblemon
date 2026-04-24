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
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.pokedex.PokedexScannerRenderer
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.client.render.DeferredRenderer
import com.cobblemon.mod.common.net.messages.client.pokedex.ServerConfirmedRegisterPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishScanningPacket
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket
import com.cobblemon.mod.common.platform.events.RenderEvent
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
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL40
import java.util.LinkedHashSet
import kotlin.math.exp
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tan

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
        private const val BASE_PHOTO_RAYCAST_COUNT = 6
        private const val PHOTO_DEBUG_RAY_DURATION_MS = 10_000L
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
    private var capturedDebugRays: List<Pair<Vec3, Vec3>> = emptyList()
    private var capturedDebugRaysExpireAt: Long = 0L

    private data class PhotoCaptureMetadata(
        val detectedSpecies: List<String>,
        val biome: String,
        val dimension: String,
        val timeOfDayTicks: Long
    ) {
        companion object {
            val EMPTY = PhotoCaptureMetadata(
                detectedSpecies = emptyList(),
                biome = "minecraft:unknown",
                dimension = "minecraft:overworld",
                timeOfDayTicks = 0L
            )
        }
    }

    private data class RaycastCandidate(
        val scannable: ScannableEntity,
        val inflatedBox: AABB
    )

    private data class PhotoRayHit(
        val scannable: ScannableEntity,
        val point: Vec3,
        val distance: Double
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
            val player = Minecraft.getInstance().player
            if (player != null) {
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
            player.displayClientMessage(Component.literal("Photo scan detected: none"), false)
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
        player.displayClientMessage(
            Component.literal("Photo scan detected (${speciesNames.size}): ${shown.joinToString(", ")}$suffix"),
            false
        )
    }

    private fun collectPhotoMetadata(player: LocalPlayer): PhotoCaptureMetadata {
        val level = player.level()
        val eyePos = player.getEyePosition(1.0F)
        val raycastDistance = Cobblemon.config.pokedexScannerPhotoRaycastDistance.coerceIn(4.0, 512.0)
        val rayDirections = computePhotoRayDirections(player)
        val candidates = collectRaycastCandidates(player, raycastDistance)
        val seenSpecies = LinkedHashSet<String>()

        rayDirections.forEach { direction ->
            detectSpeciesAlongRay(player, eyePos, direction, raycastDistance, candidates)?.let { seenSpecies.add(it.toString()) }
        }

        val biome = level.getBiome(player.blockPosition()).unwrapKey()
            .map { it.location().toString() }
            .orElse("minecraft:unknown")

        return PhotoCaptureMetadata(
            detectedSpecies = seenSpecies.toList(),
            biome = biome,
            dimension = level.dimension().location().toString(),
            timeOfDayTicks = ((level.dayTime % 24000L) + 24000L) % 24000L
        )
    }

    private fun buildPhotoRaySamples(): List<Pair<Double, Double>> {
        val density = Cobblemon.config.pokedexScannerPhotoRaycastDensity.coerceIn(1, 6)
        val rayCount = BASE_PHOTO_RAYCAST_COUNT * (1 shl (density - 1))
        val samples = ArrayList<Pair<Double, Double>>(rayCount)

        repeat(rayCount) { index ->
            val horizontal = (((index + 0.5) / rayCount.toDouble()) * 2.0) - 1.0
            val vertical = (radicalInverseBase2(index + 1) * 2.0) - 1.0
            samples += horizontal.coerceIn(-1.0, 1.0) to vertical.coerceIn(-1.0, 1.0)
        }

        if (samples.isNotEmpty()) {
            samples[0] = 0.0 to 0.0
        }
        return samples
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
        val samples = buildPhotoRaySamples()
        val renderTarget = Minecraft.getInstance().mainRenderTarget
        val aspectRatio = (renderTarget.width.toDouble() / max(1, renderTarget.height).toDouble()).coerceIn(0.5, 4.0)
        val squareScaleX = if (aspectRatio >= 1.0) 1.0 / aspectRatio else 1.0
        val squareScaleY = if (aspectRatio >= 1.0) 1.0 else aspectRatio
        val verticalFovRadians = Math.toRadians((ZOOM_BASE_FOV * zoomModifier).coerceIn(10F, 170F).toDouble())
        val halfVerticalFov = verticalFovRadians * 0.5
        val halfHorizontalFov = atan(tan(halfVerticalFov) * aspectRatio)
        val tanHalfVertical = tan(halfVerticalFov)
        val tanHalfHorizontal = tan(halfHorizontalFov)

        val forward = player.getViewVector(1.0F).normalize()
        val worldUp = Vec3(0.0, 1.0, 0.0)
        val fallbackUp = Vec3(0.0, 0.0, 1.0)
        var right = forward.cross(worldUp)
        if (right.lengthSqr() < 1.0E-6) {
            right = forward.cross(fallbackUp)
        }
        right = right.normalize()
        val up = right.cross(forward).normalize()

        return samples.map { (sampleX, sampleY) ->
            val boundedSampleX = sampleX * squareScaleX
            val boundedSampleY = sampleY * squareScaleY
            forward
                .add(right.scale(tanHalfHorizontal * boundedSampleX))
                .add(up.scale(tanHalfVertical * boundedSampleY))
                .normalize()
        }
    }

    private fun captureDebugRays(player: LocalPlayer) {
        val eyePos = player.getEyePosition(1.0F)
        val raycastDistance = Cobblemon.config.pokedexScannerPhotoRaycastDistance.coerceIn(4.0, 512.0)
        capturedDebugRays = computePhotoRayDirections(player).map { direction ->
            eyePos to eyePos.add(direction.scale(raycastDistance))
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

            debugRays.forEach { (start, end) ->
                val dx = (end.x - start.x).toFloat()
                val dy = (end.y - start.y).toFloat()
                val dz = (end.z - start.z).toFloat()
                val length = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(0.0001F)
                val nx = dx / length
                val ny = dy / length
                val nz = dz / length

                vertexBuffer
                    .addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
                    .setColor(70, 210, 255, 190)
                    .setNormal(entry, nx, ny, nz)
                vertexBuffer
                    .addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
                    .setColor(255, 110, 110, 190)
                    .setNormal(entry, nx, ny, nz)
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
                RaycastCandidate(scannable = scannable, inflatedBox = inflateScannerHitbox(entity.boundingBox))
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

    private fun detectSpeciesAlongRay(
        player: LocalPlayer,
        eyePos: Vec3,
        direction: Vec3,
        maxDistance: Double,
        candidates: List<RaycastCandidate>
    ): ResourceLocation? {
        if (candidates.isEmpty()) return null

        val rayEnd = eyePos.add(direction.scale(maxDistance))
        val hits = candidates.asSequence().mapNotNull { candidate ->
            val intersection = candidate.inflatedBox.clip(eyePos, rayEnd)
            if (intersection.isEmpty) {
                return@mapNotNull null
            }

            val point = intersection.get()
            PhotoRayHit(candidate.scannable, point, eyePos.distanceTo(point))
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
                return species
            }
        }

        return null
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
