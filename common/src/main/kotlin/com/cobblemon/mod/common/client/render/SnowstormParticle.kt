/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials
import com.cobblemon.mod.common.api.snowstorm.UVDetails
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.particle.SnowstormParticleRenderState
import com.cobblemon.mod.common.util.math.geometry.transformDirection
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import kotlin.math.abs
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.SingleQuadParticle.Layer
import net.minecraft.client.Camera
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.AxisAngle4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.random.Random

class SnowstormParticle(
    val storm: ParticleStorm,
    world: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    initialVelocity: Vec3,
    //Usually, this will be the storms runtime. But sometimes particles need to have variables different from their emitter, so the VariableStruct will differ
    val runtime: MoLangRuntime,
    val matrixWrapper: MatrixWrapper,
    var invisible: Boolean = false,
) : SingleQuadParticle(world, x, y, z, initialVelocity.x, initialVelocity.y, initialVelocity.z, resolveSprite(storm.effect.particle.texture)) {
    companion object {
        const val MAXIMUM_DISTANCE_CHANGE_PER_TICK_FOR_FRICTION = 0.005

        private fun resolveSprite(texture: Identifier): TextureAtlasSprite {
            val textureAtlas = Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES) as TextureAtlas
            return textureAtlas.getSprite(texture)
        }
    }
    var angularVelocity = 0.0
    var colliding = false

    var texture = storm.effect.particle.texture

    var localX = x - storm.getX()
    var localY = y - storm.getY()
    var localZ = z - storm.getZ()
    var rotatedLocal = Vector3d(localX, localY, localZ)

    var prevLocalX = localX
    var prevLocalY = localY
    var prevLocalZ = localZ
    var prevRotatedLocal = Vector3d(localX, localY, localZ)

    val currentRotation = AxisAngle4d(0.0, 0.0, 1.0, 0.0)

    var oldAxisRotation = AxisAngle4d(0.0, 0.0, 1.0, 0.0)
    var axisRotation = AxisAngle4d(0.0, 0.0, 1.0, 0.0)


    val uvDetails = UVDetails()

    var viewDirection = Vec3.ZERO
    var originPos = Vec3(storm.getX(), storm.getY(), storm.getZ())

    fun getX() = x
    fun getY() = y
    fun getZ() = z

    fun getVelocityX() = xd
    fun getVelocityY() = yd
    fun getVelocityZ() = zd

    private fun applyRandoms() {
        //Will having these emitter randoms not equal the real emitter randoms be a problem?
        runtime.environment.setSimpleVariable("emitter_random_1", DoubleValue(Random.Default.nextDouble()))
        runtime.environment.setSimpleVariable("emitter_random_2", DoubleValue(Random.Default.nextDouble()))
        runtime.environment.setSimpleVariable("emitter_random_3", DoubleValue(Random.Default.nextDouble()))
        runtime.environment.setSimpleVariable("emitter_random_4", DoubleValue(Random.Default.nextDouble()))
    }

    init {
        setParticleSpeed(initialVelocity.x, initialVelocity.y, initialVelocity.z)
        roll = -storm.effect.particle.rotation.getInitialRotation(runtime).toFloat()
        oRoll = roll
        quadSize = (maxOf(
            runtime.resolveDouble(storm.effect.particle.sizeX),
            runtime.resolveDouble(storm.effect.particle.sizeY)
        ) / 1.5).toFloat()
        angularVelocity = storm.effect.particle.rotation.getInitialAngularVelocity(runtime)
        friction = 1F
        lifetime = (runtime.resolveDouble(storm.effect.particle.maxAge) * 20).toInt()
        storm.particles.add(this)
        gravity = 0F
        storm.effect.particle.creationEvents.forEach { it.trigger(storm, this) }
    }

    fun runExpirationEvents() {
        storm.effect.particle.expirationEvents.forEach { it.trigger(storm, this)}
    }

    override fun extract(renderState: QuadParticleRenderState, camera: Camera, tickDelta: Float) {
        val particleRenderState = extractParticleRenderState(camera, tickDelta) ?: return
        renderState.add(
            getLayer(),
            particleRenderState.x,
            particleRenderState.y,
            particleRenderState.z,
            particleRenderState.quaternionX,
            particleRenderState.quaternionY,
            particleRenderState.quaternionZ,
            particleRenderState.quaternionW,
            particleRenderState.xSize.coerceAtLeast(particleRenderState.ySize),
            particleRenderState.minU,
            particleRenderState.maxU,
            particleRenderState.minV,
            particleRenderState.maxV,
            particleRenderState.color,
            particleRenderState.light
        )
    }

    fun extract(renderState: SnowstormParticleRenderState, camera: Camera, tickDelta: Float) {
        val particleRenderState = extractParticleRenderState(camera, tickDelta) ?: return
        renderState.add(
            getLayer(),
            particleRenderState.x,
            particleRenderState.y,
            particleRenderState.z,
            particleRenderState.quaternionX,
            particleRenderState.quaternionY,
            particleRenderState.quaternionZ,
            particleRenderState.quaternionW,
            particleRenderState.xSize,
            particleRenderState.ySize,
            particleRenderState.minU,
            particleRenderState.maxU,
            particleRenderState.minV,
            particleRenderState.maxV,
            particleRenderState.color,
            particleRenderState.light
        )
    }

    private fun extractParticleRenderState(camera: Camera, tickDelta: Float): ExtractedParticleRenderState? {
        if (invisible) {
            return null
        }

        applyRandoms()
        setParticleAgeInRuntime()
        storm.effect.curves.forEach { it.apply(runtime) }
        runtime.execute(storm.effect.particle.renderExpressions)
        setSprite(resolveSprite(storm.effect.particle.texture))

        val colour = storm.getParticleColor() ?: storm.effect.particle.tinting.getTint(runtime)
        setColor(colour.x, colour.y, colour.z)
        setAlpha(colour.w)

        val cameraPosition = camera.position()
        val interpLocalX = Mth.lerp(tickDelta.toDouble(), prevLocalX, localX)
        val interpLocalY = Mth.lerp(tickDelta.toDouble(), prevLocalY, localY)
        val interpLocalZ = Mth.lerp(tickDelta.toDouble(), prevLocalZ, localZ)
        val pos = if (storm.effect.space.localRotation) {
            val interpRotation = Mth.lerp(tickDelta.toDouble(), 0.0, currentRotation.angle)
            val vec = Vector3d(interpLocalX, interpLocalY, interpLocalZ)
            oldAxisRotation.transform(vec)
            currentRotation.get(AxisAngle4d()).also { it.angle = interpRotation }.transform(vec)
        } else {
            Vector3d(interpLocalX, interpLocalY, interpLocalZ)
        }

        val x = (pos.x + originPos.x - cameraPosition.x()).toFloat()
        val y = (pos.y + originPos.y - cameraPosition.y()).toFloat()
        val z = (pos.z + originPos.z - cameraPosition.z()).toFloat()
        val quaternion = storm.effect.particle.cameraMode.getRotation(
            matrixWrapper = matrixWrapper,
            prevAngle = oRoll,
            angle = roll,
            deltaTicks = tickDelta,
            particlePosition = Vec3(this.x, this.y, this.z),
            cameraPosition = cameraPosition,
            cameraAngle = camera.rotation(),
            cameraYaw = camera.yRot(),
            cameraPitch = camera.xRot(),
            viewDirection = viewDirection
        )
        val xSize = runtime.resolveDouble(storm.effect.particle.sizeX).toFloat() / 1.5F
        val ySize = runtime.resolveDouble(storm.effect.particle.sizeY).toFloat() / 1.5F
        val uvs = storm.effect.particle.uvMode.get(runtime, age / 20.0, lifetime / 20.0, uvDetails)
        val sprite = resolveSprite(storm.effect.particle.texture)
        val spriteURange = sprite.u1 - sprite.u0
        val spriteVRange = sprite.v1 - sprite.v0
        val minU = uvs.startU * spriteURange + sprite.u0
        val maxU = uvs.endU * spriteURange + sprite.u0
        val minV = uvs.startV * spriteVRange + sprite.v0
        val maxV = uvs.endV * spriteVRange + sprite.v0
        val light = if (storm.effect.particle.environmentLighting) getLightCoords(tickDelta) else (15 shl 20 or (15 shl 4))

        return ExtractedParticleRenderState(
            x = x,
            y = y,
            z = z,
            quaternionX = quaternion.x,
            quaternionY = quaternion.y,
            quaternionZ = quaternion.z,
            quaternionW = quaternion.w,
            xSize = xSize,
            ySize = ySize,
            minU = minU,
            maxU = maxU,
            minV = minV,
            maxV = maxV,
            color = ARGB.colorFromFloat(colour.w, colour.x, colour.y, colour.z),
            light = light
        )
    }

    override fun tick() {
        if (storm.effect.space.localPosition) {
            originPos = matrixWrapper.getOrigin()
        }

        applyRandoms()
        setParticleAgeInRuntime()
        storm.effect.curves.forEach { it.apply(runtime) }
        runtime.execute(storm.effect.particle.updateExpressions)
        setSprite(resolveSprite(storm.effect.particle.texture))
        quadSize = (maxOf(
            runtime.resolveDouble(storm.effect.particle.sizeX),
            runtime.resolveDouble(storm.effect.particle.sizeY)
        ) / 1.5).toFloat()
        val colour = storm.getParticleColor() ?: storm.effect.particle.tinting.getTint(runtime)
        setColor(colour.x, colour.y, colour.z)
        setAlpha(if (invisible) 0F else colour.w)
        angularVelocity = storm.effect.particle.rotation.getAngularVelocity(runtime, -roll.toDouble(), angularVelocity) / 20

        if (age >= lifetime || runtime.resolveBoolean(storm.effect.particle.killExpression)) {
            runExpirationEvents()
            remove()
            return
        } else {
            val velocity = storm.effect.particle.motion.getVelocity(runtime, this,
                Vec3(xd, yd, zd)
            )
            xd = velocity.x
            yd = velocity.y
            zd = velocity.z
            oRoll = roll
            // Subtract because Bedrock particles are counter-clockwise and Java Edition is clockwise.
            roll = oRoll - angularVelocity.toFloat()
        }

        viewDirection = storm.effect.particle.viewDirection.getDirection(
            runtime = runtime,
            lastDirection = viewDirection,
            currentVelocity = Vec3(xd, yd, zd)
        ).normalize()

        xo = x
        yo = y
        zo = z

        prevLocalX = localX
        prevLocalY = localY
        prevLocalZ = localZ

        oldAxisRotation = axisRotation
        prevRotatedLocal = oldAxisRotation.transform(Vector3d(prevLocalX, prevLocalY, prevLocalZ))

        //This is a bit of a hack. Technically local rotation is supposed to make it so the particle uses
        //the emitter's rotation - but we've made it so the emitter doesn't actually follow the locators rotation, only position.
        //So instead of being bound to the emitter, the particle is actually bound to the locator for local rotation.
        //If you're messing with this - make sure evo particles still rotate with the pokemon
        if (storm.effect.space.localRotation) {
            storm.attachedMatrix.matrix.getRotation(axisRotation)
        }
        else {
            matrixWrapper.matrix.getRotation(axisRotation)
        }

        rotatedLocal = axisRotation.transform(Vector3d(prevLocalX, prevLocalY, prevLocalZ))
        Quaterniond().rotateTo(prevRotatedLocal, rotatedLocal).get(currentRotation)

        age++

        this.move(xd, yd, zd)

        storm.effect.particle.timeline.check(storm, this, (age - 1) / 20.0, age / 20.0)
    }

    override fun move(dx: Double, dy: Double, dz: Double) {
        val collision = storm.effect.particle.collision
        val radius = runtime.resolveDouble(collision.radius)
        boundingBox = AABB.ofSize(Vec3(x, y, z), radius, radius, radius)
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            updatePosition()
            expandBoundingBoxForCulling(radius)
            return
        }

        var dx = dx
        var dy = dy
        var dz = dz

        if (runtime.resolveBoolean(collision.enabled) && radius > 0.0 && !storm.effect.space.isLocalSpace) {
            hasPhysics = true

            val newMovement = checkCollision(Vec3(dx, dy, dz))

            if (removed) {
                return
            }

            dx = newMovement.x
            dy = newMovement.y
            dz = newMovement.z

//            if (collidesWithWorld && (dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < 10000) {
//                val vec3d = Entity.adjustMovementForCollisions(
//                    null,
//                    Vec3d(dx, dy, dz),
//                    boundingBox,
//                    world,
//                    listOf()
//                )
//
//            }

            if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
                boundingBox = boundingBox.move(dx, dy, dz)
                localX += dx
                localY += dy
                localZ += dz
            }

//            if (abs(dy) >= 9.999999747378752E-6 && abs(dy) < 9.999999747378752E-6) {
//                field_21507 = true
//            }
//            onGround = dy != dy && e < 0.0
//            if (d != dx) {
//                xd = 0.0
//            }
//            if (dz != dz) {
//                zd = 0.0
//            }
        } else {
            hasPhysics = false
            if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
                localX += dx
                localY += dy
                localZ += dz
            }
        }
        updatePosition()
        expandBoundingBoxForCulling(radius)
    }

    private fun expandBoundingBoxForCulling(collisionRadius: Double) {
        val visualSizeX = abs(runtime.resolveDouble(storm.effect.particle.sizeX))
        val visualSizeY = abs(runtime.resolveDouble(storm.effect.particle.sizeY))
        val visualExtent = maxOf(visualSizeX, visualSizeY)
        if (visualExtent > collisionRadius) {
            boundingBox = AABB.ofSize(Vec3(x, y, z), visualExtent, visualExtent, visualExtent)
        }
    }

    fun updatePosition() {
        val localVector = if (storm.effect.space.localRotation) storm.attachedMatrix.matrix.transformDirection(
            Vec3(
                localX,
                localY,
                localZ
            )
        ) else Vec3(localX, localY, localZ)
        x = localVector.x + originPos.x
        y = localVector.y + originPos.y
        z = localVector.z + originPos.z
    }

    private fun checkCollision(movement: Vec3): Vec3 {
        val collision = storm.effect.particle.collision
        var box = boundingBox
        val bounciness = runtime.resolveDouble(collision.bounciness)
        val friction = runtime.resolveDouble(collision.friction)
        val expiresOnContact = collision.expiresOnContact

        // Handle easy collision cases
        // If there are no collisions return the unchanged movement. If the particle expires on contact then remove it.
        val collisions = level.getBlockCollisions(null, box.expandTowards(movement))
        if (collisions.none()) {
            colliding = false
            return movement
        } else if (expiresOnContact) {
            runExpirationEvents()
            remove()
            return movement
        }

        // Initial particle movement and collision type flags
        var xMovement = movement.x
        var yMovement = movement.y
        var zMovement = movement.z
        var bouncing = false
        var sliding = false

        // Determine which horizontal axis to resolve first by favoring the larger movement
        val mostlyIsZMovement = abs(xMovement) < abs(zMovement)

        /******************************************************************************************************************************/
        /**
         * Helper function to resolve a collision along a given axis.
         */
        fun resolveAxis(
            axis: Direction.Axis,
            axisMovement: Double,
            originalVelocity: Double,
            collisions: Iterable<VoxelShape>
        ): Double {

            // Check for collision on the given axis. If there is no collision then move the box along the axis and return the value of the vector for that axis
            val resolved = Shapes.collide(axis, box, collisions, axisMovement)
            if (resolved != 0.0) {
                box = box.move(
                    if (axis == Direction.Axis.X) resolved else 0.0,
                    if (axis == Direction.Axis.Y) resolved else 0.0,
                    if (axis == Direction.Axis.Z) resolved else 0.0
                )
                return resolved
            }

            // If the particle is bouncy and is moving fast enough then "bounce" it on the given axis and return
            if (bounciness > 0.0 && abs(originalVelocity) > MAXIMUM_DISTANCE_CHANGE_PER_TICK_FOR_FRICTION) {
                val bounced = -1 * bounciness * originalVelocity
                when (axis) {
                    Direction.Axis.X -> xd = bounced
                    Direction.Axis.Y -> yd = bounced
                    Direction.Axis.Z -> zd = bounced
                }
                bouncing = true
                return bounced
            }

            // If the particle is instead slidey then mark the sliding flag for later slide/friction resolution
            if (friction > 0.0) sliding = true
            when (axis) {
                Direction.Axis.X -> xd = 0.0
                Direction.Axis.Y -> yd = 0.0
                Direction.Axis.Z -> zd = 0.0
            }
            return 0.0
        }
        /******************************************************************************************************************************/

        // Resolve y collision first, then horizontal with the axis that holds the largest portion of the movement coming first
        if (yMovement != 0.0)
            yMovement = resolveAxis(Direction.Axis.Y, yMovement, movement.y, collisions)
        if (mostlyIsZMovement) {
            if (zMovement != 0.0) zMovement = resolveAxis(Direction.Axis.Z, zMovement, movement.z, collisions)
            if (xMovement != 0.0) xMovement = resolveAxis(Direction.Axis.X, xMovement, movement.x, collisions)
        } else {
            if (xMovement != 0.0) xMovement = resolveAxis(Direction.Axis.X, xMovement, movement.x, collisions)
            if (zMovement != 0.0) zMovement = resolveAxis(Direction.Axis.Z, zMovement, movement.z, collisions)
        }

        // Grab our current projected movement for the tick and the particles current velocity
        var newMovement = Vec3(xMovement, yMovement, zMovement)
        var velocity = Vec3(xd, yd, zd)

        // If the particle is slidey and not bouncy then perform slide/friction resolution
        if (sliding && !bouncing) {

            // If it's moving slower than the friction per second, time to stop
            if (velocity.length() * 20 < friction) {
                setParticleSpeed(0.0, 0.0, 0.0)
                newMovement = Vec3.ZERO
            } else {
                // Slow down the particle due to friction
                val reduced = velocity.subtract(velocity.normalize().scale(friction / 20))
                setParticleSpeed(reduced.x, reduced.y, reduced.z)
                newMovement = reduced
            }
        }

        return newMovement
    }


    private fun setParticleAgeInRuntime() {
        runtime.environment.variable.setDirectly("particle_age", DoubleValue(age / 20.0))
        runtime.environment.variable.setDirectly("particle_lifetime", DoubleValue(lifetime / 20.0))
    }

    override fun getLayer(): Layer {
        return ParticleMaterials.layer(storm.effect.particle.material)
    }

    override fun getGroup(): ParticleRenderType {
        return if (invisible) ParticleRenderType.NO_RENDER else ParticleMaterials.group(storm.effect.particle.material)
    }

    override fun remove() {
        super.remove()
        storm.particles.remove(this)
    }

    private data class ExtractedParticleRenderState(
        val x: Float,
        val y: Float,
        val z: Float,
        val quaternionX: Float,
        val quaternionY: Float,
        val quaternionZ: Float,
        val quaternionW: Float,
        val xSize: Float,
        val ySize: Float,
        val minU: Float,
        val maxU: Float,
        val minV: Float,
        val maxV: Float,
        val color: Int,
        val light: Int
    )
}
