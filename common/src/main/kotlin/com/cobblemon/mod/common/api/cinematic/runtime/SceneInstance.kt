/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiActionType
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiArgValue
import com.cobblemon.mod.common.api.molangui.MolangUiContract
import com.cobblemon.mod.common.api.molangui.MolangUiContractRegistry
import com.cobblemon.mod.common.api.molangui.MolangUiOpenRequest
import com.cobblemon.mod.common.api.molangui.MolangUiOpenResult
import com.cobblemon.mod.common.api.molangui.MolangUiService
import com.cobblemon.mod.common.api.molangui.MolangUiSessionHandle
import com.cobblemon.mod.common.api.molangui.ParameterValue
import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneStartPacket
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.resolveString
import java.util.UUID
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

class SceneInstance(
    val id: UUID,
    val scene: Scene,
    val origin: Vec3,
    val actorBindings: Map<String, ActorBinding>,
    val parameterOverrides: Map<String, Any> = emptyMap(),
    val previewMode: Boolean = false
) {
    private val viewers: MutableSet<UUID> = mutableSetOf()

    private val openGuiSessions: MutableMap<String, MutableList<MolangUiSessionHandle>> = mutableMapOf()

    private var guiDispatchedUpTo: Float = -1f

    val networkSync: SceneNetworkSync = SceneNetworkSync(id, viewers)
    val stateMachine: BeatStateMachine = BeatStateMachine(scene)
    val shotEvaluator: ShotEvaluator = ShotEvaluator(scene)
    val cutResolver: CutResolver = CutResolver()
    val actorDirector: ActorDirector = ActorDirector(
        scene = scene,
        bindings = actorBindings,
        networkSync = networkSync,
        serverLevel = null,
        sceneOrigin = origin,
        sceneId = id,
        previewMode = previewMode,
        runtimeProvider = { stateMachine.runtime }
    )
    val feedRouter: FeedRouter = FeedRouter(scene)

    init {
        networkSync.onViewersChanged { viewers ->
            actorDirector.updateSynthActorViewerScope(viewers)
        }
    }

    private var lastTickNanos: Long = 0L
    private var lastDispatchedBeatId: String? = null
    private val lastShotPerFeed: MutableMap<String, String> = mutableMapOf()
    var previewPlayback: PreviewPlayback? = null
        private set
    var beforePreviewTick: (() -> Unit)? = null
    var onPreviewTick: (() -> Unit)? = null
    private var previewSnap = false
    private val previewFeedBlends = PreviewFeedBlends()
    private val cueDispatcher = SceneCueDispatcher()
    private val cueListeners = mutableListOf<(SceneCue) -> Unit>()
    private val stopListeners = mutableListOf<() -> Unit>()
    private val feedTransformListeners = mutableListOf<(FeedTransformPacket) -> Unit>()

    fun onFeedTransforms(listener: (FeedTransformPacket) -> Unit) {
        check(!stopped) { "Scene has already stopped" }
        feedTransformListeners.add(listener)
    }

    fun onCue(listener: (SceneCue) -> Unit) { cueListeners.add(listener) }

    fun onStop(listener: () -> Unit) {
        check(!stopped) { "Scene has already stopped" }
        stopListeners.add(listener)
    }

    private fun dispatchCues(beat: Beat, time: Float) {
        if (previewMode) return
        cueDispatcher.dispatch(beat, time, ::isActive) { cue ->
            for (listener in cueListeners.toList()) {
                if (!isActive()) break
                try {
                    listener(cue)
                } catch (error: Exception) {
                    Cobblemon.LOGGER.error("Scene {} cue '{}' failed", id, cue.id, error)
                    stop()
                }
            }
        }
    }

    fun configurePreview(beatId: String, seconds: Float, paused: Boolean) {
        check(previewMode && actorBindings.values.none { it is ActorBinding.Real || it is ActorBinding.Player })
        val beat = scene.getBeat(beatId) ?: throw CinematicValidationException(
            Component.translatable("cobblemon.cinematic_editor.preview.unknown_beat", beatId))
        ActorDependencyOrder.resolve(scene.actors.keys, beat)
        val playback = previewPlayback?.takeIf { it.beat.id == beatId } ?: PreviewPlayback(beat)
        playback.seek(seconds, paused)
        previewPlayback = playback
        previewSnap = true
        lastTickNanos = System.nanoTime()
    }

    private fun tickPreview(deltaSeconds: Float) {
        val playback = previewPlayback ?: return
        playback.advance(deltaSeconds)
        var packet: FeedTransformPacket? = null
        val changed = playback.evaluate(
            reset = {
                stateMachine.resetPreview(playback.beat)
                applyParameterOverrides()
                actorDirector.resetPreview(playback.beat)
                shotEvaluator.resetSmoothing()
                feedRouter.resetOrbitProgress()
                previewFeedBlends.reset()
            },
            sample = { time ->
                stateMachine.samplePreview(playback.beat, time)
                actorDirector.evaluate(playback.beat, time)
                packet = previewFeedBlends.apply(feedRouter.buildTransformPacket(id, shotEvaluator, cutResolver, actorDirector,
                    stateMachine.runtime, playback.beat, time), playback.beat, time)
            }
        )
        if (changed) {
            val beat = playback.beat
            if (lastDispatchedBeatId != beat.id || previewSnap) {
                networkSync.sendBeatChange(BeatChangePacket(id, beat.id, true, beat.renderPlayerBody,
                    beat.hiddenHudElements, beat.cuts.keys))
                lastDispatchedBeatId = beat.id
                lastShotPerFeed.clear()
            }
            for (feed in scene.feeds) {
                val cut = cutResolver.resolveLiveShot(beat, feed.id, playback.time) ?: continue
                if (lastShotPerFeed[feed.id] != cut.shotId) {
                    networkSync.sendFeedAssignment(FeedAssignmentPacket(id, feed.id, cut.shotId,
                        FeedAssignmentPacket.TransitionType.INSTANT, 0f))
                    lastShotPerFeed[feed.id] = cut.shotId
                }
            }
            packet?.let(networkSync::sendFeedTransform)
            actorDirector.tickPreviewSync()
            previewSnap = false
        }
        onPreviewTick?.invoke()
    }

    fun start(): Boolean {
        // Apply overrides before starting so parameter defaults cannot replace caller-supplied values.
        applyParameterOverrides()
        if (!stateMachine.start()) return false
        lastTickNanos = System.nanoTime()
        networkSync.sendSceneStart(SceneStartPacket(
            sceneId = id,
            sceneDefinitionId = scene.id,
            origin = origin,
            feeds = scene.feeds
        ))
        return true
    }

    // Multiple teardown paths can converge; a second stop must not rebroadcast SceneEndPacket.
    private var stopped = false

    fun stop() {
        if (stopped) return
        stopped = true
        cleanup { stateMachine.stop() }
        cleanup { actorDirector.dispose() }
        lastDispatchedBeatId = null
        lastShotPerFeed.clear()
        closeAllOpenGuis()
        cleanup { networkSync.sendSceneEnd(SceneEndPacket(sceneId = id)) }
        cueListeners.clear()
        feedTransformListeners.clear()
        val listeners = stopListeners.toList()
        stopListeners.clear()
        listeners.forEach { listener -> cleanup(listener) }
    }

    private fun cleanup(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Scene {} cleanup failed", id, error)
        }
    }

    private fun applyParameterOverrides() {
        val runtime = stateMachine.runtime
        for ((name, def) in scene.parameters) {
            (def.default as? ParameterDefault.StringDefault)?.let {
                runtime.environment.variable.map[name] =
                    StringValue(it.value)
            }

            val override = parameterOverrides[name] ?: continue
            when (def.type) {
                ParameterType.NUMBER -> {
                    val v = (override as? Number)?.toDouble()
                    if (v == null || !v.isFinite()) {
                        Cobblemon.LOGGER.warn("[SceneInstance] Parameter '{}' expects NUMBER, got {}; ignoring", name, override::class.simpleName)
                    } else {
                        runtime.environment.variable.map[name] =
                            DoubleValue(v)
                    }
                }
                ParameterType.VEC3 -> {
                    val v = override as? Vec3
                    if (v == null || !v.x.isFinite() || !v.y.isFinite() || !v.z.isFinite()) {
                        Cobblemon.LOGGER.warn("[SceneInstance] Parameter '{}' expects VEC3, got {}; ignoring", name, override::class.simpleName)
                    } else {
                        runtime.environment.variable.map["${name}_x"] = DoubleValue(v.x)
                        runtime.environment.variable.map["${name}_y"] = DoubleValue(v.y)
                        runtime.environment.variable.map["${name}_z"] = DoubleValue(v.z)
                    }
                }
                ParameterType.STRING -> {
                    val v = override as? String
                    if (v == null) {
                        Cobblemon.LOGGER.warn("[SceneInstance] Parameter '{}' expects STRING, got {}; ignoring", name, override::class.simpleName)
                    } else {
                        runtime.environment.variable.map[name] =
                            StringValue(v)
                    }
                }
            }
        }
    }

    private fun closeAllOpenGuis() {
        val sessions = openGuiSessions.values.flatten()
        openGuiSessions.clear()
        sessions.forEach { session -> cleanup { session.close() } }
    }

    private fun dispatchGuiActions(beat: Beat, currentBeatTime: Float) {
        val track = beat.guiTrack ?: return
        for (action in track.actions) {
            if (!isActive()) return
            if (action.time > guiDispatchedUpTo && action.time <= currentBeatTime) {
                fireGuiAction(action.type, action.guiId, action.args)
            }
        }
        guiDispatchedUpTo = currentBeatTime
    }

    private fun fireGuiAction(
        actionType: SceneGuiActionType,
        guiId: String,
        args: Map<String, SceneGuiArgValue>
    ) {
        val resourceId = try { ResourceLocation.parse(guiId) } catch (e: Exception) {
            Cobblemon.LOGGER.warn("[SceneInstance] GUI action has malformed id '{}'; skipping", guiId)
            return
        }
        when (actionType) {
            SceneGuiActionType.OPEN -> {
                val contract = MolangUiContractRegistry.getContract(resourceId)
                if (contract == null) {
                    Cobblemon.LOGGER.warn("[SceneInstance] GUI action references unknown contract '{}'; skipping", guiId)
                    return
                }
                val resolvedArgs = resolveArgs(args, contract) ?: return
                val sessions = mutableListOf<MolangUiSessionHandle>()
                viewers.forEach { viewerId ->
                    val player = viewerId.getPlayer() ?: return@forEach
                    when (val result = MolangUiService.open(player, MolangUiOpenRequest(resourceId, resolvedArgs))) {
                        is MolangUiOpenResult.Opened -> sessions += result.session
                        is MolangUiOpenResult.Rejected -> Cobblemon.LOGGER.warn(
                            "[SceneInstance] Could not open GUI '{}' for {}: {}",
                            guiId, player.scoreboardName, result.detail
                        )
                    }
                }
                openGuiSessions.put(guiId, sessions)?.forEach(MolangUiSessionHandle::close)
            }
            SceneGuiActionType.CLOSE -> {
                openGuiSessions.remove(guiId)?.forEach(MolangUiSessionHandle::close)
            }
            SceneGuiActionType.UPDATE_ARGS -> {
                val contract = MolangUiContractRegistry.getContract(resourceId) ?: return
                val resolvedArgs = resolveArgs(args, contract) ?: return
                openGuiSessions[guiId]?.forEach { it.updateParameters(resolvedArgs) }
            }
        }
    }

    private fun resolveArgs(
        args: Map<String, SceneGuiArgValue>,
        contract: MolangUiContract
    ): Map<String, ParameterValue>? {
        if (args.keys.any { it !in contract.parameters }) {
            Cobblemon.LOGGER.warn("[SceneInstance] GUI '{}' received an undeclared argument", contract.id)
            return null
        }
        val out = mutableMapOf<String, ParameterValue>()
        args.forEach { (key, value) ->
            val raw = when (value) {
                is SceneGuiArgValue.Expression -> {
                    try {
                        stateMachine.runtime.resolveString(value.value.asExpressionLike())
                    } catch (e: Exception) {
                        Cobblemon.LOGGER.warn("[SceneInstance] GUI arg expression '{}' failed: {}", value.value, e.message)
                        return null
                    }
                }
                is SceneGuiArgValue.StringLiteral -> value.value
            }
            val type = contract.parameters.getValue(key).type
            out[key] = ParameterValue.parse(type, raw) ?: run {
                Cobblemon.LOGGER.warn("[SceneInstance] GUI arg '{}' does not match {}", key, type.id)
                return null
            }
        }
        return out
    }

    fun isActive(): Boolean = stateMachine.isActive()

    fun addViewer(playerId: UUID) {
        networkSync.addViewer(playerId)
    }

    fun addViewerPlayer(player: ServerPlayer) {
        check(!stopped) { "Scene has already stopped" }
        val wasViewer = player.uuid in networkSync.viewerIdsSnapshot()
        try {
            actorDirector.setServerLevel(player.serverLevel())
            addViewer(player.uuid)

            // Session initialization must arrive before any feed transforms can be routed.
            SceneStartPacket(
                sceneId = id,
                sceneDefinitionId = scene.id,
                origin = origin,
                feeds = scene.feeds
            ).sendToPlayer(player)

            val currentBeat = stateMachine.currentBeat
            if (currentBeat != null) {
                BeatChangePacket(
                    sceneId = id,
                    beatId = currentBeat.id,
                    instantTransition = true,
                    renderPlayerBody = currentBeat.renderPlayerBody,
                    hiddenHudElements = currentBeat.hiddenHudElements,
                    activeFeedIds = currentBeat.cuts.keys
                ).sendToPlayer(player)

                // Late joiners need shot assignments before the next tick's transforms arrive.
                for (feedId in currentBeat.cuts.keys) {
                    val shotId = cutResolver.resolveLiveShot(currentBeat, feedId, stateMachine.beatTime)?.shotId ?: continue
                    FeedAssignmentPacket(
                        sceneId = id,
                        feedId = feedId,
                        shotId = shotId,
                        transitionType = FeedAssignmentPacket.TransitionType.INSTANT,
                        transitionDurationSeconds = 0f
                    ).sendToPlayer(player)
                }
            }

            actorDirector.sendPreviewSpawnsToPlayer(player)
        } catch (error: Exception) {
            if (!wasViewer) {
                cleanup { removeViewer(player.uuid) }
                cleanup { SceneEndPacket(sceneId = id).sendToPlayer(player) }
            }
            throw error
        }
    }

    fun removeViewer(playerId: UUID): Boolean {
        return networkSync.removeViewer(playerId)
    }

    fun isPlayerTakeoverImmune(playerId: UUID): Boolean {
        if (!scene.damageImmunityDuringTakeover) return false
        return actorDirector.bindings.entries.any { (actorId, binding) ->
            binding is ActorBinding.Player &&
                binding.player.uuid == playerId &&
                actorDirector.isPlayerTakeoverActive(actorId)
        }
    }

    fun setVariable(name: String, value: Double) {
        stateMachine.runtime.environment.variable.map[name] =
            DoubleValue(value)
    }

    fun getVariable(name: String): Double? {
        return stateMachine.runtime.environment.variable.map[name]?.asDouble()
    }

    fun updateActorBinding(actorId: String, binding: ActorBinding) {
        actorDirector.updateBinding(actorId, binding)
    }

    fun onPlayerDisconnect(playerId: UUID) {
        removeViewer(playerId)

        val affectedActor = actorDirector.bindings.entries.firstOrNull { (_, b) ->
            b is ActorBinding.Player && b.player.uuid == playerId
        }?.key

        if (affectedActor == null) return

        when (scene.disconnectPolicy) {
            DisconnectPolicy.END_SCENE -> {
                Cobblemon.LOGGER.warn(
                    "[SceneInstance] Takeover player {} disconnected during scene {}; END_SCENE policy — stopping scene",
                    playerId, id
                )
                stop()
            }
            DisconnectPolicy.SUBSTITUTE -> {
                val fallback = (actorDirector.bindings[affectedActor] as? ActorBinding.Player)?.fallbackPokemon
                if (fallback != null) {
                    Cobblemon.LOGGER.warn(
                        "[SceneInstance] Takeover player {} disconnected; SUBSTITUTE policy — swapping actor '{}' to SynthPokemon",
                        playerId, affectedActor
                    )
                    actorDirector.updateBinding(affectedActor, ActorBinding.SynthPokemon(properties = fallback))
                } else {
                    Cobblemon.LOGGER.warn(
                        "[SceneInstance] Takeover player {} disconnected; SUBSTITUTE policy requested but ActorBinding.Player has no fallbackPokemon — degrading to HIDE_ACTOR",
                        playerId
                    )
                    actorDirector.forceHide(affectedActor)
                }
            }
            DisconnectPolicy.HIDE_ACTOR -> {
                Cobblemon.LOGGER.warn(
                    "[SceneInstance] Takeover player {} disconnected during scene {}; HIDE_ACTOR policy — hiding actor '{}'",
                    playerId, id, affectedActor
                )
                actorDirector.forceHide(affectedActor)
            }
            DisconnectPolicy.FAIL -> {
                Cobblemon.LOGGER.warn(
                    "[SceneInstance] Takeover player {} disconnected during scene {}; FAIL policy — marking scene failed and stopping",
                    playerId, id
                )
                stop()
            }
        }
    }

    fun tick() {
        if (!isActive()) return
        if (previewMode) beforePreviewTick?.invoke()
        val now = System.nanoTime()
        val deltaSeconds = ((now - lastTickNanos) / 1_000_000_000.0).toFloat()
        lastTickNanos = now

        tick(deltaSeconds)
    }

    internal fun tick(deltaSeconds: Float) {
        if (!isActive()) return

        if (previewMode) {
            tickPreview(deltaSeconds)
            return
        }

        if (lastDispatchedBeatId == null) stateMachine.currentBeat?.let { enterBeat(it, null) }
        val advance = stateMachine.advance(deltaSeconds) { beat, time ->
            dispatchCues(beat, time)
            if (isActive()) dispatchGuiActions(beat, beat.duration?.let { time.coerceAtMost(it) } ?: time)
        }
        if (advance is BeatStateMachine.AdvanceResult.Finished) {
            stop()
            return
        }
        if (advance !is BeatStateMachine.AdvanceResult.Running) return

        val currentBeat = advance.beat
        dispatchCues(currentBeat, stateMachine.beatTime)
        if (!isActive()) return

        if (advance.entered || lastDispatchedBeatId != currentBeat.id) enterBeat(currentBeat, advance.transition)
        dispatchGuiActions(currentBeat, stateMachine.beatTime)
        if (!isActive()) return

        actorDirector.evaluate(currentBeat, stateMachine.beatTime)
        val feedPacket = feedRouter.buildTransformPacket(
            id,
            shotEvaluator,
            cutResolver,
            actorDirector,
            stateMachine.runtime,
            currentBeat,
            stateMachine.beatTime,
            advance.elapsedBeatSeconds
        )
        for (feed in scene.feeds) {
            val resolution = cutResolver.resolveLiveShot(currentBeat, feed.id, stateMachine.beatTime) ?: continue
            val previousShotId = lastShotPerFeed[feed.id]
            if (previousShotId != resolution.shotId) {
                val (transitionType, duration) = when (val t = advance.transition?.transition ?: resolution.transition) {
                    CutTransition.Instant ->
                        FeedAssignmentPacket.TransitionType.INSTANT to 0f
                    is CutTransition.Blend ->
                        FeedAssignmentPacket.TransitionType.BLEND to t.duration
                }
                networkSync.sendFeedAssignment(FeedAssignmentPacket(
                    sceneId = id,
                    feedId = feed.id,
                    shotId = resolution.shotId,
                    transitionType = transitionType,
                    transitionDurationSeconds = duration
                ))
                lastShotPerFeed[feed.id] = resolution.shotId
            }
        }

        // A cut must reach the client before the pose that starts its transition.
        networkSync.sendFeedTransform(feedPacket)
        feedTransformListeners.toList().forEach { it(feedPacket) }

        actorDirector.tickPreviewSync()
    }

    private fun enterBeat(beat: Beat, transition: BeatTransition?) {
        actorDirector.onBeatEnter(beat)
        networkSync.sendBeatChange(BeatChangePacket(
            sceneId = id,
            beatId = beat.id,
            instantTransition = transition?.transition == CutTransition.Instant,
            renderPlayerBody = beat.renderPlayerBody,
            hiddenHudElements = beat.hiddenHudElements,
            activeFeedIds = beat.cuts.keys
        ))
        lastDispatchedBeatId = beat.id
        lastShotPerFeed.clear()
        guiDispatchedUpTo = -1f
    }
}
