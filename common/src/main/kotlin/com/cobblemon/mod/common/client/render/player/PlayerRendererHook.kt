package com.cobblemon.mod.common.client.render.player

import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.Rideable
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.ClientPlayerIcon.Companion.onRenderPlayer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.mixin.PlayerRendererMixin
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.math.geometry.transformPosition
import com.cobblemon.mod.common.util.toVec3d
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.AxisAngle4f
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f

object PlayerRendererHook {
    var maxPartialTicks = 0F
    const val disableRollableRenderDebug: Boolean = false
    fun before(
        player: AbstractClientPlayer,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        multiBufferSource: MultiBufferSource,
        i: Int
    ) {
        val shouldRoll = player is Rollable && player.shouldRoll()
        if (player.vehicle is Rideable) {
            //We want to ignore where vanilla is placing the player, so get rid of any transformations that have occured so far
            poseStack.setIdentity()
            //The actual rendering of the player model expects to be able to use coordinates relative to the camera's position
            //So all of our coordinates need to be offset by the camera position
            val cameraPos = Minecraft.getInstance().gameRenderer.mainCamera.position
            val vehicle = player.vehicle as Rideable
            val entity = player.vehicle!!
            //Get the seat
            val seatIndex = vehicle.riding.entity.passengers.indexOf(player)
            val seat = vehicle.riding.entity.seats[seatIndex]
            var playerPosition =  seat.offset
            //If we are riding a posable entity, use the locator position over the seat offset
            if (entity is PosableEntity) {
                val posableState = entity.delegate as PosableState
                val locator = posableState.locatorStates[seat.locator]!!
                val locatorOffset = locator.getOrigin().subtract(entity.position())
                val oldLocatorPos = locatorOffset.add(Vec3(entity.xOld, entity.yOld, entity.zOld))
                val curLocatorPos = oldLocatorPos.lerp(locator.getOrigin(), partialTicks.toDouble()).subtract(cameraPos)
                playerPosition = curLocatorPos
            }
            if (shouldRoll) {
                //Here, basically what we are doing is figuring out the coords of the rotated locator.
                //We have to do the same rotations that are done in the pokemon renderer
                val rollable = player as Rollable
                val pokemonCenter = Vec3(entity.xOld, entity.yOld, entity.zOld).lerp(entity.position(), partialTicks.toDouble()).add(0.0, entity.bbHeight / 2.0, 0.0).subtract(cameraPos)
                val transformationMatrix = Matrix4f()
                transformationMatrix.translate(pokemonCenter.toVector3f())
                transformationMatrix.rotate(180f.toRadians() - (rollable.yaw).toRadians(), rollable.upVector, transformationMatrix)
                transformationMatrix.rotate((-rollable.pitch).toRadians(), rollable.leftVector, transformationMatrix)
                transformationMatrix.rotate((-rollable.roll).toRadians(), rollable.forwardVector, transformationMatrix)
                transformationMatrix.translate(pokemonCenter.toVector3f().negate())

                playerPosition = transformationMatrix.transformPosition(playerPosition)
                //We need to move "down" half a block to account for the player crouching pose.
                playerPosition = playerPosition.subtract(Vector3f(rollable.upVector).mul(0.5F).toVec3d())
            }
            else {
                playerPosition = playerPosition.subtract(0.0, 0.5, 0.0)
            }
            poseStack.translate(playerPosition.x, playerPosition.y, playerPosition.z)

            if (shouldRoll) {
                val orientation = Matrix4f((player as Rollable).orientation ?: Matrix3f())
                val rotation = orientation.getRotation(AxisAngle4f())
                poseStack.mulPose(Quaternionf(rotation))
            }
        }
    }

    fun renderAxisLines(
        player: AbstractClientPlayer,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        multiBufferSource: MultiBufferSource,
        i: Int
    ) {
        if (player is Rollable) {
            val pose = poseStack.last().pose()
            val buffer = multiBufferSource.getBuffer(RenderType.lines())
            //"Forward" is red
            val forwardVector = player.forwardVector.mul(2F)
            buffer.addVertex(pose, 0F, 0F, 0F).setColor(1F, 0F, 0F, 1F).setNormal(1F, 1F, 1F)
            buffer.addVertex(pose, forwardVector.x, forwardVector.y, forwardVector.z).setColor(1F, 0F, 0F, 1F).setNormal(1F, 1F, 1F)
            //"Left" is blue
            val leftVector = player.leftVector.mul(2F)
            buffer.addVertex(pose, 0F, 0F, 0F).setColor(0F, 0F, 1F, 1F).setNormal(1F, 1F, 1F)
            buffer.addVertex(pose, leftVector.x, leftVector.y, leftVector.z).setColor(0F, 0F, 1F, 1F).setNormal(1F, 1F, 1F)
            //"Up" is green
            val upVector = player.upVector.mul(2F)
            buffer.addVertex(pose, 0F, 0F, 0F).setColor(0F, 1F, 0F, 1F).setNormal(1F, 1F, 1F)
            buffer.addVertex(pose, upVector.x, upVector.y, upVector.z).setColor(0F, 1F, 0F, 1F).setNormal(1F, 1F, 1F)
        }
    }
}