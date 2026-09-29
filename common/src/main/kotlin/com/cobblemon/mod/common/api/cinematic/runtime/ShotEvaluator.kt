/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ConstraintModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.CranePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.DollyPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionVec3Position
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FollowActorPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAlongPath
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeDerivedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OffsetModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.TruckPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.VariableVec3Position
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth
import kotlin.math.pow

/** Reuse across ticks to retain smoothing state. */
class ShotEvaluator(
    val scene: Scene,
    private val blockCollision: CameraBlockCollision? = null
) {
    private val smoothingState = mutableMapOf<Pair<String, Int>, ShotTransform>()
    private val smoothingTimes = mutableMapOf<Pair<String, Int>, Float>()
    private val previousTransforms = mutableMapOf<String, ShotTransform>()
    private val orbitTimes = mutableMapOf<String, Float>()

    fun evaluate(shotId: String, time: Float, actorDirector: ActorDirector? = null, runtime: MoLangRuntime? = null, orbitTime: Float? = null): ShotTransform? {
        if (!time.isFinite() || orbitTime?.isFinite() == false) return null
        val shot = scene.getShot(shotId) ?: return null
        return evaluateShot(shotId, shot, time, actorDirector, runtime, orbitTime)
    }

    fun resetSmoothing() {
        smoothingState.clear()
        smoothingTimes.clear()
        previousTransforms.clear()
        orbitTimes.clear()
    }

    private fun evaluateShot(shotId: String, shot: Shot, time: Float, actorDirector: ActorDirector?, runtime: MoLangRuntime? = null, orbitTime: Float? = null): ShotTransform? {
        val positionTime = if ((shot.position as? OrbitPosition)?.rememberProgress == true) orbitTime ?: time else time
        val fov = shot.fov?.let { evaluateFov(it, time, runtime) }
        val position = evaluatePosition(shot.position, positionTime, actorDirector, runtime, fov)
        val rotation = evaluateRotation(shot.rotation, position, shot.position, time, actorDirector, runtime, positionTime, fov)
        val roll = shot.roll?.let { evaluateRoll(it, time) }
        val boneHint = resolveBoneHint(shot.rotation, actorDirector)

        var baseRotation = rotation
        var out = ShotTransform(position, rotation, fov, roll, boneHint)
        if (!out.isFinite()) return null

        for ((index, modifier) in shot.modifiers.withIndex()) {
            out = applyModifier(shotId, index, modifier, out, time)
            if (!out.isFinite()) return null
        }

        val orbit = shot.position as? OrbitPosition
        if (orbit?.avoidBlocks == true) {
            val collision = blockCollision ?: actorDirector?.serverLevel?.let(CameraBlockCollision::forLevel)
            if (collision != null) {
                val focus = orbit.focus(ActorFraming.bounds(orbit.actorIds, actorDirector))
                if (!focus.x.isFinite() || !focus.y.isFinite() || !focus.z.isFinite()) return null
                val previousTime = orbitTimes[shotId]
                val previous = previousTransforms[shotId]
                var safePosition = collision.orbitPosition(focus, out.position, orbit.avoidanceSmoothing)
                    ?: previous?.position?.takeIf(collision::clear) ?: return null
                if (previous != null && previousTime != null && time > previousTime && time - previousTime <= 0.25f && collision.clear(previous.position)) {
                    safePosition = collision.slide(previous.position, safePosition)
                }
                val correctedRotation = evaluateRotation(shot.rotation, safePosition, shot.position, time, actorDirector, runtime, positionTime, fov)
                val modifiedRotation = out.rotation
                out = out.copy(position = safePosition, rotation = if (correctedRotation != null && rotation != null && modifiedRotation != null) {
                    CameraRotation(modifiedRotation.yaw + Mth.wrapDegrees(correctedRotation.yaw - rotation.yaw),
                        correctedRotation.pitch + modifiedRotation.pitch - rotation.pitch)
                } else modifiedRotation)
                baseRotation = correctedRotation
                orbitTimes[shotId] = time
            }
        }

        val aim = baseRotation
        val modified = out.rotation
        if (boneHint != null && aim != null && modified != null) {
            out = out.copy(boneHint = boneHint.copy(
                yawOffset = Mth.wrapDegrees(modified.yaw - aim.yaw),
                pitchOffset = modified.pitch - aim.pitch
            ))
        }
        if (!out.isFinite()) return null
        previousTransforms[shotId] = out
        return out
    }

    /** The server resolves actor identity only; bone positions are resolved by the client each frame. */
    private fun resolveBoneHint(source: RotationSource, actorDirector: ActorDirector?): BoneHint? {
        val look = source as? LookAt ?: return null
        val boneTarget = look.target as? LookAtTarget.ActorBone ?: return null
        val entity = actorDirector?.resolveEntityPublic(boneTarget.actorId) ?: return null
        // Retain the hint after removal so the client can hold its last resolved bone position.
        return BoneHint(
            entityId = entity.id,
            boneName = boneTarget.boneName,
            offset = boneTarget.offset,
            smooth = boneTarget.smooth
        )
    }


    private fun evaluatePosition(
        source: PositionSource,
        time: Float,
        actorDirector: ActorDirector?,
        runtime: MoLangRuntime?,
        fov: Float? = null
    ): Vec3 = when (source) {
        is FixedPosition -> source.position
        is KeyframePosition -> source.evaluate(time)
        is SplinePosition -> source.evaluate(time)
        is OrbitPosition -> source.evaluate(time, ActorFraming.bounds(source.actorIds, actorDirector), fov ?: source.framingFov,
            source.bearingActorId?.let { ActorFraming.bounds(listOf(it), actorDirector)?.center })
        is DollyPosition -> source.evaluate(time)
        is TruckPosition -> source.evaluate(time)
        is CranePosition -> source.evaluate(time)
        is VariableVec3Position -> source.evaluate(runtime)
        is ExpressionVec3Position -> source.evaluate(runtime)
        is FollowActorPosition -> {
            val position = actorDirector?.cameraPosition(source.actorId)
            if (position != null) {
                position.add(source.offset)
            } else {
                Cobblemon.LOGGER.debug(
                    "[ShotEvaluator] FollowActorPosition actor '${source.actorId}' not resolvable; using fallback"
                )
                source.fallback
            }
        }
        else -> {
            Cobblemon.LOGGER.warn("[ShotEvaluator] Unknown position source type '${source.type}'; defaulting to origin")
            Vec3.ZERO
        }
    }


    private fun evaluateRotation(
        source: RotationSource,
        position: Vec3,
        positionSource: PositionSource,
        time: Float,
        actorDirector: ActorDirector?,
        runtime: MoLangRuntime?,
        positionTime: Float = time,
        fov: Float? = null
    ): CameraRotation? = when (source) {
        is FixedRotation -> source.rotation
        is KeyframeRotation -> source.evaluate(time)
        is LookAt -> {
            val targetPos = when (val t = source.target) {
                is LookAtTarget.Position -> t.pos
                is LookAtTarget.Actors -> ActorFraming.bounds(t.actorIds, actorDirector)?.center ?: position
                is LookAtTarget.Actor -> {
                    actorDirector?.cameraPosition(t.actorId) ?: run {
                        Cobblemon.LOGGER.debug(
                            "[ShotEvaluator] LookAt target actor '${t.actorId}' not resolvable; using camera position"
                        )
                        position
                    }
                }
                is LookAtTarget.Variable -> t.evaluate(runtime)
                is LookAtTarget.Expression -> t.evaluate(runtime)
                is LookAtTarget.ActorBone -> {
                    // Bones resolve only on the client; use entity position until the client applies the bone hint.
                    actorDirector?.cameraBounds(t.actorId)?.center ?: position
                }
            }
            source.computeRotation(position, targetPos)
        }
        is LookAlongPath -> {
            val aheadTime = (positionTime.toDouble() + source.epsilon).coerceIn(-Float.MAX_VALUE.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
            val ahead = evaluatePosition(positionSource, aheadTime, actorDirector, runtime, fov)
            source.computeRotation(position, ahead)
        }
        else -> {
            Cobblemon.LOGGER.warn("[ShotEvaluator] Unknown rotation source type '${source.type}'; returning null")
            null
        }
    }


    private fun evaluateFov(source: FovSource, time: Float, runtime: MoLangRuntime?): Float? = when (source) {
        is FixedFov -> source.fov
        is KeyframeFov -> source.evaluate(time)
        is ExpressionFov -> runtime?.let { source.evaluate(it) } ?: source.fallbackFov
        else -> {
            Cobblemon.LOGGER.warn("[ShotEvaluator] Unknown fov source type '${source.type}'; returning null")
            null
        }
    }


    private fun evaluateRoll(source: RollSource, time: Float): Float? = when (source) {
        is FixedRoll -> source.roll
        is KeyframeRoll -> source.evaluate(time)
        is ShakeDerivedRoll -> source.evaluate(time)
        else -> {
            Cobblemon.LOGGER.warn("[ShotEvaluator] Unknown roll source type '${source.type}'; returning null")
            null
        }
    }


    private fun applyModifier(
        shotId: String,
        modifierIndex: Int,
        modifier: ShotModifier,
        current: ShotTransform,
        beatTime: Float
    ): ShotTransform = when (modifier) {
        is SmoothingModifier -> {
            val key = shotId to modifierIndex
            val previousTime = smoothingTimes[key]
            val result = applySmoothing(smoothingState[key], previousTime?.let { beatTime - it }, modifier, current)
            // Later modifiers must not feed their offsets back into the smoothing history.
            if (result.isFinite()) {
                smoothingState[key] = result
                smoothingTimes[key] = beatTime
            }
            result
        }
        is ShakeModifier -> applyShake(modifier, current, beatTime)
        is OffsetModifier -> applyOffset(modifier, current)
        is ConstraintModifier -> current.copy(position = modifier.apply(current.position))
        else -> {
            Cobblemon.LOGGER.warn("[ShotEvaluator] Unknown modifier type '${modifier.type}'; pass-through")
            current
        }
    }

    private fun ShotTransform.isFinite(): Boolean =
        position.x.isFinite() && position.y.isFinite() && position.z.isFinite() &&
            rotation?.let { it.yaw.isFinite() && it.pitch.isFinite() } != false &&
            (fov == null || fov in 1f..179f) && roll?.isFinite() != false

    private fun applySmoothing(previous: ShotTransform?, elapsedSeconds: Float?, mod: SmoothingModifier, current: ShotTransform): ShotTransform {
        if (previous == null || elapsedSeconds == null || elapsedSeconds < 0f) return current
        val prev = previous

        val samples = elapsedSeconds / 0.05f
        fun retention(factor: Float) = if (factor == 0f) 0f else factor.pow(samples)
        val pf = retention(mod.positionFactor)
        val rf = retention(mod.rotationFactor)
        val fovf = retention(mod.fovFactor)

        val smoothedPosition = if (pf > 0f) {
            val alpha = 1f - pf
            Vec3(
                prev.position.x + (current.position.x - prev.position.x) * alpha,
                prev.position.y + (current.position.y - prev.position.y) * alpha,
                prev.position.z + (current.position.z - prev.position.z) * alpha
            )
        } else current.position

        val smoothedRotation = if (rf > 0f && current.rotation != null && prev.rotation != null) {
            val alpha = 1f - rf
            CameraRotation(
                yaw = prev.rotation.yaw + Mth.wrapDegrees(current.rotation.yaw - prev.rotation.yaw) * alpha,
                pitch = prev.rotation.pitch + (current.rotation.pitch - prev.rotation.pitch) * alpha
            )
        } else current.rotation

        val smoothedFov = if (fovf > 0f && current.fov != null && prev.fov != null) {
            val alpha = 1f - fovf
            prev.fov + (current.fov - prev.fov) * alpha
        } else current.fov

        return current.copy(position = smoothedPosition, rotation = smoothedRotation, fov = smoothedFov)
    }

    private fun applyShake(mod: ShakeModifier, current: ShotTransform, beatTime: Float): ShotTransform {
        val decay = mod.decayFactor(beatTime)
        if (decay <= 0f) return current
        val (nx, ny, nz) = mod.noise3(beatTime)
        val posA = mod.positionAmplitude * decay
        val rotA = mod.rotationAmplitudeDeg * decay
        val shakenPosition = Vec3(
            current.position.x + nx * posA,
            current.position.y + ny * posA,
            current.position.z + nz * posA
        )
        val shakenRotation = current.rotation?.let {
            CameraRotation(it.yaw + nx * rotA, it.pitch + ny * rotA)
        }
        return current.copy(position = shakenPosition, rotation = shakenRotation)
    }

    private fun applyOffset(mod: OffsetModifier, current: ShotTransform): ShotTransform {
        return current.copy(
            position = Vec3(
                current.position.x + mod.positionOffset.x,
                current.position.y + mod.positionOffset.y,
                current.position.z + mod.positionOffset.z
            ),
            rotation = current.rotation?.let {
                CameraRotation(it.yaw + mod.yawOffset, it.pitch + mod.pitchOffset)
            },
            fov = current.fov?.let { it + mod.fovOffset },
            roll = current.roll?.let { it + mod.rollOffset }
        )
    }
}

data class ShotTransform(
    val position: Vec3,
    val rotation: CameraRotation? = null,
    val fov: Float? = null,
    val roll: Float? = null,
    val boneHint: BoneHint? = null
)

data class BoneHint(
    val entityId: Int,
    val boneName: String,
    val offset: Vec3,
    val smooth: Boolean,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f
)
