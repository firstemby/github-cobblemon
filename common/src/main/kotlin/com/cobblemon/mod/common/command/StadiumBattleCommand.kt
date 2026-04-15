/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.stadium.EntitySnapshot
import com.cobblemon.mod.common.battles.stadium.PendingStadiumBattle
import com.cobblemon.mod.common.battles.stadium.StadiumBattleCameraCoordinator
import com.cobblemon.mod.common.battles.stadium.StadiumBattleConfig
import com.cobblemon.mod.common.battles.stadium.StadiumBattleFormat
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumPlacement
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.api.camera.CameraController
import com.cobblemon.mod.common.api.camera.CameraManager
import com.cobblemon.mod.common.api.molangui.MolangUiSessionManager
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraDebugPacket
import com.cobblemon.mod.common.net.messages.client.camera.SplitScreenPacket
import com.cobblemon.mod.common.util.permission
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.phys.Vec3

object StadiumBattleCommand {
    private const val ROOT = "stadium"
    private const val NODE_BATTLE = "battle"
    private const val ARG_STADIUM = "stadiumId"
    private const val ARG_FORMAT = "format"
    private const val ARG_SIDE_ONE = "entityA"
    private const val ARG_SIDE_TWO = "entityB"
    private const val ARG_CAMERA = "playCamera"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(ROOT)
                .permission(CobblemonPermissions.STADIUM_BATTLE)
                .then(
                    Commands.literal(NODE_BATTLE)
                        .then(
                            Commands.argument(ARG_STADIUM, StringArgumentType.string())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(StadiumBattleRegistry.ids(), builder)
                                }
                                .then(
                                    Commands.argument(ARG_FORMAT, StringArgumentType.string())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(listOf("single", "double"), builder)
                                        }
                                        .then(
                                            Commands.argument(ARG_SIDE_ONE, EntityArgument.entity())
                                                .then(
                                                    Commands.argument(ARG_SIDE_TWO, EntityArgument.entity())
                                                        .executes { execute(it, null) }
                                                        .then(
                                                            Commands.argument(ARG_CAMERA, BoolArgumentType.bool())
                                                                .executes { context ->
                                                                    execute(context, BoolArgumentType.getBool(context, ARG_CAMERA))
                                                                }
                                                        )
                                                )
                                        )
                                )
                        )
                )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, cameraOverride: Boolean?): Int {
        val stadiumId = StringArgumentType.getString(context, ARG_STADIUM)
        val formatId = StringArgumentType.getString(context, ARG_FORMAT)

        val entityA = EntityArgument.getEntity(context, ARG_SIDE_ONE)
        val entityB = EntityArgument.getEntity(context, ARG_SIDE_TWO)

        if (entityA !is LivingEntity || entityB !is LivingEntity) {
            context.source.sendFailure(Component.literal("Both participants must be living entities."))
            return 0
        }

        val config = StadiumBattleRegistry.get(stadiumId)
        if (config == null) {
            context.source.sendFailure(Component.literal("Unknown stadium '$stadiumId'."))
            return 0
        }

        val stadiumFormat = StadiumBattleFormat.fromId(formatId)
        if (stadiumFormat == null) {
            context.source.sendFailure(Component.literal("Unknown stadium battle format '$formatId'. Expected 'single' or 'double'."))
            return 0
        }

        val layout = config.layoutFor(stadiumFormat)
        if (layout == null) {
            context.source.sendFailure(Component.literal("Stadium '$stadiumId' does not define layout for $formatId battles."))
            return 0
        }

        val server = context.source.server
        val fallbackLevel = context.source.level
        val targetLevel = resolveLevel(server, config.dimension, fallbackLevel) ?: run {
            context.source.sendFailure(Component.literal("Unable to resolve dimension for stadium '$stadiumId'."))
            return 0
        }

        val battleFormat = when (stadiumFormat) {
            StadiumBattleFormat.SINGLES -> BattleFormat.GEN_9_SINGLES.copy()
            StadiumBattleFormat.DOUBLES -> BattleFormat.GEN_9_DOUBLES.copy()
        }

        val origin = config.origin
        val side1ActorPlacement = layout.side1.actors.firstOrNull()
        val side2ActorPlacement = layout.side2.actors.firstOrNull()

        if (side1ActorPlacement == null || side2ActorPlacement == null) {
            context.source.sendFailure(Component.literal("Stadium layout must define at least one actor placement per side."))
            return 0
        }

        val snapshots = listOf(entityA, entityB).map { snapshot(it) }

        val side1ActorPositions = layout.side1.actors.map { toWorld(origin, it.position) }
        val side2ActorPositions = layout.side2.actors.map { toWorld(origin, it.position) }

        val fallbackLookAt1 = average(side2ActorPositions)
        val fallbackLookAt2 = average(side1ActorPositions)

        teleportParticipant(entityA, targetLevel, origin, side1ActorPlacement, fallbackLookAt1)
        teleportParticipant(entityB, targetLevel, origin, side2ActorPlacement, fallbackLookAt2)

        val opponentPlayersForA = collectPlayers(entityB)
        val opponentPlayersForB = collectPlayers(entityA)

        val actorA = createActor(entityA, layout.side1.pokemon, origin, battleFormat, opponentPlayersForA, context, "Side 1")
            ?: run {
                restoreSnapshots(snapshots)
                return 0
            }
        val actorB = createActor(entityB, layout.side2.pokemon, origin, battleFormat, opponentPlayersForB, context, "Side 2")
            ?: run {
                restoreSnapshots(snapshots)
                return 0
            }

        val viewers = buildList {
            if (entityA is ServerPlayer) add(entityA)
            if (entityB is ServerPlayer) add(entityB)
        }

        val playCamera = cameraOverride ?: (config.cameraTimeline != null)

        if (playCamera && config.cameraTimeline != null && viewers.isNotEmpty()) {
            // Deferred battle start: camera plays intro cinematic, then timeline event triggers battle
            val pending = PendingStadiumBattle(
                battleFormat = battleFormat,
                side1 = BattleSide(actorA),
                side2 = BattleSide(actorB),
                viewers = viewers,
                config = config,
                snapshots = snapshots,
                entityA = entityA,
                entityB = entityB
            )
            return executeDeferredBattle(context, pending, stadiumId, stadiumFormat, origin)
        } else {
            // Immediate battle start: no camera or camera disabled
            return executeImmediateBattle(
                context, config, stadiumId, stadiumFormat, battleFormat,
                actorA, actorB, viewers, snapshots, entityA, entityB
            )
        }
    }

    /**
     * Deferred battle start path: starts the camera timeline first, then the battle
     * is launched by a `q.launch_battle()` MoLang event within the timeline.
     */
    private fun executeDeferredBattle(
        context: CommandContext<CommandSourceStack>,
        pending: PendingStadiumBattle,
        stadiumId: String,
        stadiumFormat: StadiumBattleFormat,
        origin: Vec3
    ): Int {
        val config = pending.config
        val viewers = pending.viewers
        val cameraOrigin = toWorld(origin, config.cameraOriginOffset)
        val timelineId = config.cameraTimeline!!.path.toString()

        // Send intro music immediately (part of the cinematic)
        sendIntroMusic(config, viewers)

        // Prepare GUI args for the battle intro overlay
        val playerName = pending.entityA.name.string
        val opponentName = pending.entityB.name.string
        val playerPartyCount = pending.side1.actors.firstOrNull()?.pokemonList?.size ?: 0
        val opponentPartyCount = pending.side2.actors.firstOrNull()?.pokemonList?.size ?: 0
        val guiArgs = mapOf(
            "player_name" to playerName,
            "opponent_name" to opponentName,
            "player_party_count" to playerPartyCount.toString(),
            "opponent_party_count" to opponentPartyCount.toString()
        )
        val battleIntroGuiId = ResourceLocation.parse("cobblemon:battle_intro")

        // Track GUI sessions for cleanup
        val guiSessions = mutableListOf<UUID>()

        // Create the launch_battle query function (idempotent via AtomicBoolean)
        val launched = AtomicBoolean(false)
        var cameraId: UUID? = null
        var cameraBController: CameraController? = null

        val queryFunctions = mutableMapOf<String, Function<MoParams, Any>>()

        // GUI control query functions for the battle intro overlay
        queryFunctions["open_battle_intro"] = Function { _ ->
            viewers.forEach { viewer ->
                val session = MolangUiSessionManager.openGui(viewer, battleIntroGuiId, guiArgs)
                session?.let { guiSessions.add(it.sessionId) }
            }
            DoubleValue(1.0)
        }

        queryFunctions["close_battle_intro"] = Function { _ ->
            guiSessions.forEach { sessionId -> MolangUiSessionManager.closeGui(sessionId) }
            guiSessions.clear()
            DoubleValue(1.0)
        }

        // Split-screen query functions
        queryFunctions["split_screen_enable"] = Function { _ ->
            val camAId = cameraId
            val camB = cameraBController
            if (camAId != null && camB != null) {
                val packet = SplitScreenPacket(
                    enabled = true,
                    primaryCameraId = camAId,
                    secondaryCameraId = camB.id,
                    splitAngleDegrees = 30f,
                    gapWidth = 2f
                )
                viewers.forEach { viewer -> viewer.sendPacket(packet) }
            }
            DoubleValue(1.0)
        }

        queryFunctions["split_screen_disable"] = Function { _ ->
            val disablePacket = SplitScreenPacket(enabled = false)
            viewers.forEach { viewer -> viewer.sendPacket(disablePacket) }
            // Stop Camera B
            cameraId?.let { StadiumBattleCameraCoordinator.stopSecondaryCamera(it) }
            cameraBController = null
            DoubleValue(1.0)
        }

        queryFunctions["launch_battle"] = Function { _ ->
            if (launched.compareAndSet(false, true)) {
                val camId = cameraId
                val startResult = BattleRegistry.startBattle(
                    battleFormat = pending.battleFormat,
                    side1 = pending.side1,
                    side2 = pending.side2
                )
                when (startResult) {
                    is SuccessfulBattleStart -> {
                        val battle = startResult.battle
                        val camera = camId?.let { CameraManager.getCamera(it) }

                        // Close battle intro GUI overlay
                        guiSessions.forEach { sessionId -> MolangUiSessionManager.closeGui(sessionId) }
                        guiSessions.clear()

                        // Bind coordinator to the actual battle
                        if (camId != null) {
                            StadiumBattleCameraCoordinator.bindBattle(battle, camId)
                        }

                        // Inject battle query functions into camera runtime
                        camera?.addQueryFunctions(battle.runtime.environment.query.functions.toMap())

                        // Set transition variable so timeline moves from intro to battle state
                        camera?.setVariable("battle_launched", 1.0)

                        // Setup loop music and battle end cleanup
                        setupBattleEndHandlers(battle, config, viewers, camId)

                        Cobblemon.LOGGER.info("[StadiumBattle] Deferred battle launched for stadium '$stadiumId'")
                    }
                    is ErroredBattleStart -> {
                        val camera = camId?.let { CameraManager.getCamera(it) }
                        camera?.setVariable("battle_launch_failed", 1.0)
                        restoreSnapshots(pending.snapshots)
                        Cobblemon.LOGGER.error("[StadiumBattle] Deferred battle start failed for stadium '$stadiumId'")
                    }
                    else -> {
                        val camera = camId?.let { CameraManager.getCamera(it) }
                        camera?.setVariable("battle_launch_failed", 1.0)
                        restoreSnapshots(pending.snapshots)
                    }
                }
            }
            DoubleValue(1.0)
        }

        // Start camera timeline with the launch_battle function available
        cameraId = CameraManager.startCamera(
            timelineId = timelineId,
            context = mapOf(
                "arena_center_x" to DoubleValue(origin.x),
                "arena_center_y" to DoubleValue(origin.y),
                "arena_center_z" to DoubleValue(origin.z)
            ),
            viewers = viewers,
            origin = cameraOrigin,
            queryFunctions = queryFunctions
        )

        if (cameraId == null) {
            // Camera failed to start - fall back to immediate battle start
            Cobblemon.LOGGER.warn("[StadiumBattle] Camera failed to start, falling back to immediate battle start")
            return executeImmediateBattleStart(context, pending, stadiumId, stadiumFormat, origin)
        }

        // Bind coordinator to camera only (no battle yet)
        StadiumBattleCameraCoordinator.bindCamera(cameraId)

        // Start Camera B (secondary) for split-screen if configured
        val sideBTimeline = config.cameraTimelineSideB
        if (sideBTimeline != null) {
            val sideBTimelineId = sideBTimeline.path.toString()
            val cameraB = CameraController(
                context = mapOf(
                    "arena_center_x" to DoubleValue(origin.x),
                    "arena_center_y" to DoubleValue(origin.y),
                    "arena_center_z" to DoubleValue(origin.z)
                ),
                origin = cameraOrigin
            )
            if (cameraB.startTimeline(sideBTimelineId)) {
                viewers.forEach { viewer -> cameraB.addFeedViewer(viewer) }
                cameraBController = cameraB
                StadiumBattleCameraCoordinator.registerSecondaryCamera(cameraId, cameraB)
                Cobblemon.LOGGER.info("[StadiumBattle] Started Camera B with timeline '$sideBTimelineId'")
            } else {
                Cobblemon.LOGGER.warn("[StadiumBattle] Failed to start Camera B timeline '$sideBTimelineId'")
            }
        }

        context.source.sendSuccess(
            { Component.literal("Started stadium battle '$stadiumId' (${stadiumFormat.name.lowercase()}) with intro cinematic.") },
            false
        )
        return Command.SINGLE_SUCCESS
    }

    /**
     * Immediate battle start path: starts the battle right away, optionally with camera.
     * Used when `playCamera=false` or when no camera timeline is configured.
     */
    private fun executeImmediateBattle(
        context: CommandContext<CommandSourceStack>,
        config: StadiumBattleConfig,
        stadiumId: String,
        stadiumFormat: StadiumBattleFormat,
        battleFormat: BattleFormat,
        actorA: BattleActor,
        actorB: BattleActor,
        viewers: List<ServerPlayer>,
        snapshots: List<EntitySnapshot>,
        entityA: LivingEntity,
        entityB: LivingEntity
    ): Int {
        val startResult = BattleRegistry.startBattle(
            battleFormat = battleFormat,
            side1 = BattleSide(actorA),
            side2 = BattleSide(actorB)
        )

        return when (startResult) {
            is SuccessfulBattleStart -> {
                val battle = startResult.battle

                // Handle stadium music
                sendIntroMusic(config, viewers)
                setupBattleEndHandlers(battle, config, viewers, null)

                context.source.sendSuccess(
                    { Component.literal("Started stadium battle '$stadiumId' (${stadiumFormat.name.lowercase()}).") },
                    false
                )
                Command.SINGLE_SUCCESS
            }
            is ErroredBattleStart -> {
                val notifyEntities = mutableListOf(entityA, entityB)
                context.source.entity?.let { notifyEntities.add(it as LivingEntity) }
                startResult.sendTo(notifyEntities)
                restoreSnapshots(snapshots)
                0
            }
            else -> {
                restoreSnapshots(snapshots)
                0
            }
        }
    }

    /**
     * Fallback for when deferred camera fails to start - starts battle immediately.
     */
    private fun executeImmediateBattleStart(
        context: CommandContext<CommandSourceStack>,
        pending: PendingStadiumBattle,
        stadiumId: String,
        stadiumFormat: StadiumBattleFormat,
        origin: Vec3
    ): Int {
        val startResult = BattleRegistry.startBattle(
            battleFormat = pending.battleFormat,
            side1 = pending.side1,
            side2 = pending.side2
        )

        return when (startResult) {
            is SuccessfulBattleStart -> {
                setupBattleEndHandlers(startResult.battle, pending.config, pending.viewers, null)
                context.source.sendSuccess(
                    { Component.literal("Started stadium battle '$stadiumId' (${stadiumFormat.name.lowercase()}).") },
                    false
                )
                Command.SINGLE_SUCCESS
            }
            is ErroredBattleStart -> {
                val notifyEntities = mutableListOf(pending.entityA, pending.entityB)
                context.source.entity?.let { notifyEntities.add(it as LivingEntity) }
                startResult.sendTo(notifyEntities)
                restoreSnapshots(pending.snapshots)
                0
            }
            else -> {
                restoreSnapshots(pending.snapshots)
                0
            }
        }
    }

    /**
     * Sends intro music to viewers. If there's intro music configured, sends it with
     * loop music info so the client auto-switches. Otherwise sends loop music directly.
     */
    private fun sendIntroMusic(config: StadiumBattleConfig, viewers: List<ServerPlayer>) {
        val hasIntroMusic = config.introMusic != null
        val hasLoopMusic = config.music != null

        if ((hasIntroMusic || hasLoopMusic) && viewers.isNotEmpty()) {
            val musicPacket = if (hasIntroMusic) {
                BattleMusicPacket(
                    music = config.introMusic,
                    volume = config.introMusicVolume,
                    pitch = config.introMusicPitch,
                    restartExisting = true,
                    loopMusic = config.music,
                    loopVolume = config.musicVolume,
                    loopPitch = config.musicPitch
                )
            } else {
                BattleMusicPacket(
                    music = config.music,
                    volume = config.musicVolume,
                    pitch = config.musicPitch,
                    restartExisting = true
                )
            }
            viewers.forEach { viewer -> viewer.sendPacket(musicPacket) }
        }
    }

    /**
     * Sets up battle end handlers for music stop, camera cleanup, and coordinator release.
     */
    private fun setupBattleEndHandlers(
        battle: PokemonBattle,
        config: StadiumBattleConfig,
        viewers: List<ServerPlayer>,
        cameraId: UUID?
    ) {
        // Stop music when battle ends
        if (config.introMusic != null || config.music != null) {
            battle.onEndHandlers += {
                val stopMusicPacket = BattleMusicPacket(music = null)
                viewers.filter { it.isAlive }.forEach { viewer -> viewer.sendPacket(stopMusicPacket) }
            }
        }

        // Camera cleanup when battle ends
        if (cameraId != null) {
            battle.onEndHandlers += {
                // Disable split-screen if still active
                val disablePacket = SplitScreenPacket(enabled = false)
                viewers.filter { it.isAlive }.forEach { viewer -> viewer.sendPacket(disablePacket) }

                StadiumBattleCameraCoordinator.release(battle.battleId)
                viewers.forEach { CameraManager.stopCameraForPlayer(it) }
                CameraManager.stopCamera(cameraId)
            }
        }
    }

    private fun resolveLevel(server: net.minecraft.server.MinecraftServer, dimensionId: ResourceLocation?, fallback: ServerLevel): ServerLevel? {
        if (dimensionId == null) {
            return fallback
        }
        val key = ResourceKey.create(Registries.DIMENSION, dimensionId)
        return server.getLevel(key)
    }

    private fun collectPlayers(entity: LivingEntity): List<ServerPlayer> = when (entity) {
        is ServerPlayer -> listOf(entity)
        else -> emptyList()
    }

    private fun createActor(
        entity: LivingEntity,
        pokemonPlacements: List<StadiumPlacement>,
        origin: Vec3,
        battleFormat: BattleFormat,
        opponentPlayers: List<ServerPlayer>,
        context: CommandContext<CommandSourceStack>,
        label: String
    ): BattleActor? {
        val requiredSlots = battleFormat.battleType.slotsPerActor
        val sendOutPositions = pokemonPlacements.map { toWorld(origin, it.position) }

        if (sendOutPositions.size < requiredSlots) {
            context.source.sendFailure(Component.literal("$label does not define enough Pokémon placements (needs $requiredSlots)."))
            return null
        }

        val actor = when (entity) {
            is ServerPlayer -> {
                if (BattleRegistry.getBattleByParticipatingPlayer(entity) != null) {
                    context.source.sendFailure(Component.literal("${entity.name.string} is already in a battle."))
                    return null
                }
                val team = entity.party().toBattleTeam().sortedBy { it.health <= 0 }
                val available = team.count { it.health > 0 }
                if (available < requiredSlots) {
                    context.source.sendFailure(
                        Component.literal("${entity.name.string} does not have enough healthy Pokémon ($available/$requiredSlots).")
                    )
                    return null
                }
                PlayerBattleActor(entity.uuid, team)
            }
            is NPCEntity -> {
                val party: PartyStore = entity.getPartyForChallenge(opponentPlayers)
                    ?: run {
                        context.source.sendFailure(Component.literal("${entity.displayName!!.string} has no battle party available."))
                        return null
                    }
                val team = party.toBattleTeam(healPokemon = entity.npc.autoHealParty).sortedBy { it.health <= 0 }
                val available = team.count { it.health > 0 }
                if (available < requiredSlots) {
                    context.source.sendFailure(
                        Component.literal("${entity.displayName!!.string} does not have enough healthy Pokémon ($available/$requiredSlots).")
                    )
                    return null
                }
                NPCBattleActor(entity, team, entity.skill ?: entity.npc.skill)
            }
            else -> {
                context.source.sendFailure(Component.literal("Unsupported participant type for stadium battles."))
                return null
            }
        }

        actor.stadiumSendOutPositions = sendOutPositions.take(requiredSlots)
        return actor
    }

    private fun teleportParticipant(
        entity: LivingEntity,
        level: ServerLevel,
        origin: Vec3,
        placement: StadiumPlacement,
        fallbackLookAt: Vec3?
    ) {
        val targetPosition = toWorld(origin, placement.position)
        val lookAt = placement.lookAt?.let { toWorld(origin, it) } ?: fallbackLookAt
        val yaw = placement.yaw ?: lookAt?.let { computeYaw(targetPosition, it) } ?: entity.yRot
        val pitch = placement.pitch ?: lookAt?.let { computePitch(targetPosition, it) } ?: entity.xRot

        entity.fallDistance = 0f
        entity.deltaMovement = Vec3.ZERO

        if (entity is ServerPlayer) {
            entity.teleportTo(level, targetPosition.x, targetPosition.y, targetPosition.z, yaw, pitch)
        } else {
            entity.teleportTo(level, targetPosition.x, targetPosition.y, targetPosition.z, RelativeMovement.ALL, yaw, pitch)
        }

        entity.yRot = yaw % 360.0F
        entity.xRot = pitch % 360.0F
        entity.yHeadRot = yaw
        entity.yHeadRotO = yaw
        entity.yBodyRot = yaw
        entity.yBodyRotO = yaw
    }

    private fun snapshot(entity: LivingEntity): EntitySnapshot {
        val level = entity.level() as ServerLevel
        return EntitySnapshot(
            entity = entity,
            level = level,
            position = entity.position(),
            yaw = entity.yRot,
            pitch = entity.xRot
        )
    }

    private fun restoreSnapshots(snapshots: List<EntitySnapshot>) {
        snapshots.forEach { (entity, level, position, yaw, pitch) ->
            if (!entity.isAlive) return@forEach
            if (entity is ServerPlayer) {
                entity.teleportTo(level, position.x, position.y, position.z, yaw, pitch)
            } else {
                entity.teleportTo(level, position.x, position.y, position.z, RelativeMovement.ALL, yaw, pitch)
            }
            entity.yRot = yaw % 360.0F
            entity.xRot = pitch % 360.0F
            entity.yHeadRot = yaw
            entity.yHeadRotO = yaw
            entity.yBodyRot = yaw
            entity.yBodyRotO = yaw
        }
    }

    private fun toWorld(origin: Vec3, offset: Vec3): Vec3 = origin.add(offset)

    private fun average(vectors: List<Vec3>): Vec3? {
        if (vectors.isEmpty()) return null
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        vectors.forEach {
            sumX += it.x
            sumY += it.y
            sumZ += it.z
        }
        val size = vectors.size.toDouble()
        return Vec3(sumX / size, sumY / size, sumZ / size)
    }

    private fun computeYaw(from: Vec3, to: Vec3): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val angle = Mth.atan2(-dx, dz)
        return (angle * (180f / Math.PI)).toFloat()
    }

    private fun computePitch(from: Vec3, to: Vec3): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val horizontal = Mth.sqrt((dx * dx + dz * dz).toFloat()).toDouble()
        val angle = Mth.atan2(-dy, horizontal)
        return (angle * (180f / Math.PI)).toFloat()
    }

    
    /**
     * Reads the raw JSON for a timeline from the server's resource manager.
     * This is needed to send timeline data to dedicated clients for debug visualization.
     */
    private fun readTimelineJson(resourceManager: net.minecraft.server.packs.resources.ResourceManager, timelineId: String): String? {
        val resources = resourceManager.listResources("camera_timelines") { path ->
            path.path.endsWith(".json")
        }
        
        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream)
                    val content = reader.readText()
                    
                    if (content.contains("\"$timelineId\"") || content.contains("\"timelineId\": \"$timelineId\"")) {
                        val json = com.google.gson.JsonParser.parseString(content).asJsonObject
                        val id = json.get("timelineId")?.asString
                        if (id == timelineId) {
                            return content
                        }
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("[StadiumBattle] Error reading timeline from $identifier: ${e.message}")
            }
        }
        
        return null
    }
}

