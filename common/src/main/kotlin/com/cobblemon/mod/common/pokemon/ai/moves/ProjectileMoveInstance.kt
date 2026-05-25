package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.GenericHurtingProjectile

class ProjectileMoveInstance(val projectileMove: ProjectileMove, pokemonEntity: PokemonEntity, targetEntity: LivingEntity) :
    RealTimeMoveInstance(move = projectileMove, pokemonEntity = pokemonEntity, targetEntity) {
    var salvosRemaining = projectileMove.salvoCount
    var salvoCooldownTicks = 0

    override fun start(
        pokemonEntity: PokemonEntity,
        targetEntity: LivingEntity
    ) {
        TODO("Not yet implemented")
    }

    override fun tick(pokemonEntity: PokemonEntity, targetEntity: LivingEntity) : Boolean {

        if (delayTicks > 0) {
            delayTicks--
            return true
        }

        if (salvosRemaining > 0) {
            if (salvoCooldownTicks <= 0) {
                createProjectiles()
                salvosRemaining--
                salvoCooldownTicks = projectileMove.salvoDelayTicks
            } else {
                salvoCooldownTicks--
            }
            return true
        }


        if (recoveryTicks > 0) {
            recoveryTicks--
            return true
        }

        return false
    }

    override fun end(pokemonEntity: PokemonEntity, targetEntity: LivingEntity) {
        // TODO: add this as an "on hit" post event on the project
        var attackTargetData = pokemonEntity.biggerBrain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
        if (attackTargetData != null) {
            attackTargetData.hitsLanded += 1

        } else {
            attackTargetData = CobblemonAttackTargetData()
            attackTargetData.hitsLanded = 1
        }
        pokemonEntity.biggerBrain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
    }


    fun createProjectiles() {

        for (i in 1..projectileMove.shotsPerSalvo) {
            var velocity = targetEntity.eyePosition.subtract(pokemonEntity.eyePosition).normalize()
            if (projectileMove.spreadDegrees > 0) {
                velocity = getRandomizedVector(velocity, projectileMove.spreadDegrees)
            }
            velocity = velocity.scale(projectileMove.speed.toDouble())

            val projectile = GenericHurtingProjectile(
                pokemonEntity.level(),
                pokemonEntity,
                velocity,
                hasGravity = projectileMove.hasGravity,
                move.power * 1.0f,
                move.effect
            )
            projectile.setPos(pokemonEntity.eyePosition)
            pokemonEntity.level().addFreshEntity(projectile)

        }

    }

}
