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
//        // ---- Rock Slide debug instrumentation (temporary). Capped so a runaway can't flood the log. ----
//        var dbgLines = 0
//        const val DBG_CAP = 6000
    }

//    // True only for Rock Slide storms running inside the MoveDex GUI scene — keeps the firehose off battle/other moves.
//    private val dbgOn: Boolean get() = storm.guiScene != null && storm.effect.id.path.contains("rockslide")
//
//    /** Logs one line of everything we know about this particle right now, prefixed [phase]. Gated + capped. */
//    private fun dbg(phase: String, extra: String = "") {
//        if (!dbgOn || dbgLines >= DBG_CAP) return
//        dbgLines++
//        val floor = storm.guiScene?.guiFloorY(storm) ?: 0.0
//        Cobblemon.LOGGER.info(
//            "ROCKDBG#{} {} id={} age={}/{} worldY={} aboveFloor={} pos=({},{},{}) vel=({},{},{}) localSpace={} localPos={} collEnabled={} expireOnContact={} removed={} {}",
//            dbgLines, "%-16s".format(phase), storm.effect.id.path, age, lifetime,
//            "%.3f".format(y), "%.3f".format(y - floor),
//            "%.2f".format(x), "%.2f".format(y), "%.2f".format(z),
//            "%.4f".format(xd), "%.4f".format(yd), "%.4f".format(zd),
//            storm.effect.space.isLocalSpace, storm.effect.space.localPosition,
//            runtime.resolveBoolean(storm.effect.particle.collision.enabled),
//            storm.effect.particle.collision.expiresOnContact, removed, extra
//        )
//    }

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
//        dbg("SPAWN", "initVel=(${"%.3f".format(initialVelocity.x)},${"%.3f".format(initialVelocity.y)},${"%.3f".format(initialVelocity.z)}) " +
//            "collRadius=${"%.3f".format(runtime.resolveDouble(storm.effect.particle.collision.radius))} " +
//            "motion=${storm.effect.particle.motion::class.simpleName} maxAgeSec=${"%.3f".format(runtime.resolveDouble(storm.effect.particle.maxAge))} " +
//            "stormXYZ=(${"%.2f".format(storm.getX())},${"%.2f".format(storm.getY())},${"%.2f".format(storm.getZ())}) " +
//            "localX/Y/Z=(${"%.2f".format(localX)},${"%.2f".format(localY)},${"%.2f".format(localZ)}) originPos=(${"%.2f".format(originPos.x)},${"%.2f".format(originPos.y)},${"%.2f".format(originPos.z)})")
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
        tickDelta: Float
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

        // Orient with the SAME world-space inputs battle feeds getRotation — no munging — so every camera mode
        // behaves exactly as in battle. The mismatch this scene introduces is purely that it mirrors world-up to
        // screen-up by negating Y in the particle POSITION (py). We reconcile that below by mirroring the oriented
        // quad's Y for the world-locked modes, rather than distorting the inputs into getRotation.
        val cameraPosition = Vec3(x - forward.x * 64.0, y - forward.y * 64.0, z - forward.z * 64.0)
        // ROTATE_Y applies the particle's in-plane spin (roll) in a frame the scene's Y-mirror REVERSES, so an
        // authored initial_spin comes out rotated the wrong way — Dragon Claw's aura (spin -135°) and Swords Dance's
        // swords (-45°) looked rotated. Negate the roll for this mode so spun rotate_y sprites match battle. A sprite
        // with NO spin (Thunder's vertical bolt, roll 0) is unchanged, so this can't re-break it. rotate_xyz applies
        // roll in screen space (after cameraAngle) and already reads correctly, so it is left alone.
        val rotateY = storm.effect.particle.cameraMode.type == ParticleCameraModeType.ROTATE_Y
        val quaternion = storm.effect.particle.cameraMode.getRotation(
            matrixWrapper = matrixWrapper,
            prevAngle = if (rotateY) -oRoll else oRoll,
            angle = if (rotateY) -roll else roll,
            deltaTicks = tickDelta,
            particlePosition = Vec3(x, y, z),
            cameraPosition = cameraPosition,
            cameraAngle = Quaternionf(cameraAngle),
            cameraYaw = cameraYaw,
            cameraPitch = cameraPitch,
            viewDirection = viewDirection
        )
        // A camera-facing billboard (rotate_xyz/rotate_y/lookat_y/lookat_xyz) is symmetric under the scene's Y
        // mirror, so its world-space orientation still reads correctly. But a mode that locks the quad to a WORLD
        // axis or the particle's velocity (lookat_direction, direction_*, emitter planes) must have its oriented
        // quad mirrored in Y to match the mirrored position — otherwise the sprite faces against its own travel
        // (Thunderbolt's bolts) or sits at the wrong tilt. We mirror the QUAD, not getRotation's inputs, so the
        // shared battle code is reused verbatim.
        val worldLocked = when (storm.effect.particle.cameraMode.type) {
            ParticleCameraModeType.LOOK_AT_DIRECTION,
            ParticleCameraModeType.DIRECTION_X,
            ParticleCameraModeType.DIRECTION_Y,
            ParticleCameraModeType.DIRECTION_Z,
            ParticleCameraModeType.EMITTER_XY_PLANE,
            ParticleCameraModeType.EMITTER_XZ_PLANE,
            ParticleCameraModeType.EMITTER_YZ_PLANE -> true
            else -> false
        }

        val particleVertices = arrayOf(
            Vector3f(xSize, -ySize, 0.0f),
            Vector3f(xSize, ySize, 0.0f),
            Vector3f(-xSize, ySize, 0.0f),
            Vector3f(-xSize, -ySize, 0.0f)
        )
        for (vertex in particleVertices) {
            vertex.rotate(quaternion)
            if (worldLocked) vertex.y = -vertex.y // mirror the oriented quad to match the scene's Y-mirrored position
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

//        dbg("TICK")
        if (age >= lifetime || runtime.resolveBoolean(storm.effect.particle.killExpression)) {
//            dbg("EXPIRE-AGE/KILL", "ageHit=${age >= lifetime} killExpr=${runtime.resolveBoolean(storm.effect.particle.killExpression)}")
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

        val collEnabled = runtime.resolveBoolean(collision.enabled)
//        dbg("MOVE", "dxyz=(${"%.4f".format(dx)},${"%.4f".format(dy)},${"%.4f".format(dz)}) collEnabled=$collEnabled radius=${"%.3f".format(radius)} isLocalSpace=${storm.effect.space.isLocalSpace} " +
//            "=> collisionPath=${collEnabled && radius > 0.0 && !storm.effect.space.isLocalSpace}")
        if (collEnabled && radius > 0.0 && !storm.effect.space.isLocalSpace) {
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
//            dbg("MOVE-NOCOLLISION", "floor & expire_on_contact are SKIPPED here (collEnabled=$collEnabled radius=${"%.3f".format(radius)} isLocalSpace=${storm.effect.space.isLocalSpace})")
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
            // A thick slab whose TOP face sits exactly at floorY, covering well beyond the particle's reach. Only
            // add it when the particle is NOT moving upward away from it: a particle launched UP off the floor
            // (Rock Slide's `guide2` ray, which spawns the falling rocks high overhead, spawns AT floor level and
            // shoots up with expire_on_contact) must not be killed by the floor on its first tick — else the rocks
            // never spawn above the target. A descending/level particle still collides and settles normally.
            // CRITICAL: only add a synthesized shape if the swept box ACTUALLY reaches it. The real
            // level.getBlockCollisions only returns shapes the box truly intersects, and downstream logic
            // (collisions.none(), the expire_on_contact shortcut) treats "list non-empty" as "colliding". If we
            // add the floor unconditionally whenever a particle descends, an expire_on_contact particle (Rock
            // Slide's falling rocks) sees a permanent candidate and breaks in mid-air the instant the spawn grace
            // ends — never falling to the ground. The floor slab's top is floorY, so the box reaches it only once
            // its swept minY dips to/below floorY.
            val sweep = box.expandTowards(movement)
            if (movement.y <= 0.0) {
                val floorY = scene.guiFloorY(storm)
                if (sweep.minY <= floorY) {
                    extra.add(Shapes.create(AABB(sweep.minX - 64.0, floorY - 64.0, sweep.minZ - 64.0, sweep.maxX + 64.0, floorY, sweep.maxZ + 64.0)))
                }
            }
            // The target's solid box, so traveling effects stop AT the target's face instead of overshooting it —
            // but, like the floor, only when the swept box actually touches it (else it'd be a permanent candidate).
            scene.guiImpactBox(storm)?.let { ib ->
                if (sweep.intersects(ib)) { extra.add(Shapes.create(ib)) }
            }
            worldCollisions + extra
        } ?: worldCollisions
        if (collisions.none()) {
            colliding = false
            return movement
        } else if (expiresOnContact && age <= 2) {
            // Grace period: an expire_on_contact particle must not break on the tick(s) it spawns. Rock Slide lofts
            // its rocks into the air with a guide ray and they were breaking instantly at the spawn point instead of
            // falling to the floor. Let them move a couple ticks to clear the spawn before contact can kill them.
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
