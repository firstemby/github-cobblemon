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
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.ModAPI
import com.cobblemon.mod.common.api.snowstorm.ParticleCameraModeType
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterial
import com.cobblemon.mod.common.api.snowstorm.ParticleMaterials
import com.cobblemon.mod.common.api.snowstorm.UVDetails
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.util.math.geometry.transformDirection
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import kotlin.math.abs
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.ParticleRenderType.NO_RENDER
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.AxisAngle4d
import org.joml.Quaterniond
import org.joml.Quaternionf
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
) : Particle(world, x, y, z) {
    companion object {
        const val MAXIMUM_DISTANCE_CHANGE_PER_TICK_FOR_FRICTION = 0.005
    }

    val sprite = getSpriteFromAtlas()

    val particleTextureSheet: ParticleRenderType
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

    fun getSpriteFromAtlas(): TextureAtlasSprite {
        val atlas = Minecraft.getInstance().particleEngine.textureAtlas
        val sprite = atlas.getSprite(storm.effect.particle.texture)
        return sprite
    }

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
        angularVelocity = storm.effect.particle.rotation.getInitialAngularVelocity(runtime)
        friction = 1F
        lifetime = (runtime.resolveDouble(storm.effect.particle.maxAge) * 20).toInt()
        storm.particles.add(this)
        gravity = 0F
        particleTextureSheet = if (invisible) {
            NO_RENDER
        } else {
            when (storm.effect.particle.material) {
                ParticleMaterial.ALPHA -> ParticleMaterials.ALPHA
                ParticleMaterial.OPAQUE -> ParticleMaterials.OPAQUE
                ParticleMaterial.BLEND -> ParticleMaterials.BLEND
                ParticleMaterial.ADD -> ParticleMaterials.ADD
            }
        }
        storm.effect.particle.creationEvents.forEach { it.trigger(storm, this) }
    }

    override fun render(vertexConsumer: VertexConsumer, camera: Camera, tickDelta: Float) {
        if (invisible) {
            return
        }

        if (Cobblemon.implementation.modAPI != ModAPI.FORGE) {
           if (!Minecraft.getInstance().levelRenderer.cullingFrustum.isVisible(boundingBox)) {
               return
           }
        }

        applyRandoms()
        setParticleAgeInRuntime()
        storm.effect.curves.forEach { it.apply(runtime) }
        runtime.execute(storm.effect.particle.renderExpressions)

        val vec3d = camera.position

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

        val f = (pos.x + originPos.x - vec3d.x()).toFloat()
        val g = (pos.y + originPos.y - vec3d.y()).toFloat()
        val h = (pos.z + originPos.z - vec3d.z()).toFloat()
        val quaternion = storm.effect.particle.cameraMode.getRotation(
            matrixWrapper = matrixWrapper,
            prevAngle = oRoll,
            angle = roll,
            deltaTicks = tickDelta,
            particlePosition = Vec3(x, y, z),
            cameraPosition = camera.position,
            cameraAngle = camera.rotation(),
            cameraYaw = camera.yRot,
            cameraPitch = camera.xRot,
            viewDirection = viewDirection
        )
        val xSize = runtime.resolveDouble(storm.effect.particle.sizeX).toFloat() / 1.5.toFloat()
        val ySize = runtime.resolveDouble(storm.effect.particle.sizeY).toFloat() / 1.5.toFloat()

        val particleVertices = arrayOf(
            Vector3f(xSize, -ySize, 0.0f),
            Vector3f(xSize, ySize, 0.0f),
            Vector3f(-xSize, ySize, 0.0f),
            Vector3f(-xSize, -ySize, 0.0f)
        )

        for (k in 0..3) {
            val vertex = particleVertices[k]
            vertex.rotate(quaternion)
            vertex.add(f, g, h)
        }

        val uvs = storm.effect.particle.uvMode.get(runtime, age / 20.0, lifetime / 20.0, uvDetails)
        val colour = storm.getParticleColor() ?: storm.effect.particle.tinting.getTint(runtime)

        val spriteURange = sprite.u1 - sprite.u0
        val spriteVRange = sprite.v1 - sprite.v0

        val minU = uvs.startU * spriteURange + sprite.u0
        val maxU = uvs.endU * spriteURange + sprite.u0
        val minV = uvs.startV * spriteVRange + sprite.v0
        val maxV = uvs.endV * spriteVRange + sprite.v0

        val p = if (storm.effect.particle.environmentLighting) getLightColor(tickDelta) else (15 shl 20 or (15 shl 4))
        vertexConsumer
            .addVertex(particleVertices[0].x, particleVertices[0].y, particleVertices[0].z)
            .setUv(maxU, maxV)
            .setColor(colour.x, colour.y, colour.z, colour.w)
            .setLight(p)
        vertexConsumer
            .addVertex(particleVertices[1].x, particleVertices[1].y, particleVertices[1].z)
            .setUv(maxU, minV)
            .setColor(colour.x, colour.y, colour.z, colour.w)
            .setLight(p)
        vertexConsumer
            .addVertex(particleVertices[2].x, particleVertices[2].y, particleVertices[2].z)
            .setUv(minU, minV)
            .setColor(colour.x, colour.y, colour.z, colour.w)
            .setLight(p)
        vertexConsumer
            .addVertex(particleVertices[3].x, particleVertices[3].y, particleVertices[3].z)
            .setUv(minU, maxV)
            .setColor(colour.x, colour.y, colour.z, colour.w)
            .setLight(p)
    }

    /**
     * Renders this particle into a simulated 3D scene inside a GUI [PoseStack] (the MoveDex move viewport),
     * faithfully reproducing the world render path: it billboards via the effect's real
     * [com.cobblemon.mod.common.api.snowstorm.ParticleCameraMode] instead of a hand-rolled screen-facing
     * quad, and reuses every other appearance input (size, UV/flipbook, tint, sprite, local rotation).
     *
     * In the world, particle quads are built in world axes oriented by [cameraAngle] (the camera's WORLD
     * rotation) and the modelview matrix (the camera VIEW rotation = inverse) flattens them to the screen.
     * Here, the caller's [poseStack] already applies the scene's view rotation, so [cameraAngle] must be
     * its INVERSE — then the two compose to a screen-facing quad exactly as in battle. Particle positions
     * are the local-to-emitter coordinates, placed by the same pose, so they share the dolls' 3D space.
     * Full-bright; the caller supplies a [VertexConsumer] bound to the particle atlas + matching blend.
     */
    fun renderInScene(
        poseStack: com.mojang.blaze3d.vertex.PoseStack,
        vertexConsumer: VertexConsumer,
        cameraAngle: Quaternionf,
        cameraYaw: Float,
        cameraPitch: Float,
        sceneOrigin: Vec3,
        anchorX: Float,
        anchorY: Float,
        anchorZ: Float,
        tickDelta: Float,
        beamEnd: FloatArray? = null
    ) {
        if (invisible) return

        applyRandoms()
        setParticleAgeInRuntime()
        storm.effect.curves.forEach { it.apply(runtime) }
        runtime.execute(storm.effect.particle.renderExpressions)

        // Use the particle's final WORLD position (which already bakes in local-rotation via updatePosition),
        // relative to the scene's single shared origin, plus the storm's scene-local Z anchor (which doll it
        // belongs to). This lets the scene batch every storm of a material into one draw without per-storm
        // pose pushes, while a storm and its event-spawned children still map into one consistent space.
        val px = (Mth.lerp(tickDelta.toDouble(), xo, x) - sceneOrigin.x).toFloat() + anchorX
        // Negate the vertical: the GUI scene pose's +Y points down (the doll model flips internally to stand
        // upright; particles have no such flip), so world-up must be negated here to render up. anchorY is the
        // upward locator height, so it's subtracted to lift the emit point.
        val py = (-(Mth.lerp(tickDelta.toDouble(), yo, y) - sceneOrigin.y) - anchorY).toFloat()
        val pz = (Mth.lerp(tickDelta.toDouble(), zo, z) - sceneOrigin.z + anchorZ).toFloat()

        // Synthesise a camera position far "behind" the particle along the view direction, so distance-based
        // camera modes (LOOK_AT_DIRECTION, DIRECTION_*) see a near-parallel view like a distant battle camera.
        val forward = Vector3f(0f, 0f, 1f).rotate(Quaternionf(cameraAngle))

        val xSize = runtime.resolveDouble(storm.effect.particle.sizeX).toFloat() / 1.5f
        val ySize = runtime.resolveDouble(storm.effect.particle.sizeY).toFloat() / 1.5f

        val uvs = storm.effect.particle.uvMode.get(runtime, age / 20.0, lifetime / 20.0, uvDetails)
        val colour = storm.getParticleColor() ?: storm.effect.particle.tinting.getTint(runtime)
        val spriteURange = sprite.u1 - sprite.u0
        val spriteVRange = sprite.v1 - sprite.v0
        val minU = uvs.startU * spriteURange + sprite.u0
        val maxU = uvs.endU * spriteURange + sprite.u0
        val minV = uvs.startV * spriteVRange + sprite.v0
        val maxV = uvs.endV * spriteVRange + sprite.v0
        val light = (15 shl 20) or (15 shl 4)
        val m = poseStack.last().pose()

        // Beam path: a LOOK_AT_DIRECTION beam is drawn as an explicit camera-facing ribbon from the emit point
        // (px,py,pz) to the FIXED scene target [beamEnd]. The far end is therefore ALWAYS the target-locator
        // position and never drifts with the actor's head/mouth locator (which only moves the start). The ribbon
        // length is the real emit->target distance, so the beam connects the two exactly instead of over/under-
        // shooting like the billboard's fixed size would.
        if (beamEnd != null && storm.effect.particle.cameraMode.type == ParticleCameraModeType.LOOK_AT_DIRECTION) {
            // Pin the NEAR end to the emit anchor (the frozen mouth/hand locator), NOT the particle's own position.
            // A beam particle carries an initial_speed along its aim direction (e.g. Ice Beam's `direction:
            // [-v.target_deltax, v.target_deltay, -v.target_deltaz]`), so it drifts forward toward the target over
            // its life. Anchoring the start to that drifting particle tore the beam away from the mouth, leaving a
            // gap. The anchor (anchorX, -anchorY, anchorZ) is exactly where a freshly-emitted particle sits — the
            // same point the debug locator marker draws — so the beam now starts right at the mouth. Far end stays
            // the fixed [beamEnd] target locator.
            val sx = anchorX; val sy = -anchorY; val sz = anchorZ
            val ex = beamEnd[0]; val ey = beamEnd[1]; val ez = beamEnd[2]
            var ax = ex - sx; var ay = ey - sy; var az = ez - sz
            val len = Mth.sqrt(ax * ax + ay * ay + az * az)
            if (len < 1.0e-4f) return
            ax /= len; ay /= len; az /= len
            // right = beamAxis x viewForward → perpendicular to the beam and facing the viewer (a flat ribbon).
            var rx = ay * forward.z - az * forward.y
            var ry = az * forward.x - ax * forward.z
            var rz = ax * forward.y - ay * forward.x
            var rlen = Mth.sqrt(rx * rx + ry * ry + rz * rz)
            if (rlen < 1.0e-4f) { rx = 0f; ry = 1f; rz = 0f; rlen = 1f } // beam ~parallel to view: any perpendicular
            rx /= rlen; ry /= rlen; rz /= rlen
            val hw = if (ySize > 0f) ySize else 0.05f // half the beam's authored thickness
            vertexConsumer.addVertex(m, sx + rx * hw, sy + ry * hw, sz + rz * hw).setUv(minU, minV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
            vertexConsumer.addVertex(m, ex + rx * hw, ey + ry * hw, ez + rz * hw).setUv(maxU, minV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
            vertexConsumer.addVertex(m, ex - rx * hw, ey - ry * hw, ez - rz * hw).setUv(maxU, maxV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
            vertexConsumer.addVertex(m, sx - rx * hw, sy - ry * hw, sz - rz * hw).setUv(minU, maxV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
            return
        }

        // Direction-based camera modes (LOOK_AT_DIRECTION without a beam target, DIRECTION_X/Y/Z) orient the quad
        // from the particle's velocity (viewDirection) and its position relative to the camera. Those vectors are
        // in sim space, but the scene PLACES particles with Y negated (see py above) — a vertical mirror. Feeding
        // sim-space vectors into the orientation while the position is mirrored leaves a MOVING sprite's facing
        // flipped against its travel (e.g. Thunderbolt's bolts, which orient along a velocity whose Y is now
        // upside-down relative to where the sprite actually goes). So compute the orientation in the SAME scene
        // space the quad is placed in: Y-flip the view direction and use the scene-space particle/camera positions.
        // (rotate_xyz/rotate_y read only the camera params, so they're unaffected and keep working as before.)
        val scenePos = Vec3(px.toDouble(), py.toDouble(), pz.toDouble())
        val cameraPosition = scenePos.subtract(forward.x * 64.0, forward.y * 64.0, forward.z * 64.0)
        val sceneViewDirection = Vec3(viewDirection.x, -viewDirection.y, viewDirection.z)
        val quaternion = storm.effect.particle.cameraMode.getRotation(
            matrixWrapper = matrixWrapper,
            prevAngle = oRoll,
            angle = roll,
            deltaTicks = tickDelta,
            particlePosition = scenePos,
            cameraPosition = cameraPosition,
            cameraAngle = Quaternionf(cameraAngle),
            cameraYaw = cameraYaw,
            cameraPitch = cameraPitch,
            viewDirection = sceneViewDirection
        )
        // GUI-scene correction: ROTATE_Y sprites come out spun 90° in-plane in this mirrored scene (e.g. Dragon
        // Claw's long "cut" aura slashes lie sideways instead of along their travel). Spin them back about their
        // own view axis. Scene-only — the real battle render is untouched because this is in renderInScene, not the
        // shared ParticleCameraMode the world uses.
        if (storm.effect.particle.cameraMode.type == ParticleCameraModeType.ROTATE_Y) {
            quaternion.rotateZ(Mth.HALF_PI)
        }

        val particleVertices = arrayOf(
            Vector3f(xSize, -ySize, 0.0f),
            Vector3f(xSize, ySize, 0.0f),
            Vector3f(-xSize, ySize, 0.0f),
            Vector3f(-xSize, -ySize, 0.0f)
        )
        for (vertex in particleVertices) {
            vertex.rotate(quaternion)
            vertex.add(px, py, pz)
        }

        // V is swapped vs the world render: this GUI scene is Y-down (we negate the particle's Y so it rises
        // correctly), which would otherwise leave the texture vertically flipped. Swapping min/max V un-flips it.
        vertexConsumer.addVertex(m, particleVertices[0].x, particleVertices[0].y, particleVertices[0].z).setUv(maxU, minV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
        vertexConsumer.addVertex(m, particleVertices[1].x, particleVertices[1].y, particleVertices[1].z).setUv(maxU, maxV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
        vertexConsumer.addVertex(m, particleVertices[2].x, particleVertices[2].y, particleVertices[2].z).setUv(minU, maxV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
        vertexConsumer.addVertex(m, particleVertices[3].x, particleVertices[3].y, particleVertices[3].z).setUv(minU, minV).setColor(colour.x, colour.y, colour.z, colour.w).setLight(light)
    }

    fun runExpirationEvents() {
        storm.effect.particle.expirationEvents.forEach { it.trigger(storm, this)}
    }

    override fun tick() {
        if (storm.effect.space.localPosition) {
            originPos = matrixWrapper.getOrigin()
        }

        applyRandoms()
        setParticleAgeInRuntime()
        storm.effect.curves.forEach { it.apply(runtime) }
        runtime.execute(storm.effect.particle.updateExpressions)
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
        // In the offscreen GUI scene there are no real blocks (it runs in empty sky), so synthesise a flat ground
        // slab at the scene's floor Y: this is what lets Bulldoze-style ground effects slide/stop instead of
        // falling through forever, mirroring the battle floor without touching the world.
        val worldCollisions = level.getBlockCollisions(null, box.expandTowards(movement))
        val collisions: Iterable<VoxelShape> = storm.guiScene?.let { scene ->
            val extra = ArrayList<VoxelShape>()
            // A thick slab whose TOP face sits exactly at floorY, covering well beyond the particle's reach.
            val floorY = scene.guiFloorY()
            val sweep = box.expandTowards(movement)
            extra.add(Shapes.create(AABB(sweep.minX - 64.0, floorY - 64.0, sweep.minZ - 64.0, sweep.maxX + 64.0, floorY, sweep.maxZ + 64.0)))
            // The target's solid box, so traveling effects stop AT the target's face instead of overshooting it.
            scene.guiImpactBox(storm)?.let { extra.add(Shapes.create(it)) }
            worldCollisions + extra
        } ?: worldCollisions
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

    override fun getRenderType() = particleTextureSheet

    override fun remove() {
        super.remove()
        storm.particles.remove(this)
    }
}
