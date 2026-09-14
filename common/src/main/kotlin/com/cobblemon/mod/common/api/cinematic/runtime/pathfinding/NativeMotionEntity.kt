/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.mixin.accessor.AccessorEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.MovementEmission
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.phys.Vec3

internal interface NativeMotionEntity {
    val mob: Mob
    val jumpCount: Int
    fun tickMotion()
}

internal class CinematicPokemonMotor(source: PokemonEntity) :
    PokemonEntity(source.level(), source.pokemon.clone(registryAccess = source.level().registryAccess())), NativeMotionEntity {
    override val mob: Mob get() = this
    override var jumpCount = 0
        private set
    var allowFlight = true
    var forceFlight = false
    private var actorDimensions: EntityDimensions? = null

    init {
        actorDimensions = EntityDimensions.scalable(source.bbWidth, source.bbHeight)
        refreshDimensions()
        enablePoseTypeRecalculation = false
        isSilent = true
        getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = source.getAttributeValue(Attributes.MOVEMENT_SPEED)
        getAttribute(Attributes.STEP_HEIGHT)?.baseValue = source.maxUpStep().toDouble()
        random.setSeed(0)
    }

    override fun createNavigation(world: Level) = object : OmniPathNavigation(world, this@CinematicPokemonMotor) {
        override fun createPathFinder(range: Int): PathFinder = super.createPathFinder(4096)
        // The waypoint controller owns fixed-tick replanning; native timeouts use the live world clock.
        override fun doStuckDetection(position: Vec3) {}
        override fun recomputePath() {}
    }

    override fun customServerAiStep() {}
    override fun canFly() = allowFlight && super.canFly()
    override fun couldStopFlying() = !forceFlight && super.couldStopFlying()
    override fun getDefaultDimensions(pose: Pose) = actorDimensions ?: super.getDefaultDimensions(pose)
    override fun checkInsideBlocks() {}
    override fun pushEntities() {}
    override fun canCollideWith(entity: Entity) = false
    override fun canBeCollidedWith() = false
    override fun getMovementEmission() = MovementEmission.NONE
    override fun checkFallDamage(distance: Double, onGround: Boolean, state: BlockState, pos: BlockPos) {}
    override fun hurt(source: DamageSource, amount: Float) = false

    override fun jumpFromGround() {
        jumpCount++
        super.jumpFromGround()
    }

    override fun tickMotion() {
        xo = x; yo = y; zo = z
        tickCount++
        updateInWaterStateAndDoFluidPushing()
        (this as AccessorEntity).cobblemonUpdateFluidOnEyes()
        aiStep()
    }
}

internal class CinematicGroundMotor(source: Entity) :
    PathfinderMob(EntityType.ZOMBIE, source.level()), NativeMotionEntity {
    override val mob: Mob get() = this
    override var jumpCount = 0
        private set
    private var actorDimensions: EntityDimensions? = null

    init {
        actorDimensions = EntityDimensions.scalable(source.bbWidth, source.bbHeight)
        refreshDimensions()
        isSilent = true
        getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue =
            (source as? Mob)?.getAttributeValue(Attributes.MOVEMENT_SPEED) ?: 0.25
        random.setSeed(0)
    }

    override fun createNavigation(world: Level) = object : GroundPathNavigation(this@CinematicGroundMotor, world) {
        override fun createPathFinder(range: Int): PathFinder = super.createPathFinder(4096)
        override fun doStuckDetection(position: Vec3) {}
        override fun recomputePath() {}
    }

    override fun customServerAiStep() {}
    override fun getDefaultDimensions(pose: Pose) = actorDimensions ?: super.getDefaultDimensions(pose)
    override fun checkInsideBlocks() {}
    override fun pushEntities() {}
    override fun canCollideWith(entity: Entity) = false
    override fun canBeCollidedWith() = false
    override fun getMovementEmission() = MovementEmission.NONE
    override fun checkFallDamage(distance: Double, onGround: Boolean, state: BlockState, pos: BlockPos) {}
    override fun hurt(source: DamageSource, amount: Float) = false

    override fun jumpFromGround() {
        jumpCount++
        super.jumpFromGround()
    }

    override fun tickMotion() {
        xo = x; yo = y; zo = z
        tickCount++
        updateInWaterStateAndDoFluidPushing()
        (this as AccessorEntity).cobblemonUpdateFluidOnEyes()
        aiStep()
    }
}
