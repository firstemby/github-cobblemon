/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.WaypointPathfinding.TravelMode
import com.cobblemon.mod.common.entity.OmniPathingEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import kotlin.math.abs
import kotlin.math.ceil
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal class NativeWaypointMotor(private val track: WaypointPathfinding, source: Entity) : WaypointMotor {
    private val simulation: NativeMotionEntity =
        if (source is PokemonEntity) CinematicPokemonMotor(source) else CinematicGroundMotor(source)
    private val mob = simulation.mob
    private val pokemon = mob as? CinematicPokemonMotor
    private val canFly = (source as? OmniPathingEntity)?.canFly() ?: false
    private val canWalk = (source as? OmniPathingEntity)?.canWalk() ?: true
    private val terrain = LevelWaypointTerrain(mob.level(), source.bbWidth.toDouble(), source.bbHeight.toDouble())
    private var destination: Vec3? = null
    private var speedMultiplier = 1.0
    private var flyingLeg = false
    var searchNodes: List<Vec3> = emptyList()
        private set
    var projectedTarget: Vec3? = null
        private set

    override val position: Vec3 get() = mob.position()
    override val yaw: Float get() = mob.yRot
    override var pose = PoseType.STAND
        private set
    override val onGround get() = mob.onGround()
    override val jumpCount get() = simulation.jumpCount
    override val ready get() = onGround || (canFly && track.travelMode != TravelMode.GROUND) || mob.isInWater
    override val arrived get() = destination?.let {
        position.subtract(it).horizontalDistanceSqr() < 0.09 && abs(position.y - it.y) < 0.35 &&
            (flyingLeg || onGround || mob.isInWater)
    } ?: false
    override var error: Component? = null
        private set

    init {
        mob.setPos(track.controlPoints.first())
        mob.setOnGround(terrain.supported(position))
        if (!clear(position)) error = Component.translatable("cobblemon.cinematic_editor.route.first_obstructed")
        if (track.travelMode == TravelMode.FLIGHT && !canFly) error = Component.translatable("cobblemon.cinematic_editor.route.cannot_fly")
        if (track.travelMode == TravelMode.GROUND && !canWalk) error = Component.translatable("cobblemon.cinematic_editor.route.cannot_walk")
        if (!canWalk && !canFly && !mob.isInWater) error = Component.translatable("cobblemon.cinematic_editor.route.cannot_move")
        pokemon?.allowFlight = canFly && track.travelMode != TravelMode.GROUND
        pokemon?.forceFlight = track.travelMode == TravelMode.FLIGHT
        pokemon?.setFlying(canFly && track.travelMode != TravelMode.GROUND && (!onGround || !canWalk || pokemon.forceFlight))
        updatePose(false)
    }

    override fun navigate(destination: Vec3, speedMultiplier: Float): Boolean {
        val ground = terrain.ground(destination, 32.0, 0.6)
        flyingLeg = canFly && track.travelMode != TravelMode.GROUND &&
            (track.travelMode == TravelMode.FLIGHT || !canWalk || !terrain.supported(destination) || !onGround)
        val target = if (flyingLeg || !mob.level().getFluidState(BlockPos.containing(destination)).isEmpty) destination else ground
        projectedTarget = target
        searchNodes = emptyList()
        if (target == null) { error = Component.translatable("cobblemon.cinematic_editor.route.no_ground"); return false }
        if (!clear(target)) { error = Component.translatable("cobblemon.cinematic_editor.route.obstructed"); return false }
        pokemon?.allowFlight = canFly && track.travelMode != TravelMode.GROUND
        pokemon?.setFlying(flyingLeg)
        updatePose(false)
        this.destination = target
        this.speedMultiplier = speedMultiplier.toDouble()
        val targets = NativeWaypointTargets.candidates(target, mob.bbWidth, ::canFinishFrom)
        if (targets.isEmpty()) {
            error = Component.translatable("cobblemon.cinematic_editor.route.no_approach", target.toString())
            return false
        }
        var path: Path? = null
        var searchedRange = 0
        var terrainLimited = false
        for (range in NativeWaypointTargets.searchRanges(position.distanceTo(target))) {
            if (!loaded(AABB(position, position).inflate(range + 16.0, 0.0, range + 16.0))) {
                terrainLimited = true
                break
            }
            searchedRange = range
            mob.getAttribute(Attributes.FOLLOW_RANGE)?.baseValue = range.toDouble()
            val candidate = mob.navigation.createPath(targets, 0)
            if (candidate != null && (path == null || candidate.canReach() || candidate.distToTarget < path.distToTarget)) path = candidate
            if (path?.canReach() == true) break
        }
        searchNodes = path?.let { route -> List(route.nodeCount) { route.getEntityPosAtNode(mob, it) } }.orEmpty()
        if (path == null || !path.canReach()) {
            error = when {
                terrainLimited -> Component.translatable("cobblemon.cinematic_editor.route.search_unloaded", target.toString(), searchedRange)
                path == null -> Component.translatable("cobblemon.cinematic_editor.route.no_route_target", target.toString())
                else -> Component.translatable("cobblemon.cinematic_editor.route.search_partial", searchNodes.lastOrNull().toString(), target.toString(), searchedRange)
            }
            return false
        }
        return mob.navigation.moveTo(path, this.speedMultiplier)
    }

    private fun canFinishFrom(node: Vec3, target: Vec3): Boolean {
        val fluidTarget = !mob.level().getFluidState(BlockPos.containing(target)).isEmpty
        val start = if (flyingLeg || fluidTarget) node else terrain.ground(node, 1.0, 0.0) ?: return false
        if (!flyingLeg && !fluidTarget && abs(start.y - target.y) > 0.35) return false
        val steps = ceil(start.distanceTo(target) / 0.1).toInt().coerceAtLeast(1)
        return (0..steps).all { step ->
            val point = start.lerp(target, step.toDouble() / steps)
            clear(point) && (flyingLeg || fluidTarget || terrain.supported(point))
        }
    }

    override fun step() {
        if (error != null) return
        val before = position
        if (!loaded(mob.boundingBox.inflate(2.0))) { error = Component.translatable("cobblemon.cinematic_editor.route.unloaded"); stop(); return }
        destination?.takeIf { mob.navigation.isDone && !arrived }?.let {
            mob.moveControl.setWantedPosition(it.x, it.y, it.z, speedMultiplier)
        }
        if (pokemon?.forceFlight == true) pokemon.setFlying(true)
        updatePose(false)
        simulation.tickMotion()
        updatePose(position.distanceToSqr(before) > 0.000001 && destination != null)
    }

    override fun stop() {
        val hovering = pokemon?.isFlying() == true && !onGround && !mob.isInWater
        destination = null
        mob.navigation.stop()
        (mob.moveControl as? PokemonMoveControl)?.stop()
        if (hovering) pokemon?.setFlying(true)
        mob.moveControl.setWantedPosition(mob.x, mob.y, mob.z, 0.0)
        mob.setZza(0f); mob.setXxa(0f); mob.yya = 0f
        mob.setJumping(false)
        mob.deltaMovement = Vec3(0.0, if (onGround || pokemon?.isFlying() == true) 0.0 else mob.deltaMovement.y, 0.0)
        if (pokemon?.forceFlight == true) pokemon.setFlying(true)
        updatePose(false)
    }

    private fun updatePose(moving: Boolean) {
        pose = when {
            mob.isInWater -> if (moving) PoseType.SWIM else PoseType.FLOAT
            pokemon?.isFlying() == true -> if (moving) PoseType.FLY else PoseType.HOVER
            moving -> PoseType.WALK
            else -> PoseType.STAND
        }
        pokemon?.entityData?.set(PokemonEntity.POSE_TYPE, pose)
        pokemon?.entityData?.set(PokemonEntity.MOVING, moving)
        mob.isNoGravity = pokemon?.isFlying() == true
    }

    private fun clear(position: Vec3): Boolean {
        val box = mob.boundingBox.move(position.subtract(mob.position())).deflate(0.001)
        return loaded(box) && mob.level().getBlockCollisions(mob, box).none { !it.isEmpty }
    }

    private fun loaded(bounds: AABB): Boolean {
        if (bounds.minY < mob.level().minBuildHeight || bounds.maxY >= mob.level().maxBuildHeight) return false
        for (x in (BlockPos.containing(bounds.minX, 0.0, 0.0).x shr 4)..(BlockPos.containing(bounds.maxX, 0.0, 0.0).x shr 4)) {
            for (z in (BlockPos.containing(0.0, 0.0, bounds.minZ).z shr 4)..(BlockPos.containing(0.0, 0.0, bounds.maxZ).z shr 4)) {
                if (!mob.level().hasChunkAt(BlockPos(x shl 4, 0, z shl 4))) return false
            }
        }
        return true
    }
}
