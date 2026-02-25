/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.BoneTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

/**
 * Resolves bone positions from Bedrock model entities.
 * Client-side only - accesses entity models and animation state.
 */
object BoneFocusResolver {

    const val STAR_BONE = "*"
    private val STAR_BONE_ALIASES = setOf("*", "star", "auto", "dynamic")

    private data class SmoothingState(
        var lastPosition: Vec3,
        var velocity: Vec3 = Vec3.ZERO,
        val smoothTime: Double = 0.25
    )

    private val smoothingStates = mutableMapOf<String, SmoothingState>()

    fun resolveBonePosition(
        entityId: Int,
        boneName: String,
        offset: Vec3,
        smooth: Boolean,
        fallback: Vec3?
    ): Vec3? {
        val client = Minecraft.getInstance()
        val level = client.level ?: return fallback
        val entity = level.getEntity(entityId) ?: return fallback

        if (!entity.isAlive || entity.isRemoved) {
            return fallback
        }

        val bonePosition = when (entity) {
            is PokemonEntity -> resolvePokemonBone(entity, boneName)
            else -> resolveGenericEntityBone(entity, boneName)
        }

        if (bonePosition == null) {
            return fallback
        }

        val finalPosition = bonePosition.add(offset)

        if (!smooth) {
            smoothingStates.remove(getCacheKey(entityId, boneName))
            return finalPosition
        }

        return applySmoothing(getCacheKey(entityId, boneName), finalPosition)
    }

    private val BONE_TO_LOCATOR_FALLBACKS = mapOf(
        "body" to "middle",
        "body_0" to "middle",
        "torso" to "middle",
        "chest" to "middle",
        "spine" to "middle",
        "head_ai_target_position" to "head",
        "base" to "root",
        "origin" to "root"
    )

    private val SEND_OUT_BALL_ALIASES = setOf("send_out_ball", "ball", "pokeball")

    private fun resolvePokemonBone(pokemon: PokemonEntity, boneName: String): Vec3? {
        val clientDelegate = pokemon.delegate as? PokemonClientDelegate ?: return null

        if (boneName.lowercase() in STAR_BONE_ALIASES) {
            return resolveStarBone(pokemon, clientDelegate)
        }

        if (boneName.lowercase() in SEND_OUT_BALL_ALIASES) {
            return resolveSendOutBallPosition(pokemon, clientDelegate)
        }

        val locator = clientDelegate.locatorStates[boneName]
        if (locator != null) {
            return locator.getOrigin()
        }

        val trackedBone = clientDelegate.trackedBoneStates[boneName]
        if (trackedBone != null) {
            return trackedBone.getOrigin()
        }

        val variations = listOf(
            boneName.lowercase(),
            boneName.replace("_", ""),
            "internal_locator__$boneName"
        )

        for (variation in variations) {
            val found = clientDelegate.locatorStates[variation]
            if (found != null) {
                return found.getOrigin()
            }
        }

        BoneTracker.hold(pokemon.id, boneName)

        val fallbackLocator = BONE_TO_LOCATOR_FALLBACKS[boneName.lowercase()]
        if (fallbackLocator != null) {
            val found = clientDelegate.locatorStates[fallbackLocator]
            if (found != null) {
                return found.getOrigin()
            }
        }

        val middleLocator = clientDelegate.locatorStates["middle"]
        if (middleLocator != null) {
            return middleLocator.getOrigin()
        }

        return null
    }

    /** Cache of discovered bone names per entity to avoid re-scanning every frame */
    private val discoveredBones = mutableMapOf<Int, Set<String>>()

    private fun resolveSendOutBallPosition(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate): Vec3? {
        if (clientDelegate.ballDone) {
            return clientDelegate.locatorStates["middle"]?.getOrigin() ?: pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
        }

        val sendOutPos = clientDelegate.sendOutPosition
        val sendOutOffset = clientDelegate.sendOutOffset

        if (sendOutPos != null) {
            val ballPos = if (sendOutOffset != null) {
                sendOutPos.add(sendOutOffset)
            } else {
                sendOutPos
            }
            return ballPos
        }

        return pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
    }

    private fun resolveStarBone(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate): Vec3? {
        val entityId = pokemon.id

        val needsDiscovery = !StarBoneTracker.isTracking(entityId)
        if (needsDiscovery) {
            StarBoneTracker.startTracking(entityId)
            discoverAndTrackBones(pokemon, clientDelegate, entityId)
        } else if (StarBoneTracker.isStale(entityId)) {
            StarBoneTracker.resetTracking(entityId)
        }

        // Sample the current frame for star bone detection
        StarBoneTracker.sample(entityId, clientDelegate)

        // Try to get the star bone
        val starBoneName = StarBoneTracker.getStarBone(entityId)

        if (starBoneName != null) {
            val starPosition = clientDelegate.locatorStates[starBoneName]?.getOrigin()
                ?: clientDelegate.trackedBoneStates[starBoneName]?.getOrigin()

            if (starPosition != null) {
                return starPosition
            }
        }

        val middleLocator = clientDelegate.locatorStates["middle"]
        if (middleLocator != null) {
            return middleLocator.getOrigin()
        }

        return null
    }

    private fun discoverAndTrackBones(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate, entityId: Int) {
        val model = clientDelegate.currentModel ?: return

        // species + aspects as model identity since each variant may have different bones
        val species = pokemon.pokemon.species.resourceIdentifier.toString()
        val aspects = pokemon.pokemon.aspects.sorted().joinToString(",")
        val modelId = if (aspects.isEmpty()) species else "$species[$aspects]"
        StarBoneTracker.setModelId(entityId, modelId)

        if (StarBoneTracker.getStarBone(entityId) != null) {
            return
        }

        val boneNames = collectAllBoneNames(model.rootPart)
        discoveredBones[entityId] = boneNames

        val descendantCounts = countBoneDescendants(model.rootPart)
        StarBoneTracker.setBoneMetadata(entityId, descendantCounts)

        boneNames.forEach { boneName ->
            BoneTracker.hold(entityId, boneName)
        }
    }

    fun startStarBoneTracking(entityId: Int) {
        StarBoneTracker.startTracking(entityId)
    }

    fun stopStarBoneTracking(entityId: Int) {
        StarBoneTracker.stopTracking(entityId)
    }

    fun getStarBone(entityId: Int): String? {
        return StarBoneTracker.getStarBone(entityId)
    }

    /** Replicates LocatorAccess.update() for arbitrary bones. */
    private fun calculateBonePosition(
        pokemon: PokemonEntity,
        clientDelegate: PokemonClientDelegate,
        model: PosableModel,
        boneName: String
    ): Vec3? {
        // Find the bone in the model hierarchy
        val targetBone = findBoneByName(model.rootPart, boneName) ?: return null

        // Build the hierarchy from root to target
        val hierarchy = buildBoneHierarchy(model, targetBone)
        if (hierarchy.isEmpty()) return null

        // Create a PoseStack and apply transformations like LocatorAccess.update() does
        val matrixStack = PoseStack()

        var scale = pokemon.pokemon.form.baseScale * pokemon.pokemon.scaleModifier * clientDelegate.entityScaleModifier
        scale = scale.coerceAtLeast(0.01f)

        val yRot = Mth.lerp(
            clientDelegate.getPartialTicks(),
            pokemon.yBodyRotO,
            pokemon.yBodyRot
        )
        val adjustedYRot = 180f - Mth.wrapDegrees(yRot)

        matrixStack.scale(scale, scale, scale)
        matrixStack.mulPose(Axis.YP.rotationDegrees(adjustedYRot))
        matrixStack.mulPose(Axis.ZP.rotationDegrees(180f)) // Entity flip

        for (bone in hierarchy) {
            bone.transform(matrixStack)
        }

        // Undo the Z-flip: entities' locators are positioned upside-down
        matrixStack.mulPose(Axis.ZP.rotationDegrees(180f))

        val translation = matrixStack.last().pose().getTranslation(Vector3f())

        return pokemon.position().add(
            translation.x.toDouble(),
            translation.y.toDouble(),
            translation.z.toDouble()
        )
    }

    private fun findBoneByName(root: Bone, name: String): Bone? {
        val directChild = root.children[name]
        if (directChild != null) {
            return directChild
        }

        for ((_, child) in root.children) {
            val found = findBoneByName(child, name)
            if (found != null) return found
        }

        return null
    }

    private fun collectAllBoneNames(root: Bone, result: MutableSet<String> = mutableSetOf()): Set<String> {
        for ((name, child) in root.children) {
            result.add(name)
            collectAllBoneNames(child, result)
        }
        return result
    }

    private fun countBoneDescendants(root: Bone, result: MutableMap<String, Int> = mutableMapOf()): Map<String, Int> {
        for ((name, child) in root.children) {
            result[name] = countDescendants(child)
            countBoneDescendants(child, result)
        }
        return result
    }

    private fun countDescendants(bone: Bone): Int {
        var count = bone.children.size
        for ((_, child) in bone.children) {
            count += countDescendants(child)
        }
        return count
    }

    private fun buildBoneHierarchy(model: PosableModel, targetBone: Bone): List<Bone> {
        val hierarchy = mutableListOf<Bone>()
        var current: Bone? = targetBone

        while (current != null) {
            hierarchy.add(0, current)
            current = findParentBone(model, current)
        }

        return hierarchy
    }

    private fun findParentBone(model: PosableModel, bone: Bone): Bone? {
        fun searchBone(searchIn: Bone): Bone? {
            for ((_, child) in searchIn.children) {
                if (child == bone) return searchIn
                val found = searchBone(child)
                if (found != null) return found
            }
            return null
        }

        return searchBone(model.rootPart)
    }


    /**
     * Resolve bone from generic Bedrock model entity (NPCs, etc).
     */
    private fun resolveGenericEntityBone(entity: Entity, boneName: String): Vec3? {
        // For now, fallback to entity position with simple offset
        val heightOffset = when (boneName.lowercase()) {
            "head", "head_ai_target_position" -> entity.bbHeight * 0.9
            "body", "body_0" -> entity.bbHeight * 0.5
            "neck" -> entity.bbHeight * 0.7
            in STAR_BONE_ALIASES -> entity.bbHeight * 0.5 // Star bone defaults to center
            else -> entity.bbHeight * 0.5
        }

        return entity.position().add(0.0, heightOffset, 0.0)
    }


    private fun applySmoothing(cacheKey: String, targetPosition: Vec3): Vec3 {
        val state = smoothingStates.getOrPut(cacheKey) {
            SmoothingState(targetPosition)
        }

        // Approximate frame delta (camera runs at render framerate)
        val dt = 0.016 // ~60fps, good enough for smoothing
        val smoothTime = state.smoothTime.coerceAtLeast(0.001)

        val omega = 2.0 / smoothTime
        val x = omega * dt
        val exp = 1.0 / (1.0 + x + 0.48 * x * x + 0.235 * x * x * x)

        val delta = state.lastPosition.subtract(targetPosition)
        val temp = state.velocity.add(delta.scale(omega)).scale(dt)
        state.velocity = state.velocity.subtract(temp.scale(omega)).scale(exp)
        val newPos = targetPosition.add(delta.add(temp).scale(exp))

        state.lastPosition = newPos
        return newPos
    }

    fun clearSmoothingState(entityId: Int, boneName: String) {
        smoothingStates.remove(getCacheKey(entityId, boneName))
    }

    fun clearAll() {
        smoothingStates.clear()
        StarBoneTracker.clearAll()
        discoveredBones.clear()
    }

    private fun getCacheKey(entityId: Int, boneName: String) = "$entityId:$boneName"

    fun listAvailableLocators(entityId: Int): List<String>? {
        val client = Minecraft.getInstance()
        val level = client.level ?: return null
        val entity = level.getEntity(entityId) as? PokemonEntity ?: return null
        val clientDelegate = entity.delegate as? PokemonClientDelegate ?: return null

        return clientDelegate.locatorStates.keys.toList().sorted()
    }

    fun debugPrintLocators(entityId: Int) {
        val locators = listAvailableLocators(entityId)
        if (locators == null) {
            Cobblemon.LOGGER.debug("[BoneFocus] Entity $entityId not found or not a Pokemon")
            return
        }
        Cobblemon.LOGGER.debug("[BoneFocus] Available locators for entity $entityId: ${locators.joinToString(", ")}")
    }

    fun debugPrintStarBone(entityId: Int) {
        StarBoneTracker.debugPrint(entityId)
    }
}
