/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package com.cobblemon.mod.common.pokemon.ai.moves

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

abstract class RealTimeMoveInstance(val move: RealTimeMove, val pokemonEntity: PokemonEntity, val targetEntity: LivingEntity) {
    var delayTicks = move.delayTicks
    var recoveryTicks = move.recoveryTicks

    abstract fun start(pokemonEntity: PokemonEntity, targetEntity: LivingEntity)

    abstract fun tick(pokemonEntity: PokemonEntity, targetEntity: LivingEntity) : Boolean

    abstract fun end(pokemonEntity: PokemonEntity, targetEntity: LivingEntity)

    fun getRandomizedVector(
        direction: Vec3,
        degrees: Float
    ): Vec3 {

        val dir = direction.normalize()

        val helper =
            if (abs(dir.y) < 0.99)
                Vec3(0.0, 1.0, 0.0)
            else
                Vec3(1.0, 0.0, 0.0)

        val right = dir.cross(helper).normalize()
        val up = dir.cross(right).normalize()

        val theta = Math.random() * Math.PI * 2.0

        val axis = right.scale(cos(theta))
            .add(up.scale(sin(theta)))
            .normalize()

        val quat = Quaternionf()
            .rotateAxis(
                Math.toRadians(Math.random() * degrees).toFloat(),
                axis.x.toFloat(),
                axis.y.toFloat(),
                axis.z.toFloat()
            )

        val vec = Vector3f(
            dir.x.toFloat(),
            dir.y.toFloat(),
            dir.z.toFloat()
        )

        vec.rotate(quat)

        return Vec3(
            vec.x.toDouble(),
            vec.y.toDouble(),
            vec.z.toDouble()
        )
    }
}
