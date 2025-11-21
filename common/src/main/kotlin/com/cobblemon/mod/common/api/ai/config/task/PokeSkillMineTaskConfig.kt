package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask.Companion.wrapped
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.ai.PokeSkillMineTask
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class PokeSkillMineTaskConfig : SingleTaskConfig {
    override fun getVariables(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): List<MoLangConfigVariable> {
        return listOf()
    }

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) {
            return null
        }

        behaviourConfigurationContext.addMemories(
            CobblemonMemories.MINE_TARGET,
            MemoryModuleType.WALK_TARGET,
        )

        return PokeSkillMineTask().wrapped<PokemonEntity>()
    }
}