/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.command

import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.battles.stadium.StadiumSpectating
import com.cobblemon.mod.common.battles.stadium.StadiumCameraDrones

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.stadium.EntitySnapshot
import com.cobblemon.mod.common.battles.stadium.StadiumBattleConfig
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.battles.stadium.StadiumTeamRules
import com.cobblemon.mod.common.battles.stadium.StadiumBattleFormat
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumPlacement
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.permission
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import java.util.UUID
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.phys.Vec3

object StadiumBattleCommand {
    private val pendingParticipants = mutableSetOf<UUID>()
    private const val ROOT = "stadium"
    private const val NODE_BATTLE = "battle"
    private const val ARG_STADIUM = "stadiumId"
    private const val ARG_FORMAT = "format"
    private const val ARG_SIDE_ONE = "entityA"
    private const val ARG_SIDE_TWO = "entityB"

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
                                            SharedSuggestionProvider.suggest(listOf("active", "single", "double") + StadiumBattleRegistry.all().flatMap { it.formats.keys }, builder)
                                        }
                                        .then(
                                            Commands.argument(ARG_SIDE_ONE, EntityArgument.entity())
                                                .then(
                                                    Commands.argument(ARG_SIDE_TWO, EntityArgument.entity())
                                                        .executes { execute(it) }
                                                )
                                        )
                                )
                        )
                )
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>): Int {
        val stadiumId = StringArgumentType.getString(context, ARG_STADIUM)
        val formatId = StringArgumentType.getString(context, ARG_FORMAT)

        val entityA = EntityArgument.getEntity(context, ARG_SIDE_ONE)
        val entityB = EntityArgument.getEntity(context, ARG_SIDE_TWO)

        if (entityA !is LivingEntity || entityB !is LivingEntity) {
            context.source.sendFailure(lang("command.stadium.living_participants"))
            return 0
        }

        val config = StadiumBattleRegistry.get(stadiumId) ?: run {
            context.source.sendFailure(lang("command.stadium.unknown_stadium", stadiumId))
            return 0
        }
        return start(context.source, config, formatId, entityA, entityB)
    }

    fun start(source: CommandSourceStack, config: StadiumBattleConfig, formatId: String, entityA: LivingEntity, entityB: LivingEntity): Int {
        if (StadiumSpectating.isRunning(config.id, config.dimension ?: source.level.dimension().location())) {
            source.sendFailure(lang("command.stadium.match_running"))
            return 0
        }
        val stadiumId = config.id
        val selectedId = if (formatId == "active") config.activeFormat else formatId
        val rules = config.formats[selectedId]?.let(StadiumConfiguration::copy)
        if (config.formats.isNotEmpty() && rules == null) {
            source.sendFailure(lang("command.stadium.configured_format", config.formats.keys.joinToString()))
            return 0
        }
        if (rules != null && (StadiumConfiguration.formatErrors(rules) + StadiumConfiguration.engineRequirements(rules)).isNotEmpty()) {
            source.sendFailure(lang("command.stadium.invalid_format"))
            return 0
        }
        val stadiumFormat = StadiumBattleFormat.fromId(rules?.battleTypeName ?: formatId) ?: run {
            source.sendFailure(lang("command.stadium.unknown_format", formatId))
            return 0
        }
        val layout = config.layoutFor(stadiumFormat) ?: run {
            source.sendFailure(lang("command.stadium.missing_layout", stadiumId, formatId))
            return 0
        }

        val server = source.server
        val fallbackLevel = source.level
        val targetLevel = resolveLevel(server, config.dimension, fallbackLevel) ?: run {
            source.sendFailure(lang("command.stadium.dimension", stadiumId))
            return 0
        }

        val battleFormat = when (stadiumFormat) {
            StadiumBattleFormat.SINGLES -> BattleFormat.GEN_9_SINGLES.copy()
            StadiumBattleFormat.DOUBLES -> BattleFormat.GEN_9_DOUBLES.copy()
        }

        val origin = config.origin
        val side1Placement = layout.side1.actors.firstOrNull()
        val side2Placement = layout.side2.actors.firstOrNull()
        if (side1Placement == null || side2Placement == null) {
            source.sendFailure(lang("command.stadium.actor_placements"))
            return 0
        }
        val placements = layout.side1.actors + layout.side2.actors + layout.side1.pokemon + layout.side2.pokemon
        if (!finite(origin.add(config.cameraOriginOffset)) || placements.any {
                !finite(toWorld(origin, it.position)) || it.lookAt?.let { target -> !finite(toWorld(origin, target)) } == true ||
                    it.yaw?.isFinite() == false || it.pitch?.isFinite() == false
            } || listOf(entityA, entityB).any { it !is ServerPlayer && it.level() !== targetLevel }) {
            source.sendFailure(lang("command.stadium.place_failed"))
            return 0
        }

        val sceneDefinition = StadiumCinematic.resolve(config.cinematicScene, config.cinematicSceneJson)
            ?.let { StadiumCinematic.fitArena(it, config.arena) }
        if (config.cinematicScene != null && (sceneDefinition == null || sceneDefinition.validate().any { it.severity == ValidationError.Severity.ERROR } ||
            !StadiumCinematic.hasReachableLaunchCue(sceneDefinition))) {
            source.sendFailure(lang("command.stadium.launch_cue"))
            return 0
        }
        if (entityA.uuid == entityB.uuid || listOf(entityA, entityB).any { it.uuid in pendingParticipants || isInBattle(it) || StadiumSpectating.isWatching(it.uuid) }) {
            source.sendFailure(lang("command.stadium.participants_busy"))
            return 0
        }

        val snapshots = listOf(entityA, entityB).map { snapshot(it) }

        val side1Positions = layout.side1.actors.map { toWorld(origin, it.position) }
        val side2Positions = layout.side2.actors.map { toWorld(origin, it.position) }

        val opponentPlayersForA = collectPlayers(entityB)
        val opponentPlayersForB = collectPlayers(entityA)

        val actorA = createActor(entityA, layout.side1.pokemon, origin, battleFormat, opponentPlayersForA, source, rules, 1)
            ?: return 0
        val actorB = createActor(entityB, layout.side2.pokemon, origin, battleFormat, opponentPlayersForB, source, rules, 2)
            ?: return 0

        val engineRules = rules ?: StadiumConfiguration.template(stadiumFormat == StadiumBattleFormat.DOUBLES)
        val validation = BattleRegistry.validateStadium(engineRules, BattleSide(actorA), BattleSide(actorB))
        if (!validation.ok) {
            source.sendFailure(validation.userMessage)
            return 0
        }

        try {
            teleportParticipant(entityA, targetLevel, origin, side1Placement, average(side2Positions))
            teleportParticipant(entityB, targetLevel, origin, side2Placement, average(side1Positions))
        } catch (exception: Exception) {
            restoreSnapshots(snapshots)
            source.sendFailure(lang("command.stadium.place_failed"))
            return 0
        }

        val viewers = buildList {
            if (entityA is ServerPlayer) add(entityA)
            if (entityB is ServerPlayer) add(entityB)
        }

        if (sceneDefinition == null) {
            val result = BattleRegistry.startStadiumBattle(battleFormat, BattleSide(actorA), BattleSide(actorB), engineRules) {
                restoreSnapshots(snapshots)
                source.sendFailure(lang("command.stadium.engine_stopped"))
            }
            if (result !is SuccessfulBattleStart) {
                restoreSnapshots(snapshots)
                source.sendFailure((result as? ErroredBattleStart)?.errors?.firstOrNull()?.getMessageFor(entityA)
                    ?: lang("command.stadium.start_failed"))
                return 0
            }
            val spectateId = StadiumSpectating.start(config,
                targetLevel.dimension().location(), setOf(entityA.uuid, entityB.uuid))
            StadiumSpectating.attachBattle(spectateId, result.battle)
            Cobblemon.LOGGER.info("Stadium battle {} started directly from {}", result.battle.battleId, config.id)
            source.sendSuccess({ lang("command.stadium.started", config.id) }, false)
            return Command.SINGLE_SUCCESS
        }

        val sceneId = runCatching { CinematicManager.startScene(
            scene = sceneDefinition,
            origin = origin.add(config.cameraOriginOffset),
            initialViewers = viewers.map { it.uuid }.toSet(),
            actorBindings = mapOf("user" to ActorBinding.Real(entityA), "target" to ActorBinding.Real(entityB),
                "trainer_1" to ActorBinding.Real(entityA), "trainer_2" to ActorBinding.Real(entityB)),
            parameterOverrides = mapOf(
                "arena_center" to (config.arena?.center ?: entityA.position().add(entityB.position()).scale(0.5)),
                "player_name" to entityA.name.string,
                "opponent_name" to entityB.name.string,
                "player_party_count" to actorA.pokemonList.size,
                "opponent_party_count" to actorB.pokemonList.size
            )
        ) }.getOrNull()
        val scene = sceneId?.let(CinematicManager::getScene)
        if (scene == null) {
            source.sendFailure(lang("command.stadium.cinematic_failed", sceneDefinition.id))
            restoreSnapshots(snapshots)
            return 0
        }

        val participants = listOf(entityA, entityB)
        pendingParticipants.addAll(participants.map { it.uuid })
        var launched = false
        var attempted = false
        scene.onStop {
            pendingParticipants.removeAll(participants.map { it.uuid }.toSet())
            if (!launched) restoreSnapshots(snapshots)
        }
        try {
            StadiumCameraDrones.attach(scene, targetLevel)
            val spectateId = StadiumSpectating.start(config,
                targetLevel.dimension().location(), participants.map { it.uuid }.toSet(), scene.id)
            scene.onCue { cue ->
                if (cue.id != "battle_launch" || attempted) return@onCue
                attempted = true
                if (participants.any { !it.isAlive || it.isRemoved || isInBattle(it) || it.level() !== targetLevel ||
                        (it is ServerPlayer && server.playerList.getPlayer(it.uuid) !== it) }) {
                    scene.stop()
                    return@onCue
                }
                if (rules != null && listOf(actorA, actorB).any {
                        it.pokemonList.count { pokemon -> pokemon.health > 0 } < battleFormat.battleType.slotsPerActor ||
                            StadiumTeamRules.errors(rules, StadiumTeamRules.entries(it.pokemonList)).isNotEmpty()
                    }) {
                    source.sendFailure(lang("command.stadium.party_changed"))
                    scene.stop()
                    return@onCue
                }
                val startResult = BattleRegistry.startStadiumBattle(
                    battleFormat = battleFormat,
                    side1 = BattleSide(actorA),
                    side2 = BattleSide(actorB),
                    config = engineRules
                ) {
                    scene.stop()
                    restoreSnapshots(snapshots)
                    source.sendFailure(lang("command.stadium.cinematic_stopped"))
                }
                if (startResult is SuccessfulBattleStart) {
                    launched = true
                    pendingParticipants.removeAll(participants.map { it.uuid }.toSet())
                    val battle = startResult.battle
                    battle.onEndHandlers += {
                        CinematicManager.unregisterSceneForBattle(battle.battleId)
                        CinematicManager.stopScene(scene.id)
                    }
                    try {
                        scene.setVariable("battle_launched", 1.0)
                        CinematicManager.registerSceneForBattle(battle.battleId, scene.id)
                        StadiumSpectating.attachBattle(spectateId, battle)
                    } catch (exception: Exception) {
                        Cobblemon.LOGGER.error("Could not attach stadium battle {} to its cinematic", battle.battleId, exception)
                        BattleRegistry.abortStadiumBattle(battle.battleId)
                        return@onCue
                    }
                    Cobblemon.LOGGER.info("Stadium battle {} launched by scene {} cue '{}'", battle.battleId, scene.id, cue.id)
                } else {
                    source.sendFailure((startResult as? ErroredBattleStart)?.errors?.firstOrNull()?.getMessageFor(entityA)
                        ?: lang("command.stadium.launch_failed"))
                    scene.stop()
                }
            }
            viewers.forEach(scene::addViewerPlayer)
        } catch (exception: Exception) {
            Cobblemon.LOGGER.error("Could not initialize stadium cinematic {}", scene.id, exception)
            scene.stop()
            source.sendFailure(lang("command.stadium.cinematic_failed", sceneDefinition.id))
            return 0
        }

        source.sendSuccess({
            lang("command.stadium.cinematic_started", stadiumId)
        }, false)
        return Command.SINGLE_SUCCESS
    }


    private fun resolveLevel(server: MinecraftServer, dimensionId: ResourceLocation?, fallback: ServerLevel): ServerLevel? {
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

    private fun isInBattle(entity: LivingEntity): Boolean = when (entity) {
        is ServerPlayer -> BattleRegistry.getBattleByParticipatingPlayer(entity) != null
        is NPCEntity -> entity.isInBattle()
        else -> false
    }

    private fun createActor(
        entity: LivingEntity,
        pokemonPlacements: List<StadiumPlacement>,
        origin: Vec3,
        battleFormat: BattleFormat,
        opponentPlayers: List<ServerPlayer>,
        source: CommandSourceStack,
        rules: StadiumFormatConfig?,
        side: Int
    ): BattleActor? {
        val requiredSlots = battleFormat.battleType.slotsPerActor
        val sendOutPositions = pokemonPlacements.map { toWorld(origin, it.position) }

        if (sendOutPositions.size < requiredSlots) {
            source.sendFailure(lang("command.stadium.pokemon_placements", side, requiredSlots))
            return null
        }

        val actor = when (entity) {
            is ServerPlayer -> {
                if (BattleRegistry.getBattleByParticipatingPlayer(entity) != null) {
                    source.sendFailure(lang("command.stadium.already_battling", entity.name))
                    return null
                }
                val team = entity.party().toBattleTeam().sortedBy { it.health <= 0 }
                val available = team.count { it.health > 0 }
                if (available < requiredSlots) {
                    source.sendFailure(
                        lang("command.stadium.healthy_pokemon", entity.name, available, requiredSlots)
                    )
                    return null
                }
                PlayerBattleActor(entity.uuid, team)
            }
            is NPCEntity -> {
                val party: PartyStore = entity.getPartyForChallenge(opponentPlayers)
                    ?: run {
                        source.sendFailure(lang("command.stadium.no_party", entity.displayName ?: entity.name))
                        return null
                    }
                val team = party.toBattleTeam(healPokemon = entity.npc.autoHealParty).sortedBy { it.health <= 0 }
                val available = team.count { it.health > 0 }
                if (available < requiredSlots) {
                    source.sendFailure(
                        lang("command.stadium.healthy_pokemon", entity.displayName ?: entity.name, available, requiredSlots)
                    )
                    return null
                }
                NPCBattleActor(entity, team, entity.skill ?: entity.npc.skill)
            }
            else -> {
                source.sendFailure(lang("command.stadium.unsupported_participant"))
                return null
            }
        }

        actor.pokemonList.sortBy { it.health <= 0 }
        if (rules != null) {
            val errors = StadiumTeamRules.errors(rules, StadiumTeamRules.entries(actor.pokemonList))
            if (errors.isNotEmpty()) {
                source.sendFailure(lang("command.stadium.team_error", side, errors.first()))
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
        require(finite(targetPosition) && yaw.isFinite() && pitch.isFinite())

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
            try {
                if (!entity.isAlive || entity.isRemoved || isInBattle(entity)) return@forEach
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
            } catch (exception: Exception) {
                Cobblemon.LOGGER.error("Could not restore stadium participant {}", entity.uuid, exception)
            }
        }
    }

    private fun finite(position: Vec3) = position.x.isFinite() && position.y.isFinite() && position.z.isFinite()

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


}
