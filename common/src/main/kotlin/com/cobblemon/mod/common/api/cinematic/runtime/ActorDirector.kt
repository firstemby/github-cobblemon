/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.network.chat.Component
import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.StartPose
import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AnimationTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AttachTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.FollowActor
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.LookAtTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.PoseLockTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointActorMotion
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.mixin.accessor.ChunkMapAccessor
import com.cobblemon.mod.common.mixin.accessor.TrackedEntityAccessor
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.util.server
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB

class ActorDirector(
    val scene: Scene,
    bindings: Map<String, ActorBinding>,
    val networkSync: SceneNetworkSync? = null,
    serverLevel: ServerLevel? = null,
    val sceneOrigin: Vec3 = Vec3.ZERO,
    val sceneId: UUID = UUID(0, 0),
    val previewMode: Boolean = false,
    private val runtimeProvider: () -> MoLangRuntime = { MoLangRuntime() }
) {
    private val _bindings: MutableMap<String, ActorBinding> = bindings.toMutableMap()
    val bindings: Map<String, ActorBinding> get() = _bindings
    private data class CameraAnchor(val position: Vec3, val bounds: AABB)
    private val cameraAnchors = mutableMapOf<UUID, CameraAnchor>()

    var serverLevel: ServerLevel? = serverLevel
        private set

    fun setServerLevel(level: ServerLevel) {
        if (serverLevel == null) serverLevel = level
    }

    fun updateBinding(actorId: String, newBinding: ActorBinding) {
        activeBlends.remove(actorId)
        startPoseErrors.remove(actorId)
        waypointMotions.remove(actorId)?.restore()
        previewPoseResets.remove(actorId)
        val oldBinding = _bindings[actorId]
        _bindings[actorId] = newBinding

        if (oldBinding is ActorBinding.SynthPokemon || oldBinding is ActorBinding.SynthBedrock) {
            if (previewMode) {
                previewActors.remove(actorId)?.despawn()
            }
            spawnedEntities.remove(actorId)?.let { entity ->
                if (!previewMode && !entity.isRemoved) {
                    entity.remove(Entity.RemovalReason.DISCARDED)
                }
            }
        }
    }

    private val visibleActors: MutableMap<String, Boolean> = mutableMapOf()

    private val firedAnimationCues: MutableMap<String, MutableSet<Pair<Float, String>>> = mutableMapOf()

    private val spawnedEntities: MutableMap<String, Entity> = mutableMapOf()

    private val previewActors: MutableMap<String, PreviewActor> = mutableMapOf()

    private fun currentViewerPlayers(): Collection<ServerPlayer> {
        val sync = networkSync ?: return emptyList()
        val srv = try {
            server()
        } catch (_: UninitializedPropertyAccessException) {
            return emptyList()
        } ?: return emptyList()
        return sync.viewerIdsSnapshot().mapNotNull { srv.playerList.getPlayer(it) }
    }

    internal fun hasTrackedPreviewEntity(actorId: String): Boolean =
        previewMode && previewActors.containsKey(actorId)

    private val activeBlends: MutableMap<String, BlendState> = mutableMapOf()
    private val startPoseErrors = mutableMapOf<String, Component>()
    private val previewPoseResets = mutableMapOf<String, () -> Unit>()
    private val waypointMotions = mutableMapOf<String, WaypointActorMotion>()
    val previewError: Component? get() = startPoseErrors.values.firstOrNull() ?: waypointMotions.entries.firstNotNullOfOrNull { (id, motion) ->
        motion.playback.error?.let { Component.translatable("cobblemon.cinematic_editor.route.actor_error", id, it) }
    }

    private fun releaseWaypointMotions() {
        val motions = waypointMotions.toMap()
        waypointMotions.clear()
        motions.forEach { (actorId, motion) ->
            cleanupActor(actorId) { motion.restore() }
        }
    }

    private inline fun cleanupActor(actorId: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not release cinematic actor '{}'", actorId, error)
        }
    }

    private var evaluationOrder = scene.actors.keys.toList()

    private data class BlendState(
        val startPosition: Vec3,
        val targetPosition: Vec3,
        val startYaw: Float,
        val targetYaw: Float,
        val startPitch: Float,
        val targetPitch: Float,
        val durationSeconds: Float
    )

    init {
        bindings.forEach { (actorId, binding) ->
            val declared = scene.getActor(actorId)
            if (declared != null && declared.kind != binding.kind) {
                Cobblemon.LOGGER.warn(
                    "[ActorDirector] Binding for actor '{}' has kind {} but scene declared {}; proceeding but behavior undefined",
                    actorId, binding.kind::class.simpleName, declared.kind::class.simpleName
                )
            }
        }
    }

    fun isVisible(actorId: String): Boolean = visibleActors[actorId] ?: false

    /** Called once per beat transition before tick evaluation. */
    fun onBeatEnter(beat: Beat) {
        releaseWaypointMotions()
        activeBlends.clear()
        startPoseErrors.clear()
        evaluationOrder = ActorDependencyOrder.resolve(scene.actors.keys, beat)
        firedAnimationCues.clear()

        scene.actors.keys.forEach { actorId ->
            val direction = beat.actorDirections[actorId]
            applyVisibilityAndPose(actorId, direction)
        }
    }

    fun evaluate(beat: Beat, beatTime: Float) {
        val blendIter = activeBlends.entries.iterator()
        while (blendIter.hasNext()) {
            val (actorId, blend) = blendIter.next()
            val t = (beatTime / blend.durationSeconds).coerceIn(0f, 1f)
            val entity = resolveEntity(actorId)
            if (entity == null) {
                blendIter.remove()
                continue
            }
            entity.setPos(
                blend.startPosition.x + (blend.targetPosition.x - blend.startPosition.x) * t,
                blend.startPosition.y + (blend.targetPosition.y - blend.startPosition.y) * t,
                blend.startPosition.z + (blend.targetPosition.z - blend.startPosition.z) * t
            )
            entity.yRot = blend.startYaw + (blend.targetYaw - blend.startYaw) * t
            entity.xRot = blend.startPitch + (blend.targetPitch - blend.startPitch) * t
            if (t >= 1f) blendIter.remove()
        }

        evaluationOrder.forEach { actorId ->
            if (!isVisible(actorId)) return@forEach
            val direction = beat.actorDirections[actorId] ?: return@forEach
            direction.tracks.forEach { track ->
                if (track !is LookAtTrack) applyTrack(actorId, track, beatTime)
            }
            direction.tracks.filterIsInstance<LookAtTrack>().forEach { applyTrack(actorId, it, beatTime) }
        }
        bindings.filterValues { it is ActorBinding.Real || it is ActorBinding.Player }.keys.forEach { cameraAnchor(it) }
    }

    fun dispose() {
        cameraAnchors.clear()
        releaseWaypointMotions()
        previewPoseResets.clear()
        visibleActors.clear()
        firedAnimationCues.clear()
        startPoseErrors.clear()
        activeBlends.clear()

        // Virtual actors never enter the world; removing them through the level lifecycle is inappropriate.
        if (previewMode) {
            val actors = previewActors.toMap()
            previewActors.clear()
            actors.forEach { (actorId, actor) -> cleanupActor(actorId) { actor.despawn() } }
        } else {
            val entities = spawnedEntities.toMap()
            spawnedEntities.clear()
            entities.forEach { (actorId, entity) ->
                cleanupActor(actorId) {
                    if (!entity.isRemoved) entity.remove(Entity.RemovalReason.DISCARDED)
                }
            }
        }
        spawnedEntities.clear()
    }


    private fun applyVisibilityAndPose(actorId: String, direction: ActorDirection?) {
        val wasVisible = visibleActors[actorId] ?: false
        val targetVisibility = direction?.visible ?: wasVisible
        visibleActors[actorId] = targetVisibility
        if (!previewMode) spawnedEntities[actorId]?.let { updateWorldActorScope(actorId, it) }

        if (targetVisibility && direction?.startPose != null) {
            val mode = direction.startPoseMode ?: defaultModeFor(actorId, wasVisible, targetVisibility)
            applyStartPose(actorId, direction.startPose, mode)
        }
    }

    private fun defaultModeFor(actorId: String, wasVisible: Boolean, nowVisible: Boolean): StartPoseMode {
        val binding = bindings[actorId] ?: return StartPoseMode.IGNORE
        return when {
            !wasVisible && nowVisible -> StartPoseMode.TELEPORT
            binding is ActorBinding.Player -> StartPoseMode.EXPECT
            else -> StartPoseMode.IGNORE
        }
    }

    private fun applyStartPose(actorId: String, pose: StartPose, mode: StartPoseMode) {
        if (mode == StartPoseMode.IGNORE) return
        val entity = resolveEntity(actorId) ?: return
        val target = try {
            pose.position.toVec3(context = emptyMap(), runtime = runtimeProvider(), origin = sceneOrigin).also {
                require(it.x.isFinite() && it.y.isFinite() && it.z.isFinite()) { "Start pose position must be finite" }
            }
        } catch (error: Exception) {
            startPoseErrors[actorId] = Component.translatable("cobblemon.cinematic_editor.preview.invalid_start_pose", actorId)
            Cobblemon.LOGGER.warn("Could not evaluate start pose for actor '$actorId'", error)
            return
        }
        if (mode == StartPoseMode.BLEND && pose.blendDurationSeconds == 0f) {
            entity.setPos(target.x, target.y, target.z)
            entity.yRot = pose.rotation.yaw
            entity.xRot = pose.rotation.pitch
            return
        }
        when (mode) {
            StartPoseMode.TELEPORT -> {
                entity.setPos(target.x, target.y, target.z)
                entity.yRot = pose.rotation.yaw
                entity.xRot = pose.rotation.pitch
            }
            StartPoseMode.BLEND -> {
                activeBlends[actorId] = BlendState(
                    startPosition = Vec3(entity.x, entity.y, entity.z),
                    targetPosition = target,
                    startYaw = entity.yRot,
                    targetYaw = pose.rotation.yaw,
                    startPitch = entity.xRot,
                    targetPitch = pose.rotation.pitch,
                    durationSeconds = pose.blendDurationSeconds
                )
            }
            StartPoseMode.EXPECT -> {
                val currentPos = Vec3(entity.x, entity.y, entity.z)
                if (currentPos.distanceTo(target) > 1.5) {
                    Cobblemon.LOGGER.warn(
                        "[ActorDirector] Actor '{}' with EXPECT pose is not near declared start ({} vs {})",
                        actorId, currentPos, target
                    )
                }
            }
            StartPoseMode.IGNORE -> {   }
        }
    }


    private fun applyTrack(actorId: String, track: ActorTrack, beatTime: Float) {
        val entity = resolveEntity(actorId) ?: return
        when (track) {
            is WaypointPathfinding -> {
                val previousError = waypointMotions[actorId]?.playback?.error
                val motion = waypointMotions.getOrPut(actorId) { WaypointActorMotion(track, entity) }
                motion.sample(beatTime)
                if (motion.playback.error != null && previousError == null) {
                    Cobblemon.LOGGER.warn("Pathfinding actor '{}': {}", actorId, motion.playback.error)
                }
            }
            is KeyframeMovement -> {
                val newPos = track.evaluate(beatTime)
                entity.setPos(newPos.x, newPos.y, newPos.z)
            }
            is LookAtTrack -> {
                val currentPos = Vec3(entity.x, entity.y, entity.z)
                entity.yRot = track.computeYRot(currentPos)
                entity.xRot = track.computeXRot(currentPos)
                if (entity is LivingEntity) {
                    entity.yBodyRot = entity.yRot
                    entity.yHeadRot = entity.yRot
                }
            }
            is AnimationTrack -> {
                if (previewMode) return
                val firedSet = firedAnimationCues.getOrPut(actorId) { mutableSetOf() }
                track.cues.forEach { cue ->
                    val key = cue.time to cue.animationName
                    if (cue.time <= beatTime && key !in firedSet) {
                        firedSet.add(key)
                        fireAnimation(actorId, entity, cue.animationName)
                    }
                }
            }
            is SplineMovement -> {
                val newPos = track.evaluate(beatTime)
                entity.setPos(newPos.x, newPos.y, newPos.z)
                track.facingAt(beatTime)?.let { (yaw, pitch) ->
                    entity.yRot = yaw
                    entity.xRot = pitch
                    if (entity is LivingEntity) {
                        entity.yBodyRot = yaw
                        entity.yHeadRot = yaw
                    }
                }
            }
            is FollowActor -> {
                val target = resolveEntity(track.targetActorId)
                if (target != null) {
                    val desired = Vec3(
                        target.x + track.offset.x,
                        target.y + track.offset.y,
                        target.z + track.offset.z
                    )
                    if (track.lagSecs > 0f) {
                        val currentPos = Vec3(entity.x, entity.y, entity.z)
                        val alpha = (1f / (1f + track.lagSecs * 20f)).coerceIn(0f, 1f)
                        entity.setPos(
                            currentPos.x + (desired.x - currentPos.x) * alpha,
                            currentPos.y + (desired.y - currentPos.y) * alpha,
                            currentPos.z + (desired.z - currentPos.z) * alpha
                        )
                    } else {
                        entity.setPos(desired.x, desired.y, desired.z)
                    }
                }
            }
            is PoseLockTrack -> {
                if (entity is PokemonEntity) {
                    entity.entityData.set(PokemonEntity.POSE_TYPE, track.pose)
                } else if (entity is GenericBedrockEntity) {
                    entity.entityData.set(GenericBedrockEntity.POSE_TYPE, track.pose)
                } else {
                    Cobblemon.LOGGER.warn("[ActorDirector] Actor '$actorId' (${entity::class.simpleName}) is not posable; PoseLockTrack no-op")
                }
            }
            is AttachTrack -> {
                val parent = resolveEntity(track.parentActorId)
                if (parent != null) {
                    entity.setPos(
                        parent.x + track.offset.x,
                        parent.y + track.offset.y,
                        parent.z + track.offset.z
                    )
                }
            }
            else -> {
                Cobblemon.LOGGER.warn("[ActorDirector] Unknown ActorTrack type '${track.trackType}' on actor '$actorId'")
            }
        }
    }

    private fun fireAnimation(actorId: String, entity: Entity, name: String) {
        if (entity !is PosableEntity) {
            Cobblemon.LOGGER.warn("[ActorDirector] Actor '$actorId' entity is not PosableEntity; skipping animation '$name'")
            return
        }
        val packet = PlayPosableAnimationPacket(
            entityId = entity.id,
            animation = setOf(name),
            expressions = emptyList()
        )
        val level = entity.level() as? ServerLevel ?: run {
            Cobblemon.LOGGER.warn("[ActorDirector] Actor '$actorId' is not in a ServerLevel; cannot dispatch animation '$name'")
            return
        }
        packet.sendToPlayersAround(
            x = entity.x,
            y = entity.y,
            z = entity.z,
            distance = 64.0,
            worldKey = level.dimension()
        )
        Cobblemon.LOGGER.debug("[ActorDirector] Actor '$actorId' animation '$name' dispatched")
    }


    private fun resolveEntity(actorId: String): Entity? {
        val binding = bindings[actorId] ?: return null
        return when (binding) {
            is ActorBinding.Player -> binding.player as Entity
            is ActorBinding.Real -> binding.entity
            is ActorBinding.SynthPokemon -> spawnedEntities.getOrPut(actorId) {
                spawnSynthPokemon(actorId, binding) ?: return null
            }
            is ActorBinding.SynthBedrock -> spawnedEntities.getOrPut(actorId) {
                spawnSynthBedrock(actorId, binding) ?: return null
            }
        }
    }

    fun resolveEntityPublic(actorId: String): Entity? = resolveEntity(actorId)

    private fun cameraAnchor(actorId: String): CameraAnchor? {
        val entity = resolveEntity(actorId) ?: return null
        val previous = cameraAnchors[entity.uuid]
        if (previous != null && (!entity.isAlive || entity.isRemoved)) return previous
        return CameraAnchor(entity.position(), entity.boundingBox).also { cameraAnchors[entity.uuid] = it }
    }

    fun cameraPosition(actorId: String): Vec3? = cameraAnchor(actorId)?.position
    fun cameraBounds(actorId: String): AABB? = cameraAnchor(actorId)?.bounds

    fun previewActorBounds() = previewActors.mapValues { it.value.entity.boundingBox }

    internal fun resetPreview(beat: Beat) {
        cameraAnchors.clear()
        check(previewMode && bindings.values.none { it is ActorBinding.Real || it is ActorBinding.Player })
        releaseWaypointMotions()
        visibleActors.clear()
        activeBlends.clear()
        firedAnimationCues.clear()
        startPoseErrors.clear()
        scene.actors.keys.forEach { actorId ->
            val entity = resolveEntity(actorId) ?: return@forEach
            previewPoseResets.getOrPut(actorId) {
                when (entity) {
                    is PokemonEntity -> {
                        val pose = entity.entityData.get(PokemonEntity.POSE_TYPE)
                        val reset: () -> Unit = { entity.entityData.set(PokemonEntity.POSE_TYPE, pose) }
                        reset
                    }
                    is GenericBedrockEntity -> {
                        val pose = entity.entityData.get(GenericBedrockEntity.POSE_TYPE)
                        val reset: () -> Unit = { entity.entityData.set(GenericBedrockEntity.POSE_TYPE, pose) }
                        reset
                    }
                    else -> ({ })
                }
            }.invoke()
            entity.setPos(sceneOrigin)
            entity.yRot = 0f
            entity.xRot = 0f
            if (entity is LivingEntity) {
                entity.yBodyRot = 0f
                entity.yHeadRot = 0f
            }
        }
        onBeatEnter(beat)
    }

    private fun spawnSynthPokemon(actorId: String, binding: ActorBinding.SynthPokemon): Entity? {
        val level = serverLevel ?: run {
            Cobblemon.LOGGER.debug("[ActorDirector] serverLevel is null; cannot spawn SynthPokemon '$actorId'")
            return null
        }
        val pokemon = binding.properties.create()
        if (binding.aspects.isNotEmpty()) {
            pokemon.forcedAspects = pokemon.forcedAspects + binding.aspects
        }
        val entity = PokemonEntity(level, pokemon)

        if (previewMode) {
            // Defer spawning until sync so the packet contains the transform assigned later in this tick.
            previewActors[actorId] = PreviewPokemonActor(entity, actorId, ::currentViewerPlayers)
        } else {
            return spawnWorldActor(actorId, entity, level)
        }
        return entity
    }

    private fun spawnSynthBedrock(actorId: String, binding: ActorBinding.SynthBedrock): Entity? {
        val level = serverLevel ?: run {
            Cobblemon.LOGGER.debug("[ActorDirector] serverLevel is null; cannot spawn SynthBedrock '$actorId'")
            return null
        }
        val entity = GenericBedrockEntity(level).apply {
            category = binding.category
            aspects = binding.aspects
            scale = binding.scale
            savesToWorld = false
        }

        if (previewMode) {
            // Defer spawning until sync so the packet contains the transform assigned later in this tick.
            previewActors[actorId] = PreviewBedrockActor(entity, actorId, ::currentViewerPlayers)
        } else {
            return spawnWorldActor(actorId, entity, level)
        }
        return entity
    }

    internal fun spawnWorldActor(actorId: String, entity: Entity, level: ServerLevel): Entity? {
        try {
            updateWorldActorScope(actorId, entity)
            if (level.addFreshEntity(entity)) return entity
        } catch (error: Exception) {
            cleanupActor(actorId) { entity.remove(Entity.RemovalReason.DISCARDED) }
            throw error
        }
        cleanupActor(actorId) { entity.remove(Entity.RemovalReason.DISCARDED) }
        return null
    }

    private fun updateWorldActorScope(actorId: String, entity: Entity, viewers: Set<UUID>? = networkSync?.viewerIdsSnapshot()) {
        if (viewers == null) return
        val scoped = entity as CinematicViewerScopedEntity
        val allowed = if (isVisible(actorId)) viewers else emptySet()
        if (scoped.cobblemonSceneViewers == allowed) return
        scoped.cobblemonSceneViewers = allowed
        val level = entity.level() as? ServerLevel ?: return
        val tracker = (level.chunkSource.chunkMap as ChunkMapAccessor).entityMap[entity.id] as? TrackedEntityAccessor ?: return
        // Stationary entities otherwise retain their old pairings until a section change.
        tracker.`cobblemon$updatePlayers`(level.players())
    }

    fun updateSynthActorViewerScope(viewers: Set<UUID>) {
        // Virtual actors bypass the tracker; only world entities need the mixin-provided viewer scope.
        if (previewMode) {
            previewActors.values.forEach { it.retainViewers(viewers) }
            return
        }
        spawnedEntities.forEach { (actorId, entity) ->
            updateWorldActorScope(actorId, entity, viewers)
        }
    }

    fun isPlayerTakeoverActive(actorId: String): Boolean {
        val binding = bindings[actorId] ?: return false
        return binding is ActorBinding.Player && isVisible(actorId)
    }

    fun getTakeoverPlayer(actorId: String): ServerPlayer? {
        if (!isPlayerTakeoverActive(actorId)) return null
        return (bindings[actorId] as? ActorBinding.Player)?.player
    }

    fun forceHide(actorId: String) {
        activeBlends.remove(actorId)
        startPoseErrors.remove(actorId)
        waypointMotions.remove(actorId)?.restore()
        visibleActors[actorId] = false
        if (!previewMode) spawnedEntities[actorId]?.let { updateWorldActorScope(actorId, it) }
    }

    private fun flushPreviewSync() {
        if (!previewMode) return
        previewActors.forEach { (actorId, actor) ->
            if (isVisible(actorId)) actor.syncTransform() else actor.despawn()
        }
    }

    /** Call after all actor tracks have been evaluated for the tick. */
    fun tickPreviewSync() {
        flushPreviewSync()
    }

    fun sendPreviewSpawnsToPlayer(player: ServerPlayer) {
        if (!previewMode) return
        previewActors.values.forEach { it.sendCatchUpTo(player) }
    }
}
