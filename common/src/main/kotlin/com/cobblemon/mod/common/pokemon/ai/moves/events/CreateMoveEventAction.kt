/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves.events

import com.cobblemon.mod.common.CobblemonRealTimeMoves
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

class CreateMoveEventAction(
    val moveId: ResourceLocation = ResourceLocation("cobblemon", "swift"),
    val rotationDegrees: Vec3 = Vec3.ZERO,
    val positionOffset: Vec3 = Vec3.ZERO,
) : MoveEventAction() {


    override fun execute(owner: PokemonEntity, target: LivingEntity, parentEntity: Entity) {
        val realTimeMove = CobblemonRealTimeMoves.realTimeMoves[moveId]
            ?: throw IllegalArgumentException("Missing realTimeMove. A move has an event with an unresolvable realTimeMove identifier: $moveId")
        val moveInstance = realTimeMove.createInstance(owner, target, parentEntity)
        // Set Position and orientation
        moveInstance.positionOffset = positionOffset

        var dir: Vec3 = parentEntity.lookAngle.normalize()
        dir = dir.yRot(Math.toRadians(rotationDegrees.x).toFloat())
        dir = dir.xRot(Math.toRadians(rotationDegrees.y).toFloat())
        moveInstance.rotation = dir

    }
}