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
    private val speedMultiplier: Expression = "2.0".asExpression(), // funny fast number so we see who is breeding
    private val condition: Expression = "true".asExpression(),
    private val layDurationTicks: Int = 40
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
        // try to get one parent to lead the other one and have the other one not do this stuff
        if (entity.uuid > mate.uuid)
            return ObjectiveStatus.INACTIVE

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
        // path to the nest site with mate until close enough
        if (entity.distanceToSqr(nestCenter) > ARRIVAL_DISTANCE_SQR || mate.distanceToSqr(nestCenter) > ARRIVAL_DISTANCE_SQR) {
            bringMate(mate, nestCenter, speed)
            return walkToNest(entity, brain, nestCenter, speed, "in Breed fixation: going to nest")
        }
        return layEgg(entity, brain, mate, world, nest, nestPos)
    }

    // have both mates go to a ideal spot to make a nest
    private fun buildNest(entity: PokemonEntity, brain: BiggerBrain, mate: PokemonEntity, world: ServerLevel, nestPos: BlockPos): ObjectiveStatus {
        // probably should stop trying to build nest if there is an attack target
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
            return ObjectiveStatus.INACTIVE
        // find out what kinds of nests this couple can make togethr
        val types = WildNestTypes.combinedTypes(entity, mate)
        if (types.isEmpty())
            return ObjectiveStatus.INACTIVE

        val nestCenter = Vec3.atCenterOf(nestPos)
        val speed = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue()).resolveFloat(speedMultiplier)
        // keep going until both mates are not too far from the nest
        if (entity.distanceToSqr(nestCenter) > ARRIVAL_DISTANCE_SQR || mate.distanceToSqr(nestCenter) > ARRIVAL_DISTANCE_SQR) {
            bringMate(mate, nestCenter, speed)
            return walkToNest(entity, brain, nestCenter, speed, "in Breed fixation: going to nest")
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

        // wild nests are probably the only ones where we need to record the parents onto the nest
        if (nest.ownerUUID == null) {
            nest.parents = Pair(entity.uuid, mate.uuid)
            nest.nestClaimedBy = Pair(entity.uuid, mate.uuid)
        }

        for (parent in listOf(entity, mate)) {
            // might want to do the destiny knot damaging here for each parent that has one
            if (Cobblemon.config.canDestinyKnotBreak)
                if (parent.pokemon.heldItem.item == CobblemonItems.DESTINY_KNOT.asItem()) {
                    parent.pokemon.heldItemNoCopy().damageValue += 1
                }

            parent.pokemon.breedingCooldown = CobblemonBreeding.resetBreedingCooldown(parent.pokemon)
            parent.biggerBrain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, nestPos)
            parent.biggerBrain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
            parent.biggerBrain.eraseMemory(CobblemonMemories.IDEAL_NEST_LOCATION)

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
        fixation = brain.fixate(DurationLockFixation("Breed: laying an egg", layDurationTicks))
        if (mate.biggerBrain.distract(Distraction.FORCE)) {
            mateFixation = mate.biggerBrain.fixate(DurationLockFixation("Breed: making egg", layDurationTicks))
        }
        return ObjectiveStatus.FIXATED
    }

    // path to the nest location
    private fun walkToNest(entity: PokemonEntity, brain: BiggerBrain, target: Vec3, speed: Float, description: String): ObjectiveStatus {
        if (brain.isFixation(fixation))
            return ObjectiveStatus.FIXATED
        // wait for mate to get here
        if (entity.distanceToSqr(target) <= ARRIVAL_DISTANCE_SQR)
            return ObjectiveStatus.ACTIVE
        if (!brain.distract(comfortDistraction))
            return ObjectiveStatus.BLOCKED
        val tracker = RegularMovementTracker.attempt(entity, WalkTarget(target, speed, 1)) ?: return ObjectiveStatus.ACTIVE
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

    // have the other mates be told where to go
    private fun bringMate(mate: PokemonEntity, target: Vec3, speed: Float) {
        val mateBrain = mate.biggerBrain
        if (mate.distanceToSqr(target) <= ARRIVAL_DISTANCE_SQR || mateBrain.isFixation(mateFixation))
            return
        if (!mateBrain.distract(comfortDistraction))
            return
        val tracker = RegularMovementTracker.attempt(mate, WalkTarget(target, speed, 1)) ?: return
        mateFixation = mateBrain.fixate(
            LookAndMoveFixation(
                distractionTypes = setOf(DistractionType.DAMAGE, DistractionType.FORCE),
                description = "Breed: folow mate",
                lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(mate, tracker.walkTarget),
                movementTracker = tracker
            )
        )
    }

    // checks for what nests are valid for the mates
    private fun canUseNest(entity: PokemonEntity, mate: PokemonEntity, world: ServerLevel, nest: NestBlockEntity): Boolean {
        val nestType = (nest.blockState.block as? WildNestBlock)?.type ?: return false
        if (!isNestTypeAllowed(nestType, entity, mate))
            return false
        val waterlogged = nest.blockState.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)
        if (!isWaterGroupCompatible(entity, mate, waterlogged))
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
        val allowed = WildNestTypes.combinedTypes(entity, mate).toMutableSet()
        if (!entity.pokemon.isWild() && !mate.pokemon.isWild())
            allowed.add(WildNestType.PLAYER)
        if (nestType == WildNestType.PLAYER)
            return !entity.pokemon.isWild() && WildNestType.PLAYER in allowed
        return nestType in allowed
    }

    // waterlogged nests only allow parents that both have a water egg group and land nests dont work for mates that only have water egg groups
    private fun isWaterGroupCompatible(entity: PokemonEntity, mate: PokemonEntity, nestWaterlogged: Boolean): Boolean {
        val mate1Groups = entity.pokemon.species.eggGroups.map { it.name.lowercase() }
        val mate2Groups = mate.pokemon.species.eggGroups.map { it.name.lowercase() }
        val mate1HasWaterGroup = mate1Groups.any { it in WATER_EGG_GROUPS }
        val mate2HasWaterGroup = mate2Groups.any { it in WATER_EGG_GROUPS }
        val mate1OnlyWaterGroup = mate1Groups.all { it in WATER_EGG_GROUPS }
        val mate2OnlyWaterGroup = mate2Groups.all { it in WATER_EGG_GROUPS }

        return if (nestWaterlogged) mate1HasWaterGroup && mate2HasWaterGroup else !(mate1OnlyWaterGroup || mate2OnlyWaterGroup)
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.6".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val layDurationTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> {
            return listOf(speedMultiplier, condition, layDurationTicks).asVariables()
        }

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            behaviourConfigurationContext.addMemories(
                CobblemonMemories.POTENTIAL_MATE,
                CobblemonMemories.NEST_LOCATION,
                CobblemonMemories.PATERNAL_NEST_LOCATION,
                CobblemonMemories.IDEAL_NEST_LOCATION,
                CobblemonMemories.EGG_IN_NEST,
                MemoryModuleType.ATTACK_TARGET
            )
            return Breed(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                condition = condition.asSimplifiedExpression(entity),
                layDurationTicks = layDurationTicks.resolveVariableInt(behaviourConfigurationContext.runtime)
            )
        }
    }

    companion object {
        private const val ARRIVAL_DISTANCE_SQR = 2.0 // should this be bigger or maybe based on mon hitbox for the large mons? maybe
        private val WATER_EGG_GROUPS = setOf("water_1", "water_2", "water_3")
    }
}