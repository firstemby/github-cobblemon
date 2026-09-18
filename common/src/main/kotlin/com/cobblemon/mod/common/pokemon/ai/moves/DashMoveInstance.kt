/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3


class DashMoveInstance(val dashMove: DashMove, pokemonEntity: PokemonEntity, targetEntity: LivingEntity) :
    RealTimeMoveInstance(move = dashMove, pokemonEntity = pokemonEntity, targetEntity) {

    var durationTicks = 0
    var hitCount = 0
    var startPos = Vec3.ZERO

    override fun start() {
        pokemonEntity.navigation.stop()
       startPos = pokemonEntity.position()
    }


    override fun tick(): Boolean {

        if (pokemonEntity.isDeadOrDying || pokemonEntity.isBusy) return false

        if (delayTicks > 0) {
            delayTicks--
            return true
        }

        if (durationTicks == 0) {
            val vector = targetEntity.position().subtract(pokemonEntity.position()).normalize()
            val destination = vector.scale(dashMove.distance.toDouble()).add(pokemonEntity.position())
            pokemonEntity.navigation.moveTo(destination.x, destination.y, destination.z, dashMove.speed.toDouble())
        }

        // TODO: very bad way of testing this
        if (durationTicks < dashMove.durationTicks || pokemonEntity.position().distanceTo(startPos) < dashMove.distance) {
            durationTicks++
            return  true
//            if (hitCount <= 0) {
//                // TODO: visualEffects lol
//                // Raycasts Center, top, bottom
//
//
////                if (collisionResult.type != HitResult.Type.BLOCK && pokemonEntity.doHurtTarget(targetEntity)) {
//                    // Dish out the damage
//                    // TODO: Respect minecraft status effect modifiers
//                    val damage = getDamageMultiplier(pokemonEntity, targetEntity, move.elementalType) * dashMove.power
//                    targetEntity.hurt(
//                        pokemonEntity.damageSources().mobAttack(pokemonEntity),
//                        damage
//                    )
//                    pokemonEntity.swing(InteractionHand.MAIN_HAND)
//                    // if hit succeeded, apply additional effect
//                    val runtime = mainThreadRuntime.withQueryValue("entity", pokemonEntity.asMostSpecificMoLangValue())
//                        .withQueryValue("target", targetEntity.asMostSpecificMoLangValue())
//                    runtime.execute(dashMove.effect)
//                    var attackTargetData = pokemonEntity.biggerBrain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
//                    if (attackTargetData != null) {
//                        attackTargetData.hitsLanded += 1
//
//                    } else {
//                        attackTargetData = CobblemonAttackTargetData()
//                        attackTargetData.hitsLanded = 1
//                    }
//                    pokemonEntity.biggerBrain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
//                    // Successful strike
//                    hitCount++
//                }
//            }
        }



        if (recoveryTicks > 0) {
            recoveryTicks--
            return true
        }

        return false
    }

    override fun end() {

    }

}
