/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.blockPositionsAsListRounded
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.getString
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.writeString
import kotlin.math.absoluteValue
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes

class VehicleLandController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("land/vehicle")
    }

    var canJump = "true".asExpression()
        private set
    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set
    var speed = "0.3".asExpression()
        private set
    var driveFactor = "1.0".asExpression()
        private set
    var reverseDriveFactor = "0.25".asExpression()
        private set

    var minimumSpeedToTurn = "0.1".asExpression()
        private set
    var rotationSpeed = "45/20".asExpression()
        private set
    var lookYawLimit = "101".asExpression()
        private set

    @Transient
    override val key: ResourceLocation = KEY
    @Transient
    override val poseProvider: PoseProvider = PoseProvider(PoseType.STAND).with(PoseOption(PoseType.WALK) { it.deltaMovement.horizontalDistance() > 0.1 })
    @Transient
    override val condition: (PokemonEntity) -> Boolean = { entity ->
        // Are there any blocks under the mon that aren't air or fluid
        // Cant just check one block since some mons may be more than one block big
        // This should be changed so that the any predicate is only ran on blocks under the mon
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (entity.isInWater || entity.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        return getRuntime(entity).resolveFloat(speed)
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        val rotationDegrees = driver.xxa * getRuntime(entity).resolveFloat(this.rotationSpeed)
        return Vec2(driver.xRot, entity.yRot - rotationDegrees)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val runtime = getRuntime(entity)
        val driveFactor = runtime.resolveFloat(this.driveFactor)
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }
        if (driver.xxa != 0F && g.absoluteValue < runtime.resolveFloat(this.minimumSpeedToTurn)) {
            driver.xxa = 0F
        }
        val velocity = Vec3(0.0 /* Maybe do drifting here later */, 0.0, g.toDouble())

        return velocity
    }

    override fun updatePassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        driver.yRot += (entity.riding.deltaRotation.y)
        driver.setYHeadRot(driver.yHeadRot + (entity.riding.deltaRotation.y))
    }

    override fun clampPassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val f = Mth.wrapDegrees(driver.yRot - entity.yRot)
        val runtime = getRuntime(entity)
        val lookYawLimit = runtime.resolveFloat(this.lookYawLimit)
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = getRuntime(entity).resolveBoolean(canJump)

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val runtime = getRuntime(entity)
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.speed.getString())
        buffer.writeString(this.canJump.getString())
        buffer.writeString(this.jumpVector[0].getString())
        buffer.writeString(this.jumpVector[1].getString())
        buffer.writeString(this.jumpVector[2].getString())
        buffer.writeString(this.driveFactor.getString())
        buffer.writeString(this.reverseDriveFactor.getString())
        buffer.writeString(this.minimumSpeedToTurn.getString())
        buffer.writeString(this.rotationSpeed.getString())
        buffer.writeString(this.lookYawLimit.getString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        this.speed = buffer.readString().asExpression()
        this.canJump = buffer.readString().asExpression()
        this.jumpVector = listOf(
            buffer.readString().asExpression(),
            buffer.readString().asExpression(),
            buffer.readString().asExpression()
        )
        this.driveFactor = buffer.readString().asExpression()
        this.reverseDriveFactor = buffer.readString().asExpression()
        this.minimumSpeedToTurn = buffer.readString().asExpression()
        this.rotationSpeed = buffer.readString().asExpression()
        this.lookYawLimit = buffer.readString().asExpression()
    }

}