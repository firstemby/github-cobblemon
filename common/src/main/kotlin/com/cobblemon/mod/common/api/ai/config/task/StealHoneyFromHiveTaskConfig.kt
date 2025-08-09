package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.StealHoneyFromHiveTask
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

class StealHoneyFromHiveTaskConfig : SingleTaskConfig {
    companion object { const val STEAL = "steal" }
    private val condition = booleanVariable(STEAL, "can_steal_honey", true).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(condition).asVariables()

    override fun createTask(entity: LivingEntity, ctx: BehaviourConfigurationContext): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) return null
        if (!checkCondition(entity, condition)) return null
        return StealHoneyFromHiveTask.create()
    }
}