package com.cobblemon.mod.common.stadium

import net.fabricmc.loader.impl.lib.sat4j.core.Vec
import net.minecraft.client.Camera
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID

object StadiumClientManager {
    var currentStadiumBattleUUID: UUID? = null
    var stadiumBattleDefenition: StadiumBattleDefenition? = null

    fun handleJoin(battleUUID: UUID, cameraManager: ResourceLocation) {
        currentStadiumBattleUUID = battleUUID
        activeCameraManager = cameraManager

        cameraTimer = 0.0
        currentCameraState = StadiumCameraManagers.managers[cameraManager]!!.startingState
        cameraPosition = Vec3.ZERO
        cameraRotation = Vector3f()
        updateCameraData(0.0)
    }

    fun inStadiumBattle(): Boolean {
        return currentStadiumBattleUUID != null
    }

    fun shouldUseStadiumCamera(): Boolean {
        return inStadiumBattle()
    }

    //Camera Manager
    var activeCameraManager: ResourceLocation? = null
    var cameraTimer: Double = 0.0
    var currentCameraState: String = ""
    var nextCameraState: String = ""
    var calculateNextState: Boolean = true

    fun tickCameraManager() {
        if (!shouldUseStadiumCamera()) return

        val currentManager = StadiumCameraManagers.managers[activeCameraManager]!!
        val currentState = currentManager.states[currentCameraState]!!
        val currentCamera = StadiumCameras.cameras[currentState.camera]

        if (currentCamera != null) {
            cameraTimer += 1/20
            if (cameraTimer > currentCamera.length) cameraTimer = currentCamera.length
        }

        if (!calculateNextState) {
            currentCameraState = nextCameraState
            calculateNextState = true

            cameraTimer = if (currentCamera != null && currentCamera.length != 0.0) cameraTimer - currentCamera.length else 0.0
        }
        else {
            val endOfAnim = if (currentCamera != null) cameraTimer + 1/20 > currentCamera.length else true

            val nextState = evaluateTransitions(currentState, endOfAnim)
            if (nextState != null) {
                nextCameraState = nextState.targetState
                while (true) {
                    val newState = currentManager.states[nextCameraState]!!
                    val newCamera = StadiumCameras.cameras[newState.camera]
                    val newEndOfAnim = if (newCamera != null) newCamera.length == 0.0 else true

                    val newNextState = evaluateTransitions(newState, newEndOfAnim)
                    if (newNextState == null) {
                        break
                    }
                    else {
                        nextCameraState = newNextState.targetState
                    }
                }
                calculateNextState = false
            }
        }
    }

    private fun evaluateTransitions(state: StadiumCameraState, checkEnd: Boolean) : StadiumCameraTransition? {
        var nextState = state.listOfTransitions.firstOrNull { !it.checkOnEnd } //TODO Evaluate condition too
        if (nextState == null && checkEnd) {
            nextState = state.listOfTransitions.firstOrNull { it.checkOnEnd } //TODO Evaluate condition too
        }
        return nextState
    }

    var cameraPosition: Vec3 = Vec3.ZERO
    var cameraRotation: Vector3f = Vector3f()

    fun updateCameraData(delta: Double) {
        if (!shouldUseStadiumCamera()) return

        val currentManager = StadiumCameraManagers.managers[activeCameraManager]!!
        val currentState = currentManager.states[currentCameraState]!!
        val currentCamera = StadiumCameras.cameras[currentState.camera]

        if (currentCamera != null) {
            val renderTime = cameraTimer + delta/20
            if (renderTime > currentCamera.length && currentCamera.length != 0.0 && !calculateNextState) {
                val newState = currentManager.states[nextCameraState]!!
                val newCamera = StadiumCameras.cameras[newState.camera] ?: return

                cameraPosition = newCamera.resolvePosition(renderTime - currentCamera.length)
                cameraRotation = newCamera.resolveRotation(renderTime - currentCamera.length)
            }
            else {
                cameraPosition = currentCamera.resolvePosition(renderTime)
                cameraRotation = currentCamera.resolveRotation(renderTime)
            }
        }
    }
}