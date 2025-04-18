package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PathToIdealNestLocationTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PathToNestMaterialTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PathToNestTask
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class PathToIdealNestLocationTaskConfig : SingleTaskConfig {
    companion object {
        const val BREED = "breed" // Category
    }

    val condition = booleanVariable(BREED, "breeds", true).asExpressible()
    val speedMultiplier = numberVariable(BREED, "speed_multiplier", 0.6).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(
        condition,
        speedMultiplier
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        return WrapperLivingEntityTask(PathToIdealNestLocationTask.create(), PokemonEntity::class.java)
    }
}