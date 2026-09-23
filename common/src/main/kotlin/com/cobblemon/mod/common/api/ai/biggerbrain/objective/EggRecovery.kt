/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt as resolveVariableInt
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class EggRecovery(
    private val speedMultiplier: Expression = "2.0".asExpression(), // another funny speed number for testing
    private val searchRadius: Int = 20,
    private val searchIntervalTicks: Int = 20
) : Objective {
    private var fixation: EntityFixation? = null
    private var searchCooldownTicks = 0

    override fun getDescription(entity: LivingEntity) = "Egg Recovery"

    override fun perform(entity: LivingEntity, brain: BiggerBrain, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive)
            return ObjectiveStatus.INVALID

        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        // get the nest in memory if it exists and check to see if it has an egg
        val nestPos = brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) ?: return ObjectiveStatus.INACTIVE
        if (brain.getMemory(CobblemonMemories.EGG_IN_NEST) != false)
            return ObjectiveStatus.INACTIVE
        // if there is a theif they are probably being chased so nothing to do about recovery yet
        if (brain.hasMemoryValue(CobblemonMemories.EGG_THIEF))
            return ObjectiveStatus.INACTIVE

        if (brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        // if egg is held it needs to go back to the nest
        if (brain.getMemory(CobblemonMemories.HOLDING_EGG) == true)
            return returnEgg(entity, brain, world, nestPos)
        // if we have a known egg position then go get it
        val eggPos = brain.getMemory(CobblemonMemories.EGG_LOCATION)
        if (eggPos != null)
            return fetchEgg(entity, brain, world, eggPos)
        // if any of the above isnt needed then we probably have searching for the egg left
        return searchForEgg(entity, brain, world, nestPos, activeObjectiveTags)
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        // start with finding the egg if the location is known when we start.
        // should we have an if statement above this to check if we should search for the egg first or go to the egg? idk
        brain.subscribe(Priority.LOW, CobblemonMemories.EGG_LOCATION) { eggPos, _ ->
            if (eggPos != null && fixation is LookAndMoveFixation && brain.isFixation(fixation)) {
                brain.distract(Distraction.FORCE)
                fixation = null
            }
            true
        }
    }

    // look for the egg a while in an area until giving up
    private fun searchForEgg(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, nestPos: BlockPos, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)
        // if the countdown is up then clear objective
        if (shouldGiveUpSearch(entity, brain, world, nestPos))
            return ObjectiveStatus.INACTIVE
        // if our cooldown is active then tick it down
        if (searchCooldownTicks > 0) {
            searchCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        // set the cooldown so it can begin
        searchCooldownTicks = searchIntervalTicks

        // choose a random spot around the nest to check for the egg todo should we have it also consider last seen egg position? or reincorporate player disturbances?
        val searchPos = chooseRandomPos(entity, Vec3.atCenterOf(nestPos)) ?: return ObjectiveStatus.ACTIVE
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(searchPos, mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier), 1)) ?: return ObjectiveStatus.ACTIVE
        fixation = brain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE, DistractionType.COMFORT),
                description = "Egg Recovery: searching for the egg",
                lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(entity, tracker.walkTarget),
                movementTracker = tracker
            )
        )
        return ObjectiveStatus.FIXATED
    }

    // has the search countdown ended and should the nest be abandoned?
    private fun shouldGiveUpSearch(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, nestPos: BlockPos): Boolean {
        // if there is an active countdown and it isnt at the give up timeframe then don't wipe memories yet
        if (brain.getMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN) != true)
            return false
        if (entity.brain.getTimeUntilExpiry(CobblemonMemories.EGG_SEARCH_COUNTDOWN) > GIVE_UP_SEARCH_TIME)
            return false
        brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
        brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
        brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
        brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)
        val nest = world.getBlockEntity(nestPos) as? NestBlockEntity
        // clear the parent info on the nest itself if it is still around
        if (nest != null) {
            nest.parents = Pair(null, null)
            nest.nestClaimedBy = Pair(null, null)
        }
        return true
    }

    // path to the egg and pick it up
    private fun fetchEgg(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, eggPos: BlockPos): ObjectiveStatus {
        val eggCenter = Vec3.atCenterOf(eggPos)
        if (entity.position().distanceTo(eggCenter) <= CLOSE_ENOUGH_DISTANCE) {
            pickUpEgg(entity, brain, world)
            return ObjectiveStatus.ACTIVE
        }
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(eggCenter, mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier), 1)) ?: return ObjectiveStatus.ACTIVE
        fixation = brain.fixate(FetchEggFixation(this, tracker, LookingTracker.fromNavigationPathOrWalkTarget(entity, tracker.walkTarget)))
        return ObjectiveStatus.FIXATED
    }

    // pickup egg and put it in "hand" of the mon
    private fun pickUpEgg(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel): Boolean {
        // get any egg item entities that are from the parent and within their pickup radius
        val eggItem = world.getEntitiesOfClass(ItemEntity::class.java, entity.boundingBox.inflate(PICKUP_REACH)) { item ->
            item.item.item == CobblemonItems.POKEMON_EGG && item.item.get(CobblemonItemComponents.EGG_DATA)?.isParent(entity.pokemon.uuid) == true
        }.minByOrNull { it.distanceToSqr(entity) }

        if (eggItem == null) {
            // the egg isn't where it was last seen so clear location memory
            brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            return false
        }
        entity.setItemInHand(InteractionHand.MAIN_HAND, eggItem.item.copy())
        eggItem.remove(Entity.RemovalReason.DISCARDED)
        brain.setMemory(CobblemonMemories.HOLDING_EGG, true)
        brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
        brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        return true
    }

    // carry the egg back to the nest
    private fun returnEgg(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, nestPos: BlockPos): ObjectiveStatus {
        // if the nest is gone when trying to bring it back we should maybe drop the egg and stop the objective.... todo or should they just hold the egg forever? build a new nest?
        if (world.getBlockEntity(nestPos) !is NestBlockEntity) {
            val heldEgg = entity.getItemInHand(InteractionHand.MAIN_HAND)
            if (!heldEgg.isEmpty)
                entity.spawnAtLocation(heldEgg.copy())
            entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
            brain.eraseMemory(CobblemonMemories.HOLDING_EGG)

            return ObjectiveStatus.INACTIVE
        }
        val nestCenter = Vec3.atCenterOf(nestPos)
        if (entity.position().distanceTo(nestCenter) <= CLOSE_ENOUGH_DISTANCE) {
            placeEgg(entity, brain, world, nestPos)
            return ObjectiveStatus.ACTIVE
        }
        // make sure only forced distactions stop the parent from bringing the egg back
        if (!brain.distract(Distraction.FORCE))
            return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(nestCenter, mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier), 1)) ?: return ObjectiveStatus.ACTIVE
        fixation = brain.fixate(ReturnEggFixation(this, nestPos, tracker, LookingTracker.fromNavigationPathOrWalkTarget(entity, tracker.walkTarget)))
        return ObjectiveStatus.FIXATED
    }

    // place egg in the nest
    private fun placeEgg(entity: PokemonEntity, brain: BiggerBrain, world: ServerLevel, nestPos: BlockPos): Boolean {
        val nest = world.getBlockEntity(nestPos) as? NestBlockEntity ?: return false
        val heldEgg = entity.getItemInHand(InteractionHand.MAIN_HAND)

        // if for some reason the parent is not holding the egg anymore
        if (heldEgg.isEmpty || heldEgg.item != CobblemonItems.POKEMON_EGG) {
            brain.eraseMemory(CobblemonMemories.HOLDING_EGG)
            return false
        }

        // don't place the egg if the nest is not empty
        if (!nest.getItem().isEmpty)
            return false

        nest.setItem(heldEgg.copy())
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        brain.eraseMemory(CobblemonMemories.HOLDING_EGG)
        brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
        return true
    }

    private fun chooseRandomPos(entity: PokemonEntity, center: Vec3): Vec3? {
        // is this bad for performance? At low numbers I assume it won't be too bad but will need to be tested
        repeat(SEARCH_ATTEMPTS) {
            val offsetX = entity.random.nextDouble() * 2 * searchRadius - searchRadius
            val offsetZ = entity.random.nextDouble() * 2 * searchRadius - searchRadius
            val blockPos = BlockPos.containing(center.add(offsetX, 0.0, offsetZ))
            if (entity.navigation.createPath(blockPos, 0) != null)
                return Vec3.atCenterOf(blockPos)
        }
        return null
    }

    // fixation for walking to the egg and picking it up
    class FetchEggFixation(
        private val objective: EggRecovery,
        override val movementTracker: MovementTracker,
        override val lookingTracker: LookingTracker
    ) : EntityFixation, MovementTrackerFixation, LookingTrackerFixation {
        override fun getDescription(entity: LivingEntity) = "Egg Recovery: going to get egg"

        // if they are hurt or forced they should stop being fixated
        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isDamage || distraction.isForce

        // we should stop the moving tracker when distracted (do we need to stop anything else?)
        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity)
                return false

            val world = entity.level() as? ServerLevel ?: return false

            lookingTracker.tick()
            when (movementTracker.tick()) {
                MovementStatus.PENDING -> return true
                // movement is done (hopefully at the right spot) so try to pickup the egg
                MovementStatus.COMPLETED -> objective.pickUpEgg(entity, brain, world)
                // if anything happens try to get a new egg location
                MovementStatus.FAILED -> brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            }
            return false
        }
    }

    // path back to nest and place the egg
    class ReturnEggFixation(
        private val objective: EggRecovery,
        private val nestPos: BlockPos,
        override val movementTracker: MovementTracker,
        override val lookingTracker: LookingTracker
    ) : EntityFixation, MovementTrackerFixation, LookingTrackerFixation, LockFixation {
        override fun getDescription(entity: LivingEntity) = "Egg Recovery: taking egg back to nest"

        // only force for now but we might want to distract if damaged later
        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isForce

        // same as above stop movement if distracted
        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            movementTracker.stop()
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity)
                return false

            val world = entity.level() as? ServerLevel ?: return false

            lookingTracker.tick()
            if (movementTracker.tick() == MovementStatus.PENDING)
                return true
            if (movementTracker.lastStatus == MovementStatus.COMPLETED)
                objective.placeEgg(entity, brain, world, nestPos)
            return false
        }
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("2.0".asExpression())
        val searchRadius: ExpressionOrEntityVariable = Either.left("20".asExpression())
        val searchIntervalTicks: ExpressionOrEntityVariable = Either.left("20".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> {
            return listOf(speedMultiplier, searchRadius, searchIntervalTicks).asVariables()
        }

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            behaviourConfigurationContext.addMemories(
                CobblemonMemories.PATERNAL_NEST_LOCATION,
                CobblemonMemories.EGG_IN_NEST,
                CobblemonMemories.EGG_LOCATION,
                CobblemonMemories.EGG_THIEF,
                CobblemonMemories.EGG_SEARCH_COUNTDOWN,
                CobblemonMemories.HOLDING_EGG,
                CobblemonMemories.POTENTIAL_MATE
            )
            return EggRecovery(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                searchRadius = searchRadius.resolveVariableInt(behaviourConfigurationContext.runtime),
                searchIntervalTicks = searchIntervalTicks.resolveVariableInt(behaviourConfigurationContext.runtime)
            )
        }
    }

    companion object {
        private const val CLOSE_ENOUGH_DISTANCE = 1.5 // should this be bigger for the bigger mons or be based off of hitbox?
        private const val PICKUP_REACH = 1.5 // slight radius around the mon so they don't have to physically be on top of the egg
        private const val SEARCH_ATTEMPTS = 10
        private const val GIVE_UP_SEARCH_TIME = 20L // is this enough time for search? Needs playtesting
    }
}