/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.camera.ICameraController
import com.cobblemon.mod.common.api.camera.ICameraCoordinator
import com.cobblemon.mod.common.api.camera.CameraManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordinator for stadium battle cameras.
 *
 * This coordinator manages focus behavior for battle cameras, including:
 * - Setting focus targets on attacking/defending Pokemon
 * - Managing reference-counted focus overrides
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
        }

        /**
         * Releases the coordinator for a battle.
         *
         * @param battleId The battle ID
         */
        fun release(battleId: UUID) {
            val coordinator = battleCoordinators.remove(battleId)
            coordinator?.release()
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
            battleCoordinators[battleId]?.forceRelease()
        }

        /**
         * Starts focus on entities for a battle.
         *
         * This is a convenience method for backward compatibility.
         *
         * @param battle The battle
         * @param entities List of entities to focus on
         * @param role The focus role (ATTACKER or TARGET)
         * @return A cleanup function, or null if focus could not be set
         */
        fun startFocus(battle: PokemonBattle, entities: List<Entity>, role: FocusRole): (() -> Unit)? {
            return battleCoordinators[battle.battleId]?.startFocus(battle, entities, role)
        }
    }

    enum class FocusRole { ATTACKER, TARGET }

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
     * camera on attacking or defending Pokemon. The focus is reference-counted,
     * allowing multiple simultaneous focus requests.
     *
     * @param battle The battle this coordinator is managing
     * @param entities List of entities to focus on (typically Pokemon)
     * @param role Whether these entities are attackers or targets
     * @return A cleanup function to release this focus, or null if focus could not be set
     */
    fun startFocus(battle: PokemonBattle, entities: List<Entity>, role: FocusRole): (() -> Unit)? {
        if (entities.isEmpty()) return null
        val ctrl = controller ?: return null

        val focusPosition = averagePosition(entities) ?: return null
        focusCounter.incrementAndGet()

        ctrl.setFocusOverride(focusPosition)
        ctrl.setVariable("camera_focus_active", 1.0)
        ctrl.setVariable("camera_focus_role", if (role == FocusRole.TARGET) 1.0 else 0.0)
        ctrl.setVariable("camera_focus_multi", entities.size.toDouble())

        return {
            if (focusCounter.decrementAndGet() <= 0) {
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
        clearFocus(ctrl)
    }

    private fun initializeControllerVariables(controller: ICameraController) {
        clearFocus(controller)
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


