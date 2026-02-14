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
        var velocity: Vec3 = Vec3.ZERO
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
        // Body/torso bones → middle (center of entity)
        "body" to "middle",
        "body_0" to "middle",
        "torso" to "middle",
        "chest" to "middle",
        "spine" to "middle",

        // Head variations
        "head_ai_target_position" to "head",

        // Root/base
        "base" to "root",
        "origin" to "root"
    )

    private val SEND_OUT_BALL_ALIASES = setOf("send_out_ball", "ball", "pokeball")

    private fun resolvePokemonBone(pokemon: PokemonEntity, boneName: String): Vec3? {
        val clientDelegate = pokemon.delegate as? PokemonClientDelegate ?: return null

        if (boneName.lowercase() in STAR_BONE_ALIASES) {
            return resolveStarBone(pokemon, clientDelegate)
        }

        // Special handling for send-out ball position (computed from client animation state)
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

    /**
     * Resolve the send-out ball position during pokeball throw animation.
     *
     * This computes the ball position from the client delegate's animation state,
     * which tracks the ball trajectory as it's thrown toward the spawn position.
     *
     * @param pokemon The Pokemon entity being sent out
     * @param clientDelegate The client delegate with animation state
     * @return Position of the ball, or the Pokemon position if not in send-out animation
     */
    private fun resolveSendOutBallPosition(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate): Vec3? {
        // If ball animation is not active, fall back to Pokemon position
        if (clientDelegate.ballDone) {
            // Ball animation is done, return Pokemon's middle locator instead
            return clientDelegate.locatorStates["middle"]?.getOrigin() ?: pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
        }

        // Compute ball position from send-out animation state
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

        // Fallback to Pokemon position if send-out animation data not available
        return pokemon.position().add(0.0, pokemon.bbHeight / 2.0, 0.0)
    }

    /**
     * Resolve the "star bone" - the most animated bone.
     *
     * Uses [StarBoneTracker] to find which bone has moved the most during
     * the current animation. Falls back to "middle" locator if star bone
     * tracking hasn't collected enough data yet.
     *
     * **Auto-tracking**: If tracking hasn't been started for this entity,
     * it will be started automatically. This also discovers all bones in
     * the model and requests tracking for them via [BoneTracker].
     *
     * @param pokemon The Pokemon entity
     * @param clientDelegate The client delegate with locator states
     * @return Position of the star bone, or fallback position
     */
    private fun resolveStarBone(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate): Vec3? {
        val entityId = pokemon.id

        // Auto-start tracking if not already tracking, or reset if stale
        val needsDiscovery = !StarBoneTracker.isTracking(entityId)
        if (needsDiscovery) {
            StarBoneTracker.startTracking(entityId)
            // Discover and request tracking for all bones in the model
            discoverAndTrackBones(pokemon, clientDelegate, entityId)
        } else if (StarBoneTracker.isStale(entityId)) {
            // Tracking is stale (no movement detected) - reset it
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

    /**
     * Discover all bones in the model and request tracking for them.
     * This populates trackedBoneStates for the next frame.
     * Also calculates bone metadata (descendant counts) for weighting.
     */
    private fun discoverAndTrackBones(pokemon: PokemonEntity, clientDelegate: PokemonClientDelegate, entityId: Int) {
        val model = clientDelegate.currentModel ?: return

        // Get model identifier for caching across entity instances
        // Use species + aspects as identifier since each variant may have different model/bones
        val species = pokemon.pokemon.species.resourceIdentifier.toString()
        val aspects = pokemon.pokemon.aspects.sorted().joinToString(",")
        val modelId = if (aspects.isEmpty()) species else "$species[$aspects]"
        StarBoneTracker.setModelId(entityId, modelId)

        // If we already have a cached star bone for this model, we're done
        if (StarBoneTracker.getStarBone(entityId) != null) {
            return
        }

        // Collect all bone names from the model
        val boneNames = collectAllBoneNames(model.rootPart)
        discoveredBones[entityId] = boneNames

        // Count descendants for each bone (main bones have more descendants)
        val descendantCounts = countBoneDescendants(model.rootPart)

        // Pass bone metadata to StarBoneTracker for weighting
        StarBoneTracker.setBoneMetadata(entityId, descendantCounts)

        boneNames.forEach { boneName ->
            BoneTracker.hold(entityId, boneName)
        }
    }

    /**
     * Start star bone tracking for an entity.
     * Call this when an animation starts that you want to track.
     *
     * @param entityId The entity's network ID
     */
    fun startStarBoneTracking(entityId: Int) {
        StarBoneTracker.startTracking(entityId)
    }

    /**
     * Stop star bone tracking for an entity.
     * Call this when the animation ends.
     *
     * @param entityId The entity's network ID
     */
    fun stopStarBoneTracking(entityId: Int) {
        StarBoneTracker.stopTracking(entityId)
    }

    /**
     * Get the current star bone name for an entity.
     *
     * @param entityId The entity's network ID
     * @return Name of the most animated bone, or null if not tracking/not ready
     */
    fun getStarBone(entityId: Int): String? {
        return StarBoneTracker.getStarBone(entityId)
    }

    /**
     * Calculate bone position by walking the bone hierarchy.
     * This replicates the logic from LocatorAccess.update() for arbitrary bones.
     */
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

        // Apply entity-level transformations (scale and rotation)
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

        // Walk the hierarchy applying each bone's transformation cumulatively
        // Use Bone.transform() which properly handles animated transformations
        for (bone in hierarchy) {
            bone.transform(matrixStack)
        }

        // Undo the Z-flip rotation (same as LocatorAccess.update() does at line 113)
        // This is necessary because entities' locators are positioned upside-down
        matrixStack.mulPose(Axis.ZP.rotationDegrees(180f))

        // Extract the final position
        val translation = matrixStack.last().pose().getTranslation(Vector3f())

        // Add entity world position to get world coordinates
        return pokemon.position().add(
            translation.x.toDouble(),
            translation.y.toDouble(),
            translation.z.toDouble()
        )
    }

    /**
     * Find a bone by name in the model hierarchy.
     * Uses the Bone interface which all model bones implement.
     */
    private fun findBoneByName(root: Bone, name: String): Bone? {
        // Check children by name using Bone interface
        val directChild = root.children[name]
        if (directChild != null) {
            return directChild
        }

        // Recursively search children
        for ((_, child) in root.children) {
            val found = findBoneByName(child, name)
            if (found != null) return found
        }

        return null
    }

    /**
     * Collect all bone names from a bone hierarchy.
     */
    private fun collectAllBoneNames(root: Bone, result: MutableSet<String> = mutableSetOf()): Set<String> {
        for ((name, child) in root.children) {
            result.add(name)
            collectAllBoneNames(child, result)
        }
        return result
    }

    /**
     * Count total descendants (children, grandchildren, etc.) for each bone.
     * Bones with more descendants are "main" structural bones.
     */
    private fun countBoneDescendants(root: Bone, result: MutableMap<String, Int> = mutableMapOf()): Map<String, Int> {
        for ((name, child) in root.children) {
            result[name] = countDescendants(child)
            countBoneDescendants(child, result)
        }
        return result
    }

    /**
     * Count all descendants of a bone recursively.
     */
    private fun countDescendants(bone: Bone): Int {
        var count = bone.children.size
        for ((_, child) in bone.children) {
            count += countDescendants(child)
        }
        return count
    }

    /**
     * Builds the hierarchy of bones from root to target bone.
     * This ensures transformations are applied in the correct order.
     */
    private fun buildBoneHierarchy(model: PosableModel, targetBone: Bone): List<Bone> {
        val hierarchy = mutableListOf<Bone>()
        var current: Bone? = targetBone

        // Build path from target to root
        while (current != null) {
            hierarchy.add(0, current) // Add at beginning to reverse order
            current = findParentBone(model, current)
        }

        return hierarchy
    }

    /**
     * Finds the parent bone of a given bone by searching the model tree.
     * Uses the Bone interface which all model bones implement.
     */
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

    /**
     * Apply smoothing to bone position using simple velocity smoothing.
     */
    private fun applySmoothing(cacheKey: String, targetPosition: Vec3): Vec3 {
        val state = smoothingStates.getOrPut(cacheKey) {
            SmoothingState(targetPosition)
        }

        val delta = targetPosition.subtract(state.lastPosition)

        val smoothingFactor = 0.3
        state.velocity = state.velocity.scale(1.0 - smoothingFactor).add(delta.scale(smoothingFactor))

        val smoothedPosition = state.lastPosition.add(state.velocity)
        state.lastPosition = smoothedPosition

        return smoothedPosition
    }

    /**
     * Clear smoothing state for an entity bone.
     */
    fun clearSmoothingState(entityId: Int, boneName: String) {
        smoothingStates.remove(getCacheKey(entityId, boneName))
    }

    /**
     * Clear all smoothing states and star bone tracking.
     */
    fun clearAll() {
        smoothingStates.clear()
        StarBoneTracker.clearAll()
        discoveredBones.clear()
    }

    private fun getCacheKey(entityId: Int, boneName: String) = "$entityId:$boneName"

    /**
     * Debug helper: List all available locators for a Pokemon entity.
     * Use this to discover what locator names are available.
     *
     * @param entityId Entity network ID
     * @return List of available locator names, or null if entity not found
     */
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

    /**
     * Debug helper: Print star bone tracking status.
     *
     * @param entityId Entity network ID
     */
    fun debugPrintStarBone(entityId: Int) {
        StarBoneTracker.debugPrint(entityId)
    }
}
