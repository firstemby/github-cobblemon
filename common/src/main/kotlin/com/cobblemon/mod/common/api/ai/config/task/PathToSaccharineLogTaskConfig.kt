package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.CobblemonSensors
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PathToSaccharineLogTask
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

class PathToSaccharineLogTaskConfig : SingleTaskConfig {
    companion object { const val SLATHER = "slather" }
    private val condition = booleanVariable(SLATHER, "can_slather_honey", true).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(condition).asVariables()

    override fun createTask(entity: LivingEntity, ctx: BehaviourConfigurationContext): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) return null
        if (!checkCondition(entity, condition)) return null

        ctx.addMemories(
            CobblemonMemories.SACCHARINE_LOG_LOCATION,
            CobblemonMemories.HAS_HONEY
        )
        ctx.addSensors(CobblemonSensors.NEARBY_SACCHARINE_LOG)
        return PathToSaccharineLogTask.create()
    }
}