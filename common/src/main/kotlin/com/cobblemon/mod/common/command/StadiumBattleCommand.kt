/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.stadium.StadiumBattleCameraCoordinator
import com.cobblemon.mod.common.battles.stadium.StadiumBattleConfig
import com.cobblemon.mod.common.battles.stadium.StadiumBattleFormat
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumPlacement
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.api.camera.CameraManager
import com.cobblemon.mod.common.util.permission
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

        val startResult = BattleRegistry.startBattle(
            battleFormat = battleFormat,
            side1 = BattleSide(actorA),
            side2 = BattleSide(actorB)
        )

        return when (startResult) {
            is SuccessfulBattleStart -> {
                val battle = startResult.battle
                val playCamera = (cameraOverride ?: (config.cameraTimeline != null))
                if (playCamera && config.cameraTimeline != null) {
                    val viewers = buildList {
                        if (entityA is ServerPlayer) add(entityA)
                        if (entityB is ServerPlayer) add(entityB)
                    }
                    if (viewers.isNotEmpty()) {
                        val cameraOrigin = toWorld(origin, config.cameraOriginOffset)
                        val cameraId = CameraManager.startCamera(
                            timelineId = config.cameraTimeline.path.toString(),
                            context = emptyMap(),
                            viewers = viewers,
                            origin = cameraOrigin,
                            queryFunctions = battle.runtime.environment.query.functions.toMap()
                        )
                        cameraId?.let { StadiumBattleCameraCoordinator.bind(battle, it) }
                        battle.onEndHandlers += {
                            StadiumBattleCameraCoordinator.release(battle.battleId)
                            viewers.forEach { CameraManager.stopCameraForPlayer(it) }
                        }
                    }
                }
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

    private data class EntitySnapshot(
        val entity: LivingEntity,
        val level: ServerLevel,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float
    )
}

