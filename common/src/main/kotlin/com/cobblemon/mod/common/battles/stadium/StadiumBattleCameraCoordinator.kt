/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.camera.CameraController
import com.cobblemon.mod.common.api.camera.ICameraController
import com.cobblemon.mod.common.api.camera.ICameraCoordinator
import com.cobblemon.mod.common.api.camera.CameraManager
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler.SEND_OUT_DURATION
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinator for stadium battle cameras.
 *
 * This coordinator manages focus behavior for battle cameras, including:
 * - Setting focus targets on the user or target of moves
 * - Managing reference-counted focus overrides
 * - Managing send-out camera focus (ball then Pokemon)
 * - Providing battle-specific MoLang variables
 *
 * Each instance manages one camera associated with a stadium battle.
 *
 * @author Terrainwax
 * @since January 2025
 */
class StadiumBattleCameraCoordinator : ICameraCoordinator {

    companion object {
        private val battleCoordinators = mutableMapOf<UUID, StadiumBattleCameraCoordinator>()
        private val cameraCoordinators = mutableMapOf<UUID, StadiumBattleCameraCoordinator>()

        // Secondary cameras (Camera B) for split-screen, keyed by primary camera ID
        private val secondaryCameras = mutableMapOf<UUID, CameraController>()

        // Stores current user/target entity UUIDs for MoLang query resolution
        private val currentUserEntities = mutableMapOf<UUID, List<UUID>>()
        private val currentTargetEntities = mutableMapOf<UUID, List<UUID>>()

        // Stores current send-out entity UUIDs for camera tracking
        private val currentSendOutEntities = mutableMapOf<UUID, List<UUID>>()

        /**
         * Gets the current user entity UUIDs for a battle.
         * Used by MoLang queries to resolve 'user' role.
         */
        fun getCurrentUserEntityIds(battleId: UUID): List<UUID> = currentUserEntities[battleId] ?: emptyList()

        /**
         * Gets the current target entity UUIDs for a battle.
         * Used by MoLang queries to resolve 'target' role.
         */
        fun getCurrentTargetEntityIds(battleId: UUID): List<UUID> = currentTargetEntities[battleId] ?: emptyList()

        /**
         * Gets the current send-out entity UUIDs for a battle.
         * Used by MoLang queries to resolve send-out Pokemon.
         */
        fun getCurrentSendOutEntityIds(battleId: UUID): List<UUID> = currentSendOutEntities[battleId] ?: emptyList()

        /**
         * Binds a coordinator to a battle and camera.
         *
         * This is a convenience method for backward compatibility with existing battle code.
         *
         * @param battle The battle to bind
         * @param cameraId The camera ID to bind to
         */
        fun bind(battle: PokemonBattle, cameraId: UUID) {
            val coordinator = StadiumBattleCameraCoordinator()
            coordinator.bind(cameraId)
            battleCoordinators[battle.battleId] = coordinator
            cameraCoordinators[cameraId] = coordinator
        }

        /**
         * Binds a coordinator to a camera before the battle has started.
         * Used for deferred battle start where the camera runs an intro cinematic first.
         *
         * @param cameraId The camera ID to bind to
         */
        fun bindCamera(cameraId: UUID) {
            val coordinator = StadiumBattleCameraCoordinator()
            coordinator.bind(cameraId)
            cameraCoordinators[cameraId] = coordinator
        }

        /**
         * Registers a secondary camera (Camera B) for split-screen, keyed by the primary camera ID.
         */
        fun registerSecondaryCamera(primaryCameraId: UUID, secondaryCamera: CameraController) {
            secondaryCameras[primaryCameraId] = secondaryCamera
        }

        /**
         * Gets the secondary camera for a primary camera ID.
         */
        fun getSecondaryCamera(primaryCameraId: UUID): CameraController? = secondaryCameras[primaryCameraId]

        /**
         * Ticks all secondary cameras. Called from the server tick loop
         * alongside primary camera ticking.
         */
        fun tickSecondaryCameras() {
            secondaryCameras.values.forEach { it.tick() }
        }

        /**
         * Stops and removes a secondary camera by primary camera ID.
         */
        fun stopSecondaryCamera(primaryCameraId: UUID) {
            secondaryCameras.remove(primaryCameraId)?.stop()
        }

        /**
         * Associates an existing camera-bound coordinator with a battle.
         * Called when the deferred battle start fires (via launch_battle MoLang function).
         *
         * @param battle The battle to associate
         * @param cameraId The camera ID of the existing coordinator
         */
        fun bindBattle(battle: PokemonBattle, cameraId: UUID) {
            val coordinator = cameraCoordinators[cameraId]
            if (coordinator != null) {
                battleCoordinators[battle.battleId] = coordinator
            } else {
                bind(battle, cameraId)
            }
        }

        /**
         * Releases the coordinator for a battle.
         *
         * @param battleId The battle ID
         */
        fun release(battleId: UUID) {
            val coordinator = battleCoordinators.remove(battleId)
            coordinator?.let {
                it.cameraId?.let { camId ->
                    cameraCoordinators.remove(camId)
                    stopSecondaryCamera(camId)
                }
                it.release()
            }
            clearStoredEntities(battleId)
        }

        /**
         * Releases a coordinator by camera ID. Used when a camera stops before a battle was launched.
         *
         * @param cameraId The camera ID
         */
        fun releaseByCameraId(cameraId: UUID) {
            val coordinator = cameraCoordinators.remove(cameraId)
            coordinator?.release()
            stopSecondaryCamera(cameraId)
        }

        /**
         * Gets the coordinator for a battle.
         *
         * @param battleId The battle ID
         * @return The coordinator, or null if not found
         */
        fun getCoordinator(battleId: UUID): StadiumBattleCameraCoordinator? {
            return battleCoordinators[battleId]
        }

        /**
         * Forces release of focus for a battle.
         *
         * This is a convenience method for backward compatibility.
         *
         * @param battleId The battle ID
         */
        fun forceRelease(battleId: UUID) {
            val coordinator = battleCoordinators[battleId]
            coordinator?.let {
                it.cameraId?.let { camId -> cameraCoordinators.remove(camId) }
                it.forceRelease()
            }
            clearStoredEntities(battleId)
        }

        /**
         * Starts focus on entities for a battle.
         *
         * This is a convenience method for backward compatibility.
         *
         * @param battle The battle
         * @param entities List of entities to focus on
         * @param role The focus role (USER or TARGET)
         * @return A cleanup function, or null if focus could not be set
         */
        fun startFocus(battle: PokemonBattle, entities: List<Entity>, role: FocusRole): (() -> Unit)? {
            val entityIds = entities.map { it.uuid }

            // Store entity UUIDs for MoLang query resolution
            when (role) {
                FocusRole.USER -> currentUserEntities[battle.battleId] = entityIds
                FocusRole.TARGET -> currentTargetEntities[battle.battleId] = entityIds
                FocusRole.SEND_OUT -> currentSendOutEntities[battle.battleId] = entityIds
            }

            val cleanup = battleCoordinators[battle.battleId]?.startFocus(entities, role)

            // Return a cleanup function that also clears the stored entity UUIDs
            return if (cleanup != null) {
                {
                    cleanup()
                    when (role) {
                        FocusRole.USER -> currentUserEntities.remove(battle.battleId)
                        FocusRole.TARGET -> currentTargetEntities.remove(battle.battleId)
                        FocusRole.SEND_OUT -> currentSendOutEntities.remove(battle.battleId)
                    }
                }
            } else null
        }

        // Stores send-out target positions for camera tracking (server-side accessible)
        private val sendOutPositions = mutableMapOf<UUID, Vec3>()
        
        // Duration for each send-out camera focus (animation time + buffer)
        private const val SEND_OUT_CAMERA_DURATION = SEND_OUT_DURATION + 0.3F

        /**
         * Gets the send-out target position for a battle.
         * Used by MoLang queries to resolve ball/send-out position.
         */
        fun getSendOutPosition(battleId: UUID): Vec3? = sendOutPositions[battleId]
        
        /**
         * Schedules a send-out focus using battleSendoutCount for timing.
         * This ensures the camera focuses on the same Pokemon that's being animated.
         *
         * @param battle The battle
         * @param targetPosition The position where the Pokemon is being sent out to
         * @param battleSendoutCount The send-out index (0 for first, 1 for second, etc.)
         */
        fun scheduleSendOutFocus(battle: PokemonBattle, targetPosition: Vec3, battleSendoutCount: Int) {
            val battleId = battle.battleId
            
            // Calculate camera start delay based on battleSendoutCount
            // Each send-out gets SEND_OUT_CAMERA_DURATION of camera time
            val cameraStartDelay = battleSendoutCount * SEND_OUT_CAMERA_DURATION
            
            afterOnServer(seconds = cameraStartDelay) {
                // Store position for MoLang
                sendOutPositions[battleId] = targetPosition
                
                // Start camera focus
                val cleanup = battleCoordinators[battleId]?.startSendOutFocus(targetPosition)
                
                // Release focus after duration
                afterOnServer(seconds = SEND_OUT_CAMERA_DURATION) {
                    cleanup?.invoke()
                    currentSendOutEntities.remove(battleId)
                    sendOutPositions.remove(battleId)
                }
            }
        }

        /**
         * Starts send-out focus for a battle at a target position (immediate, non-queued).
         *
         * This triggers the camera to focus on the send-out position during the
         * pokeball throw animation. For battle start send-outs, use scheduleSendOutFocus instead.
         *
         * @param battle The battle
         * @param targetPosition The position where the Pokemon is being sent out to
         * @return A cleanup function, or null if focus could not be set
         */
        fun startSendOutFocus(battle: PokemonBattle, targetPosition: Vec3): (() -> Unit)? {
            // Store position for MoLang query resolution
            sendOutPositions[battle.battleId] = targetPosition

            val cleanup = battleCoordinators[battle.battleId]?.startSendOutFocus(targetPosition)

            // Return a cleanup function that also clears the stored data
            return if (cleanup != null) {
                {
                    cleanup()
                    currentSendOutEntities.remove(battle.battleId)
                    sendOutPositions.remove(battle.battleId)
                }
            } else null
        }

        /**
         * Clears all stored user/target entity UUIDs for a battle.
         * Called when the battle ends or focus is forcibly released.
         */
        private fun clearStoredEntities(battleId: UUID) {
            currentUserEntities.remove(battleId)
            currentTargetEntities.remove(battleId)
            currentSendOutEntities.remove(battleId)
            sendOutPositions.remove(battleId)
        }
    }

    /**
     * The role of entities being focused.
     * - USER: The Pokemon using the move (the attacker/caster)
     * - TARGET: The Pokemon being affected by the move
     * - SEND_OUT: A Pokemon being sent out (focus on ball, then Pokemon)
     */
    enum class FocusRole { USER, TARGET, SEND_OUT }

    private var cameraId: UUID? = null
    private var controller: ICameraController? = null
    private val focusCounter = AtomicInteger(0)

    override fun bind(cameraId: UUID) {
        this.cameraId = cameraId
        this.controller = CameraManager.getCamera(cameraId)
        controller?.let { initializeControllerVariables(it) }
    }

    override fun release() {
        controller?.let { clearFocus(it) }
        this.cameraId = null
        this.controller = null
        focusCounter.set(0)
    }

    override fun getCameraId(): UUID? = cameraId

    /**
     * Sets a focus override on specific entities during battle events.
     *
     * This method is used by battle action effects to temporarily focus the
     * camera on the user or target of a move. The focus is reference-counted,
     * allowing multiple simultaneous focus requests.
     *
     * The camera will focus on the average position of all provided entities,
     * allowing it to work correctly regardless of which side the Pokemon belongs to.
     * The role determines which camera angle/state is used.
     *
     * @param entities List of entities to focus on (typically Pokemon)
     * @param role Whether these entities are users or targets of the action
     * @return A cleanup function to release this focus, or null if focus could not be set
     */
    fun startFocus(entities: List<Entity>, role: FocusRole): (() -> Unit)? {
        if (entities.isEmpty()) return null
        val ctrl = controller ?: return null

        val focusPosition = averagePosition(entities) ?: return null
        focusCounter.incrementAndGet()

        ctrl.setFocusOverride(focusPosition)
        ctrl.setVariable("camera_focus_active", 1.0)
        // USER = 0 (user_focus state), TARGET = 1 (target_focus state)
        ctrl.setVariable("camera_focus_role", if (role == FocusRole.TARGET) 1.0 else 0.0)
        ctrl.setVariable("camera_focus_multi", entities.size.toDouble())

        return {
            if (focusCounter.decrementAndGet() <= 0) {
                clearFocus(ctrl)
            }
        }
    }

    /**
     * Starts a send-out focus at a target position.
     *
     * This sets up the camera to focus on the send-out target position
     * during the pokeball throw animation.
     *
     * @param targetPosition The position where the Pokemon is being sent out to
     * @return A cleanup function to release this focus, or null if focus could not be set
     */
    fun startSendOutFocus(targetPosition: Vec3): (() -> Unit)? {
        val ctrl = controller ?: return null

        focusCounter.incrementAndGet()

        // Trigger instant transition to prevent smoothing when switching sides
        ctrl.triggerInstantTransition()

        // Set send-out mode active and focus on the target position
        ctrl.setVariable("send_out_active", 1.0)
        ctrl.setVariable("send_out_position_x", targetPosition.x)
        ctrl.setVariable("send_out_position_y", targetPosition.y)
        ctrl.setVariable("send_out_position_z", targetPosition.z)
        ctrl.setFocusOverride(targetPosition.add(0.0, 1.0, 0.0)) // Focus slightly above ground level

        return {
            if (focusCounter.decrementAndGet() <= 0) {
                // Only disable send_out_active - keep position values so blend transition works
                ctrl.setVariable("send_out_active", 0.0)
                clearFocus(ctrl)
            }
        }
    }

    /**
     * Forces an immediate release of all focus overrides.
     *
     * This is used when a battle ends abruptly or when focus needs to be
     * cleared regardless of outstanding focus references.
     */
    fun forceRelease() {
        val ctrl = controller ?: return
        focusCounter.set(0)
        ctrl.setVariable("send_out_active", 0.0)
        // Don't reset position variables - they may still be used during blend transitions
        clearFocus(ctrl)
    }

    private fun initializeControllerVariables(controller: ICameraController) {
        clearFocus(controller)
        // Initialize send-out variables
        controller.setVariable("send_out_active", 0.0)
        controller.setVariable("send_out_position_x", 0.0)
        controller.setVariable("send_out_position_y", 0.0)
        controller.setVariable("send_out_position_z", 0.0)
    }

    private fun clearFocus(controller: ICameraController) {
        controller.setVariable("camera_focus_active", 0.0)
        controller.setVariable("camera_focus_role", 0.0)
        controller.setVariable("camera_focus_multi", 0.0)
        controller.setFocusOverride(null)
    }

    private fun averagePosition(entities: List<Entity>): Vec3? {
        if (entities.isEmpty()) return null
        var sum = Vec3(0.0, 0.0, 0.0)
        entities.forEach { entity ->
            val pos = if (entity is LivingEntity) {
                entity.eyePosition
            } else {
                entity.position()
            }
            sum = sum.add(pos)
        }
        val factor = 1.0 / entities.size.toDouble()
        return Vec3(sum.x * factor, sum.y * factor, sum.z * factor)
    }
}
