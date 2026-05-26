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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3


class RayCastMoveInstance(val rayCastMove: RayCastMove, pokemonEntity: PokemonEntity, targetEntity: LivingEntity) :
    RealTimeMoveInstance(move = rayCastMove, pokemonEntity = pokemonEntity, targetEntity) {

    var durationTicks = rayCastMove.durationTicks
    var hitCount = 0

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
            durationTicks--

            if (hitCount <= 0) {
                // TODO: visualEffects lol
                // Raycasts Center, top, bottom
                var collisionResult = pokemonEntity.level().clip(
                    ClipContext(
                        pokemonEntity.position().add(Vec3(0.0,pokemonEntity.bbHeight / 2.0,0.0)), // start position
                        Vec3(targetEntity.x, targetEntity.y + targetEntity.bbHeight / 2.0, targetEntity.z), // end position
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        pokemonEntity
                    )
                )
                if (collisionResult.type == HitResult.Type.BLOCK) {
                    collisionResult = pokemonEntity.level().clip(
                        ClipContext(
                            pokemonEntity.position().add(Vec3(0.0,pokemonEntity.bbHeight / 2.0,0.0)), // start position
                            Vec3(targetEntity.x, targetEntity.y + targetEntity.bbHeight, targetEntity.z), // end position
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            pokemonEntity
                        )
                    )
                }
                if (collisionResult.type == HitResult.Type.BLOCK) {
                    collisionResult = pokemonEntity.level().clip(
                        ClipContext(
                            pokemonEntity.position().add(Vec3(0.0,pokemonEntity.bbHeight / 2.0,0.0)), // start position
                            Vec3(targetEntity.x, pokemonEntity.bbHeight / 2.0, targetEntity.z), // end position
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            pokemonEntity
                        )
                    )
                }

                if (collisionResult.type != HitResult.Type.BLOCK && pokemonEntity.doHurtTarget(targetEntity)) {
                    // Dish out the damage
                    // TODO: Do damage calculation based on the Pokemon's stats * power of the attack
                    //      Probably override doHurtTarget in PokemonEntity
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
                    // Successful strike
                    hitCount++
                }
            }
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
