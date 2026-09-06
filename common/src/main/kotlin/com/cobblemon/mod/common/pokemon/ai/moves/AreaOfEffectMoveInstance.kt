/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.example.mod.entity.RealtimeMoveAreaEffect
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil


class AreaOfEffectMoveInstance(
    val areaOfEffectMove: AreaOfEffectMove,
    pokemonEntity: PokemonEntity,
    targetEntity: LivingEntity,
    parentEntity: Entity? = null,
) :
    RealTimeMoveInstance(move = areaOfEffectMove, pokemonEntity = pokemonEntity, targetEntity, parentEntity) {

    var durationTicks = 0
    var position = (parentEntity ?: pokemonEntity).position()

    override fun start() {
        if (this.parentEntity is PokemonEntity) {
            pokemonEntity.navigation.stop()
        }

    }

    override fun tick(): Boolean {
        if (delayTicks > 0) {
            delayTicks--
            return true
        }


        val newEntity = RealtimeMoveAreaEffect(
            this.pokemonEntity.level(),
            this
        )
        // set position
        var pos = if (areaOfEffectMove.targetOpponent) targetEntity.position() else parentEntity?.position()
            ?: pokemonEntity.position()
        val level = pokemonEntity.level()
        for(i in 1..5) { // Lift at most 5 blocks
            // Problem: position is buried inside a colliding block
            // Solution: lift the position out of the block
            val blockPos = BlockPos.containing(pos)
            val shape = level.getBlockState(blockPos)
                .getCollisionShape(level, blockPos)

            // no collsion in the block
            if (shape.isEmpty) break


            // test current position does not collide with the block
            val localY = pos.y - blockPos.y
            if (!shape.bounds().contains(
                    Vec3(
                        pos.x - blockPos.x,
                        localY,
                        pos.z - blockPos.z
                    )
                )
            ) break

            // Position collides so we Lift the current position up
            pos = Vec3(pos.x, blockPos.y + shape.max(Direction.Axis.Y) + 0.001, pos.z)

        }

        newEntity.setPos(pos)
        pokemonEntity.level().addFreshEntity(newEntity)


        if (recoveryTicks > 0) {
            recoveryTicks--
            return true
        }

        return false
    }


}
