/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.debug.speed

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.net.messages.client.debug.SpeedTunerEntry
import com.cobblemon.mod.common.net.messages.server.debug.SetSpeedTunerSprintSpeedPacket
import com.cobblemon.mod.common.net.messages.server.debug.SetSpeedTunerWalkSpeedPacket
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/**
 * Client side session for tuning species walk and sprint speeds. A display only pokemon runs a circle or a line at
 * exactly the speed being tuned, and edits are kept in blocks per second and saved to a file for applying to the
 * species data later.
 */
object SpeedTuner {
    enum class Gait(val jsonKey: String) { WALK("walkSpeed"), SPRINT("sprintSpeed") }
    enum class Track { CIRCLE, LINE }

    // Vanilla player top speeds in blocks per second, shown and run alongside for comparison
    const val PLAYER_WALK_SPEED = 4.317
    const val PLAYER_SPRINT_SPEED = 5.612
    // Sprint speed shown for species without one yet, matches the data script default
    const val DEFAULT_SPRINT_SPEED = 5.6
    // Walk speed values are multiplied by this in PokemonMoveControl
    const val WALK_SPEED_SCALE = 2.5
    // Friction of ordinary blocks, walk estimates assume flat normal ground
    const val VANILLA_BLOCK_FRICTION = 0.6
    const val TICKS_PER_SECOND = 20.0

    const val DEFAULT_CIRCLE_RADIUS = 6.0
    const val DEFAULT_LINE_LENGTH = 16.0
    // How often the track gets outlined, and the gap between outline markers in blocks
    const val TRACK_MARKER_INTERVAL_TICKS = 10
    const val TRACK_MARKER_SPACING = 1.0
    // Markers sit this far above the start height so they aren't inside the ground
    const val MARKER_HEIGHT = 0.1
    // Edits made while dragging a slider are written out at most this often
    const val AUTOSAVE_INTERVAL_TICKS = 40

    private const val UNITS = "blocks per second"
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val sessionFile: Path
        get() = Minecraft.getInstance().gameDirectory.toPath().resolve("cobblemon-dev").resolve("speed_tuning_session.json")

    var active = false
        private set
    var entries: List<SpeedTunerEntry> = emptyList()
        private set
    var selected: SpeedTunerEntry? = null
        private set
    var gait = Gait.SPRINT
        set(value) {
            field = value
            ensureSelectionVisible()
            restartRun()
        }
    var track = Track.CIRCLE
        set(value) {
            field = value
            restartRun()
        }
    var radius = DEFAULT_CIRCLE_RADIUS
    var lineLength = DEFAULT_LINE_LENGTH
    var paused = false

    // Species id to edited speeds by gait json key, all in blocks per second
    private val edits = mutableMapOf<ResourceLocation, MutableMap<String, Double>>()
    private val reviewed = mutableSetOf<ResourceLocation>()
    private var dirty = false
    private var subscribed = false

    private var origin = Vec3.ZERO
    private var lineDirection = Vec3(0.0, 0.0, 1.0)
    private var distance = 0.0
    private var referenceDistance = 0.0
    private var ticks = 0L
    private var puppet: PokemonEntity? = null

    fun open(entries: List<SpeedTunerEntry>) {
        val player = Minecraft.getInstance().player ?: return
        this.entries = entries.sortedBy { it.dex }
        if (!active) {
            origin = player.position()
            val look = Vec3(player.lookAngle.x, 0.0, player.lookAngle.z)
            lineDirection = if (look.lengthSqr() > 0) look.normalize() else Vec3(0.0, 0.0, 1.0)
            load()
            active = true
            // The server starts from the species data, so bring it up to date with the loaded session
            edits.forEach { (id, speeds) ->
                speeds[Gait.SPRINT.jsonKey]?.let { sendSprintSpeed(id, it.toFloat()) }
                speeds[Gait.WALK.jsonKey]?.let { sendWalkSpeed(id, walkSpeedFor(it)) }
            }
        }
        subscribe()
        ensureSelectionVisible()
        Minecraft.getInstance().setScreen(SpeedTunerScreen())
    }

    fun stop() {
        save()
        active = false
        puppet = null
        selected = null
    }

    /** Species that make sense for the current gait. Only species with a sprint speed can sprint. */
    fun visibleEntries(): List<SpeedTunerEntry> {
        return if (gait == Gait.SPRINT) entries.filter { it.sprintSpeed != null } else entries
    }

    fun select(entry: SpeedTunerEntry) {
        selected = entry
        puppet = null
        restartRun()
    }

    fun step(offset: Int) {
        val visible = visibleEntries().ifEmpty { return }
        val index = visible.indexOf(selected).coerceAtLeast(0)
        select(visible[Math.floorMod(index + offset, visible.size)])
    }

    /** Finds a species by dex number or by name, preferring names that start with the search. */
    fun search(text: String): Boolean {
        val query = text.trim().lowercase().ifEmpty { return false }
        val visible = visibleEntries()
        val match = query.toIntOrNull()?.let { dex -> visible.firstOrNull { it.dex == dex } }
            ?: visible.firstOrNull { it.speciesId.path.startsWith(query) }
            ?: visible.firstOrNull { it.speciesId.path.contains(query) }
            ?: return false
        select(match)
        return true
    }

    fun speed(entry: SpeedTunerEntry, gait: Gait = this.gait): Double {
        return edits[entry.speciesId]?.get(gait.jsonKey) ?: originalSpeed(entry, gait)
    }

    fun originalSpeed(entry: SpeedTunerEntry, gait: Gait = this.gait): Double {
        return when (gait) {
            Gait.SPRINT -> entry.sprintSpeed?.toDouble() ?: DEFAULT_SPRINT_SPEED
            Gait.WALK -> estimatedWalkSpeed(entry)
        }
    }

    /**
     * Walk speed is still relative in the species data, so this works out what it comes to in blocks per second on
     * normal ground. Walkers always use the same speed modifier so this is exact.
     */
    fun estimatedWalkSpeed(entry: SpeedTunerEntry): Double {
        val input = walkInputPerWalkSpeed() * entry.walkSpeed
        return PokemonMoveControl.vanillaGroundTopSpeed(input, VANILLA_BLOCK_FRICTION) * TICKS_PER_SECOND
    }

    /** The other way round, walkSpeed that comes out at [blocksPerSecond]. Top speed grows with the square of the input */
    fun walkSpeedFor(blocksPerSecond: Double): Float {
        val topSpeedPerInputSquared = PokemonMoveControl.vanillaGroundTopSpeed(1.0, VANILLA_BLOCK_FRICTION)
        val input = sqrt(blocksPerSecond / TICKS_PER_SECOND / topSpeedPerInputSquared)
        return (input / walkInputPerWalkSpeed()).toFloat()
    }

    // Move control input for each unit of walkSpeed
    private fun walkInputPerWalkSpeed(): Double {
        return Attributes.MOVEMENT_SPEED.value().defaultValue * PokemonMoveControl.GROUND_SPEED_MODIFIER * WALK_SPEED_SCALE
    }

    fun referenceSpeed() = if (gait == Gait.SPRINT) PLAYER_SPRINT_SPEED else PLAYER_WALK_SPEED

    fun setSpeed(value: Double) {
        val entry = selected ?: return
        edits.getOrPut(entry.speciesId) { mutableMapOf() }[gait.jsonKey] = value
        dirty = true
        when (gait) {
            Gait.SPRINT -> sendSprintSpeed(entry.speciesId, value.toFloat())
            Gait.WALK -> sendWalkSpeed(entry.speciesId, walkSpeedFor(value))
        }
    }

    fun resetSpeed() {
        val entry = selected ?: return
        edits[entry.speciesId]?.let {
            it.remove(gait.jsonKey)
            if (it.isEmpty()) edits.remove(entry.speciesId)
        }
        dirty = true
        when (gait) {
            Gait.SPRINT -> sendSprintSpeed(entry.speciesId, entry.sprintSpeed)
            Gait.WALK -> sendWalkSpeed(entry.speciesId, entry.walkSpeed)
        }
    }

    private fun sendSprintSpeed(speciesId: ResourceLocation, sprintSpeed: Float?) {
        SetSpeedTunerSprintSpeedPacket(speciesId, sprintSpeed).sendToServer()
    }

    /** Walk speeds go over in the species data's own units, converted from the tuner's blocks per second */
    private fun sendWalkSpeed(speciesId: ResourceLocation, walkSpeed: Float) {
        SetSpeedTunerWalkSpeedPacket(speciesId, walkSpeed).sendToServer()
    }

    fun isEdited(entry: SpeedTunerEntry) = edits[entry.speciesId]?.containsKey(gait.jsonKey) == true

    fun isReviewed(entry: SpeedTunerEntry) = entry.speciesId in reviewed

    fun toggleReviewed() {
        val entry = selected ?: return
        if (!reviewed.remove(entry.speciesId)) reviewed.add(entry.speciesId)
        dirty = true
    }

    fun editedCount() = edits.size

    fun reviewedCount() = reviewed.size

    fun restartRun() {
        distance = 0.0
        referenceDistance = 0.0
    }

    private fun ensureSelectionVisible() {
        val visible = visibleEntries()
        if (selected !in visible) {
            visible.firstOrNull()?.let { select(it) } ?: run { selected = null }
        }
    }

    private fun subscribe() {
        if (subscribed) return
        subscribed = true
        PlatformEvents.CLIENT_TICK_POST.subscribe { tick() }
        PlatformEvents.RENDER.subscribe { render(it) }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe { if (active) stop() }
    }

    private fun tick() {
        if (!active) return
        ticks++
        if (dirty && ticks % AUTOSAVE_INTERVAL_TICKS == 0L) save()
        val level = Minecraft.getInstance().level ?: return
        val entry = selected ?: return
        val display = puppet?.takeIf { it.level() === level } ?: createPuppet(level, entry)?.also { puppet = it } ?: return

        if (!paused) {
            distance += speed(entry) / TICKS_PER_SECOND
            referenceDistance += referenceSpeed() / TICKS_PER_SECOND
            display.tickCount++
        }
        val lapLength = lapLength()
        // Line runs start over from the beginning, so jump without smoothing between the two ends
        val wrapped = distance >= lapLength
        distance %= lapLength
        referenceDistance %= lapLength

        display.setOldPosAndRot()
        display.yBodyRotO = display.yBodyRot
        display.yHeadRotO = display.yHeadRot
        val position = trackPosition(distance)
        // Keep yaw continuous with last tick so nothing lerping it spins round at the wrap
        val yaw = display.yBodyRot + Mth.wrapDegrees(trackYaw(distance) - display.yBodyRot)
        display.setPos(position.x, position.y, position.z)
        display.yRot = yaw
        display.yBodyRot = yaw
        display.yHeadRot = yaw
        if (wrapped && track == Track.LINE) {
            display.setOldPosAndRot()
            display.yBodyRotO = yaw
            display.yHeadRotO = yaw
        }
        display.isPokemonSprinting = gait == Gait.SPRINT
        display.entityData.set(PokemonEntity.POSE_TYPE, PoseType.WALK)
        display.entityData.set(PokemonEntity.MOVING, !paused)
        // Animation age and pose changes advance in the client delegate's tick, which normally runs from the
        // entity's own tick. The display pokemon is never ticked so do it here, and not while paused so it freezes
        if (!paused) {
            display.delegate.tick(display)
        }

        val reference = trackPosition(referenceDistance)
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, reference.x, reference.y + MARKER_HEIGHT, reference.z, 0.0, 0.0, 0.0)
        if (ticks % TRACK_MARKER_INTERVAL_TICKS == 0L) outlineTrack(level)
    }

    private fun render(event: RenderEvent) {
        if (!active) return
        val puppet = puppet ?: return
        val level = Minecraft.getInstance().level ?: return
        if (puppet.level() !== level) return

        val camera = event.camera.position
        val partialTick = event.tickCounter.getGameTimeDeltaPartialTick(false)
        val x = Mth.lerp(partialTick.toDouble(), puppet.xo, puppet.x) - camera.x
        val y = Mth.lerp(partialTick.toDouble(), puppet.yo, puppet.y) - camera.y
        val z = Mth.lerp(partialTick.toDouble(), puppet.zo, puppet.z) - camera.z
        val light = LevelRenderer.getLightColor(level, puppet.blockPosition())
        val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
        Minecraft.getInstance().entityRenderDispatcher.render(puppet, x, y, z, puppet.yRot, partialTick, event.poseStack, buffers, light)
        buffers.endBatch()
    }

    /** Same setup as HabitatBlockRenderer, a pokemon that only exists to be drawn. */
    private fun createPuppet(level: ClientLevel, entry: SpeedTunerEntry): PokemonEntity? {
        val species = PokemonSpecies.getByIdentifier(entry.speciesId) ?: return null
        val pokemon = Pokemon().apply {
            isClient = true
            this.species = species
            initialize()
        }
        return PokemonEntity(level, pokemon).apply {
            noPhysics = true
            setNoGravity(true)
            val start = trackPosition(distance)
            setPos(start.x, start.y, start.z)
            setOldPosAndRot()
        }
    }

    private fun lapLength() = if (track == Track.CIRCLE) 2 * PI * radius else lineLength

    private fun trackPosition(along: Double): Vec3 {
        return when (track) {
            Track.CIRCLE -> {
                val angle = along / radius
                origin.add(cos(angle) * radius, 0.0, sin(angle) * radius)
            }
            Track.LINE -> origin.add(lineDirection.scale(along))
        }
    }

    private fun trackYaw(along: Double): Float {
        val direction = when (track) {
            // Tangent of the circle in the direction of travel
            Track.CIRCLE -> (along / radius).let { Vec3(-sin(it), 0.0, cos(it)) }
            Track.LINE -> lineDirection
        }
        return (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG).toFloat() - 90F
    }

    private fun outlineTrack(level: ClientLevel) {
        val markers = (lapLength() / TRACK_MARKER_SPACING).toInt()
        for (i in 0..markers) {
            val marker = trackPosition(i * TRACK_MARKER_SPACING)
            level.addParticle(ParticleTypes.COMPOSTER, marker.x, marker.y + MARKER_HEIGHT, marker.z, 0.0, 0.0, 0.0)
        }
    }

    private fun toJson(): JsonObject {
        val species = JsonObject()
        edits.toSortedMap(compareBy { it.toString() }).forEach { (id, speeds) ->
            species.add(id.toString(), JsonObject().also { speedsJson -> speeds.forEach { (key, value) -> speedsJson.addProperty(key, value) } })
        }
        return JsonObject().apply {
            addProperty("units", UNITS)
            add("species", species)
            add("reviewed", JsonArray().also { array -> reviewed.map { it.toString() }.sorted().forEach { array.add(it) } })
        }
    }

    fun save() {
        runCatching {
            Files.createDirectories(sessionFile.parent)
            Files.writeString(sessionFile, gson.toJson(toJson()))
            dirty = false
        }.onFailure { Cobblemon.LOGGER.error("Couldn't save the speed tuning session", it) }
    }

    /** Writes a timestamped copy of the session next to it and posts links to it in chat. */
    fun export() {
        save()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val file = sessionFile.resolveSibling("speed_tuning_$stamp.json").toAbsolutePath()
        runCatching {
            Files.writeString(file, gson.toJson(toJson()))
            val message = Component.literal("Speed tuning exported: ")
                .append(chatLink("[open file]", ClickEvent.Action.OPEN_FILE, file.toString()))
                .append(" ")
                .append(chatLink("[open folder]", ClickEvent.Action.OPEN_FILE, file.parent.toString()))
                .append(" ")
                .append(chatLink("[copy path]", ClickEvent.Action.COPY_TO_CLIPBOARD, file.toString()))
            Minecraft.getInstance().player?.displayClientMessage(message, false)
        }.onFailure { Cobblemon.LOGGER.error("Couldn't export the speed tuning session", it) }
    }

    private fun chatLink(label: String, action: ClickEvent.Action, value: String): Component {
        return Component.literal(label).withStyle { style ->
            style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(ClickEvent(action, value))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(value)))
        }
    }

    private fun load() {
        edits.clear()
        reviewed.clear()
        if (!Files.exists(sessionFile)) return
        runCatching {
            val json = JsonParser.parseString(Files.readString(sessionFile)).asJsonObject
            json.getAsJsonObject("species")?.entrySet()?.forEach { (id, speeds) ->
                val map = edits.getOrPut(ResourceLocation.parse(id)) { mutableMapOf() }
                speeds.asJsonObject.entrySet().forEach { (key, value) -> map[key] = value.asDouble }
            }
            json.getAsJsonArray("reviewed")?.forEach { reviewed.add(ResourceLocation.parse(it.asString)) }
        }.onFailure { Cobblemon.LOGGER.error("Couldn't load the speed tuning session", it) }
    }
}
