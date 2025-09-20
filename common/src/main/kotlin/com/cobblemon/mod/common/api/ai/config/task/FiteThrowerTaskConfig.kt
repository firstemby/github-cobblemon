package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.config.task.EatGrassTaskConfig.Companion.EATING_GRASS
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.FiteMeSoyBoyTask
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl

class FiteThrowerTaskConfig : SingleTaskConfig {
    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) {
            return null
        }

        behaviourConfigurationContext.addMemories(CobblemonMemories.RECENT_CATCH_ATTEMPT)

        return WrapperLivingEntityTask(
            FiteMeSoyBoyTask(),
            PokemonEntity::class.java,
        )
    }

    override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> = emptyList()
}