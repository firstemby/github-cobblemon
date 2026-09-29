/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.DurationLockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveInt as resolveVariableInt
import com.cobblemon.mod.common.api.breeding.CobblemonBreeding
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.block.WildNestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3

class Breed(
    private val speedMultiplier: Expression = "0.4".asExpression(),
    private val condition: Expression = "true".asExpression(),
    private val layDurationTicks: Int = 40,
    private val meetDurationTicks: Int = 100
) : Objective {
    private var fixation: EntityFixation? = null
    private var mateFixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Breed"

    override fun perform(entity: LivingEntity, brain: BiggerBrain, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive)
            return ObjectiveStatus.INVALID
        val world = entity.level() as? ServerLevel ?: return ObjectiveStatus.INVALID
        val mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE) as? PokemonEntity ?: return ObjectiveStatus.INACTIVE
        if (!mate.isAlive || mate.level() != world)
            return ObjectiveStatus.INACTIVE
        // ignore wild pokemon who are already parents of a nest
        if (entity.pokemon.isWild() && brain.hasMemoryValue(CobblemonMemories.PATERNAL_NEST_LOCATION))
            return ObjectiveStatus.INACTIVE
        // make sure either parent doesn't have an active breeding cooldown (do we want to only do this for the females?)
        if (entity.pokemon.breedingCooldown != 0 || mate.pokemon.breedingCooldown != 0)
            return ObjectiveStatus.INACTIVE
        // is breeding disabled from any preset conditions
        if (!mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveBoolean(condition))
            return ObjectiveStatus.INACTIVE
        // mates need to meet together briefly for a bit (kind of like vanilla mobs)
        if (!brain.hasMemoryValue(CobblemonMemories.MATES_MET) || !mate.biggerBrain.hasMemoryValue(CobblemonMemories.MATES_MET))
            return meetMate(entity, brain, mate)
        // mother (or ditto) goes to lay the egg and the other mate tries to follow along the best they can
        if (EggGroup.DITTO !in entity.pokemon.form.eggGroups && (EggGroup.DITTO in mate.pokemon.form.eggGroups || CobblemonBreeding.getMother(entity.pokemon, mate.pokemon, isSimulated = true) != entity.pokemon))
            return followMate(entity, brain, mate)

        val nestPos = brain.getMemory(CobblemonMemories.NEST_LOCATION)
        if (nestPos != null)
            return breedAtNest(entity, brain, mate, world, nestPos)
        val idealNestPos = brain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION) ?: return ObjectiveStatus.INACTIVE
        return buildNest(entity, brain, mate, world, idealNestPos)
    }

    // get both parents to go to the nest and breed once within range
    private fun breedAtNest(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity, world: ServerLevel, nestPos: BlockPos): ObjectiveStatus {
        val nest = world.getBlockEntity(nestPos) as? NestBlockEntity ?: return ObjectiveStatus.INACTIVE
        if (!canUseNest(entity, mate, world, nest))
            return ObjectiveStatus.INACTIVE
        if (!nest.getItem().isEmpty)
            return ObjectiveStatus.INACTIVE
        // make sure these lovers claim the nest because others may try to at the same time
        nest.nestClaimedBy = Pair(entity.uuid, mate.uuid)

        val nestCenter = Vec3.atCenterOf(nestPos)
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        // path to the nest site until close enough (big bois had issues so we are also going to try to make it easier for them)
        if (entity.position().distanceTo(nestCenter) > closeEnough(entity)) {
            return walkTo(entity, brain, nestCenter, speed, "in Breed fixation: going to nest")
        }
        return layEgg(entity, brain, mate, world, nest, nestPos)
    }

    // have both mates go to a ideal spot to make a nest
    private fun buildNest(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity, world: ServerLevel, nestPos: BlockPos): ObjectiveStatus {
        // probably should stop trying to build nest if there is an attack target
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
            return ObjectiveStatus.INACTIVE
        // find out what kinds of nests the mother makes
        val types = WildNestTypes.typesFor((CobblemonBreeding.getMother(entity.pokemon, mate.pokemon, isSimulated = true) ?: entity.pokemon).species)
        if (types.isEmpty())
            return ObjectiveStatus.INACTIVE

        val nestCenter = Vec3.atCenterOf(nestPos)
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        // keep going until the mother is not too far from the nest
        if (entity.position().distanceTo(nestCenter) > closeEnough(entity)) {
            return walkTo(entity, brain, nestCenter, speed, "in Breed fixation: going to nest")
        }

        // get our nest they are about to build and place it
        val nestToBuild = WildNestTypes.resolvePlacement(types, world.getBlockState(nestPos.below()), world.getFluidState(nestPos)) ?: return ObjectiveStatus.INACTIVE
        val nestBlock = WildNestTypes.blockFor(nestToBuild.type) as? WildNestBlock ?: return ObjectiveStatus.INACTIVE
        var nestState = nestBlock.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, nestToBuild.waterlogged)
        nestBlock.variantProperty?.let { nestState = nestState.setValue(it, nestToBuild.variant) }
        world.setBlock(nestPos, nestState, 3)

        // set the parents of the nest (hope this isn't too early to do this, might need to be after egg laying instead..)
        (world.getBlockEntity(nestPos) as? NestBlockEntity)?.nestClaimedBy = Pair(entity.uuid, mate.uuid)

        for (parentBrain in listOf(brain, mate.biggerBrain)) {
            parentBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            // give our newly made nest to the parents now taht it exists
            parentBrain.setMemory(CobblemonMemories.NEST_LOCATION, nestPos)
        }
        return ObjectiveStatus.ACTIVE
    }

    // make da baby and tell the parents about their new responsibility in life
    private fun layEgg(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity, world: ServerLevel, nest: NestBlockEntity, nestPos: BlockPos): ObjectiveStatus {
        if (!brain.distract(Distraction.FORCE))
            return ObjectiveStatus.BLOCKED
        val egg = CobblemonBreeding.createEgg(entity.pokemon, mate.pokemon) ?: return ObjectiveStatus.INACTIVE
        nest.setItem(egg)
        // if a mate (other than the egg layer) cannot get to a nest then it should be a deadbeat lol
        val mateCanReach = mate.position().distanceTo(Vec3.atCenterOf(nestPos)) <= closeEnough(mate) || mate.navigation.createPath(nestPos, 0)?.let { it.distToTarget < 1.75f } == true

        // wild nests are probably the only ones where we need to record the parents onto the nest
        if (nest.ownerUUID == null) {
            nest.parents = Pair(entity.uuid, if (mateCanReach) mate.uuid else null)
            nest.nestClaimedBy = Pair(entity.uuid, mate.uuid)
        }

        for (parent in listOf(entity, mate)) {
            // might want to do the destiny knot damaging here for each parent that has one
            if (Cobblemon.config.canDestinyKnotBreak)
                if (parent.pokemon.heldItem.item == CobblemonItems.DESTINY_KNOT.asItem()) {
                    parent.pokemon.heldItemNoCopy().damageValue += 1
                }

            parent.pokemon.breedingCooldown = CobblemonBreeding.resetBreedingCooldown(parent.pokemon)
            if (parent == entity || mateCanReach) {
                parent.biggerBrain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, nestPos)
                parent.biggerBrain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
            }
            parent.biggerBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)
            parent.biggerBrain.eraseMemory(CobblemonMemories.MATES_MET)

            // todo do we want to use our own particles? look at this later
            world.sendParticles(
                ParticleTypes.HEART,
                parent.getRandomX(1.0),
                parent.getRandomY() + 0.5,
                parent.getRandomZ(1.0),
                4 + parent.random.nextInt(2),
                0.2 * parent.random.nextGaussian(),
                0.1 * parent.random.nextGaussian(),
                0.2 * parent.random.nextGaussian(),
                0.02
            )
        }
        // try to keep them in one spot for a bit to give time for the magic to happen
        fixation = brain.fixate(DurationLockFixation("Breed: laying egg", layDurationTicks))
        if (mate.biggerBrain.distract(Distraction.FORCE)) {
            mateFixation = mate.biggerBrain.fixate(DurationLockFixation("Breed: laying egg as the mate", layDurationTicks))
        }
        return ObjectiveStatus.FIXATED
    }

    // walk over to the mate and stay put for a lil bit
    private fun meetMate(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity): ObjectiveStatus {
        // stay active while the other mate shows up
        if (brain.hasMemoryValue(CobblemonMemories.MATES_MET))
            return ObjectiveStatus.ACTIVE
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        if (entity.distanceTo(mate) > closeEnough(entity) + mate.bbWidth / 2)
            return walkTo(entity, brain, mate.position(), speed, "in Breed fixation: going to mate")
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        brain.setMemory(CobblemonMemories.MATES_MET, true, MATES_MET_TICKS)
        fixation = brain.fixate(DurationLockFixation("Breed: staying with mate", meetDurationTicks))
        return ObjectiveStatus.FIXATED
    }

    // other mate doess their best to follow along
    private fun followMate(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity): ObjectiveStatus {
        val nestPos = mate.biggerBrain.getMemory(CobblemonMemories.NEST_LOCATION) ?: mate.biggerBrain.getMemory(CobblemonMemories.IDEAL_NEST_LOCATION) ?: return ObjectiveStatus.INACTIVE
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        return walkTo(entity, brain, Vec3.atCenterOf(nestPos), speed, "in Breed fixation: following mate to nest")
    }

    // path to target location if able to
    private fun walkTo(entity: PokemonEntity, brain: BiggerBrain, target: Vec3, speed: Float, description: String): ObjectiveStatus {
        if (brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        if (entity.position().distanceTo(target) <= closeEnough(entity))
            return ObjectiveStatus.ACTIVE
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(target, speed, closeEnough(entity).toInt())) ?: return ObjectiveStatus.ACTIVE
        fixation = brain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE),
                description = description,
                lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(entity, tracker.walkTarget),
                movementTracker = tracker
            )
        )
        return ObjectiveStatus.FIXATED
    }

    // checks for what nests are valid for the mates
    private fun canUseNest(entity: PokemonEntity, mate: PokemonEntity, world: ServerLevel, nest: NestBlockEntity): Boolean {
        val nestType = (nest.blockState.block as? WildNestBlock)?.type ?: return false
        if (!isNestTypeAllowed(nestType, entity, mate))
            return false
        // the main restricter is if a nest is waterlogged so make sure we can check for that
        val waterlogged = nest.blockState.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)
        if (waterlogged && "watery" !in (CobblemonBreeding.getMother(entity.pokemon, mate.pokemon, isSimulated = true) ?: entity.pokemon).species.nestTypes)
            return false

        // check if the nest is claimed by other pokemon that exist and maybe on their way to it (CRAB NOTE: This helps prevent swarms of mons going to the same nest which looked weird)
        val (existingParent1, existingParent2) = nest.nestClaimedBy
        val claimedByMate1 = existingParent1 != null && existingParent1 != entity.uuid && world.getEntity(existingParent1) != null
        val claimedByMate2 = existingParent2 != null && existingParent2 != entity.uuid && world.getEntity(existingParent2) != null
        if (claimedByMate1 && claimedByMate2)
            return false
        return true
    }

    // make sure the nest type works for the mates and that wild use wild nests and owned pokemon use player placed nests
    private fun isNestTypeAllowed(nestType: WildNestType, entity: PokemonEntity, mate: PokemonEntity): Boolean {
        val allowed = WildNestTypes.typesFor((CobblemonBreeding.getMother(entity.pokemon, mate.pokemon, isSimulated = true) ?: entity.pokemon).species).toMutableSet()
        if (!entity.pokemon.isWild() && !mate.pokemon.isWild()) {
            allowed.add(WildNestType.PLAYER)
            allowed.add(WildNestType.KELP)
        }
        if (nestType == WildNestType.PLAYER || nestType == WildNestType.KELP)
            return !entity.pokemon.isWild() && nestType in allowed
        return nestType in allowed
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.4".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val layDurationTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())
        val meetDurationTicks: ExpressionOrEntityVariable = Either.left("100".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> {
            return listOf(speedMultiplier, condition, layDurationTicks, meetDurationTicks).asVariables()
        }

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            behaviourConfigurationContext.addMemories(
                CobblemonMemories.POTENTIAL_MATE,
                CobblemonMemories.NEST_LOCATION,
                CobblemonMemories.PATERNAL_NEST_LOCATION,
                CobblemonMemories.IDEAL_NEST_LOCATION,
                CobblemonMemories.EGG_IN_NEST,
                CobblemonMemories.MATES_MET,
                MemoryModuleType.ATTACK_TARGET
            )
            return Breed(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                condition = condition.asSimplifiedExpression(entity),
                layDurationTicks = layDurationTicks.resolveVariableInt(behaviourConfigurationContext.runtime),
                meetDurationTicks = meetDurationTicks.resolveVariableInt(behaviourConfigurationContext.runtime)
            )
        }
    }


    // lets help those with more cushion for the pushing reach those nests to breed
    private fun closeEnough(entity: PokemonEntity) = CLOSE_ENOUGH_DISTANCE + entity.bbWidth / 2

    companion object {
        private const val MATES_MET_TICKS = 2400L
        private const val CLOSE_ENOUGH_DISTANCE = 1.5 // we will have this be our baseline and then scale based on hitbox (like above) for the bigger mons
    }
}