package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity


class RayCastMoveInstance(val rayCastMove: RayCastMove, pokemonEntity: PokemonEntity, targetEntity: LivingEntity) :
    RealTimeMoveInstance(move = rayCastMove, pokemonEntity = pokemonEntity, targetEntity) {

    var durationTicks = rayCastMove.durationTicks

    override fun start(
        pokemonEntity: PokemonEntity,
        targetEntity: LivingEntity
    ) {
        TODO("Not yet implemented")
    }


    override fun tick(pokemonEntity: PokemonEntity, targetEntity: LivingEntity): Boolean {

        if (delayTicks > 0) {
            delayTicks--
            return true
        }

        if (durationTicks > 0) {
            // Dish out the damage
            // TODO: Do damage calculation based on the Pokemon's stats * power of the attack
            //      Probably override doHurtTarget in PokemonEntity

            // TODO: raycasts
            // TODO: visualEffects lol
            if (pokemonEntity.doHurtTarget(targetEntity)) {
                pokemonEntity.swing(InteractionHand.MAIN_HAND)
                // if hit succeeded, apply additional effect
                val runtime = mainThreadRuntime.withQueryValue("entity", pokemonEntity.asMostSpecificMoLangValue())
                    .withQueryValue("target", targetEntity.asMostSpecificMoLangValue())
                runtime.execute(rayCastMove.effect)
                var attackTargetData = pokemonEntity.biggerBrain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                if (attackTargetData != null) {
                    attackTargetData.hitsLanded += 1

                } else {
                    attackTargetData = CobblemonAttackTargetData()
                    attackTargetData.hitsLanded = 1
                }
                pokemonEntity.biggerBrain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
            }

            durationTicks--
        }



        if (recoveryTicks > 0) {
            recoveryTicks--
            return true
        }

        return false
    }

    override fun end(pokemonEntity: PokemonEntity, targetEntity: LivingEntity) {

    }

}
