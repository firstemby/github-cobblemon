package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.linearLerpAlpha
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.math.fromEulerXYZ
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.function.Function

object StadiumClientManager {
    //Molang
    fun MoLangRuntime.setupCameraFunctions(): MoLangRuntime {
        environment.query.addFunctions(
            hashMapOf(
                "moveCamera" to Function { params ->
                    if (params.get<MoValue>(0) !is StringValue) {
                        return@Function Unit
                    }
                    val camera = StadiumCameras.getMoveCamera(params.get<MoValue>(0).asString())
                    return@Function camera?.id?.toString() ?: ""
                }
            )
        )
        return this
    }

    val runtime = MoLangRuntime().setup().setupClient().setupCameraFunctions()

    var currentStadiumBattleUUID: UUID? = null

    var Instruction = "DEFAULT"
    var InstructionChangeTime = 0.0
    var InstructionJustChanged = false

    fun updateState(newState: String) {
        Instruction = newState
        InstructionChangeTime = 0.0
        InstructionJustChanged = true
    }

    fun onLogout() {
        currentStadiumBattleUUID = null
    }

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
        return animation != null
    }

    //Molang Data
    fun updateMolangVar() {
        val environment = runtime.environment
        environment.setSimpleVariable("instruction", StringValue(Instruction))
        //environment.setSimpleVariable("instructionChangeTime", DoubleValue(InstructionChangeTime))
        environment.setSimpleVariable("instructionJustChanged", DoubleValue(InstructionJustChanged))
        val isSpectating = CobblemonClient.battle?.spectating ?: false
        environment.setSimpleVariable("isBattling", DoubleValue(!isSpectating))
        environment.setSimpleVariable("isSpectating", DoubleValue(isSpectating))
    }

    fun instructionUpdate(newState: String, additionalValues: MutableMap<String, MoValue>) {
        val environment = runtime.environment
        Instruction = newState
        InstructionChangeTime = 0.0
        InstructionJustChanged = true
        for (value in additionalValues) {
            environment.setSimpleVariable(value.key, value.value)
        }
    }

    //Camera Manager
    var activeCameraManager: ResourceLocation? = null
    var cameraTimer: Double = 0.0
    var currentCameraState: String = ""
    var nextCameraState: String = ""
    var calculateNextState: Boolean = true

    fun tickCameraManager() {
        if (!shouldUseStadiumCamera()) return

        //Molang Variables
        val environment = runtime.environment
        if (CobblemonClient.battle != null) {
            environment.setSimpleVariable("selectingMove", DoubleValue(CobblemonClient.battle!!.mustChoose))
        }
        else {
            environment.setSimpleVariable("selectingMove", DoubleValue(0.0))
        }

        val currentManager = StadiumCameraManagers.managers[activeCameraManager]!!
        val currentState = currentManager.states[currentCameraState]!!
        val currentCamera = StadiumCameras.cameras[runtime.resolveString(currentState.camera).asResource()]

        InstructionChangeTime += 0.05
        if (currentCamera != null) {
            cameraTimer += 0.05
            if (cameraTimer > currentCamera.length) {
                if (currentState.loop) {
                    cameraTimer -= currentCamera.length
                }
                else {
                    cameraTimer = currentCamera.length
                }
            }
        }

        if (!calculateNextState) {
            currentCameraState = nextCameraState
            calculateNextState = true

            cameraTimer = if (currentCamera != null && currentCamera.length != 0.0) cameraTimer - currentCamera.length else 0.0
        }
        else {
            updateMolangVar()
            environment.setSimpleVariable("instructionChangeTime", DoubleValue(InstructionChangeTime))

            val endOfAnim = if (currentCamera != null) cameraTimer + 1/20 >= currentCamera.length else true

            val nextState = evaluateTransitions(currentState, endOfAnim)
            if (nextState != null) {
                nextCameraState = nextState.targetState
                while (true) {
                    val newState = currentManager.states[nextCameraState]!!
                    val newCamera = StadiumCameras.cameras[runtime.resolveString(newState.camera).asResource()]
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
        if (InstructionJustChanged) {
            InstructionJustChanged = false
            Instruction = "NONE"
        }
    }

    private fun evaluateTransitions(state: StadiumCameraState, checkEnd: Boolean) : StadiumCameraTransition? {
        var nextState = state.listOfTransitions.firstOrNull { !it.checkOnEnd && runtime.resolveBoolean(it.condition) }
        if (nextState == null && checkEnd) {
            nextState = state.listOfTransitions.firstOrNull { it.checkOnEnd && runtime.resolveBoolean(it.condition) }
        }
        return nextState
    }

    var cameraPosition: Vec3 = Vec3.ZERO
    var cameraRotation: Vector3f = Vector3f()

    var startTime = 0.0
    var startPosition: Vec3? = null
    var startRotation: Vector3f? = null
    var attackAnimationPokemon: PokemonEntity? = null
    var animation: CameraAnimation? = null

    fun playAnimation(animation: CameraAnimation, pokemon: PokemonEntity) {
        if (startTime != 0.0) return
        attackAnimationPokemon = pokemon
        this.animation = animation
    }

    fun updateCameraData(delta: Double) {
        val pokemon = attackAnimationPokemon ?: return
        val animation = animation ?: return
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return
        delegate.updatePartialTicks(delta.toFloat())
        if (startTime == 0.0) {
            startTime = delegate.animationSeconds.toDouble()

            val rotation = Quaternionf().fromEulerXYZ(0f, 360f.toRadians() - (((pokemon.yBodyRot % 360) + 180f).toRadians()), 0f)
            startRotation = Vector3f(
                animation.origin.rotation.x.toRadians(),
                360f.toRadians() - (((pokemon.yBodyRot - 45 % 360) + 180f).toRadians() + animation.origin.rotation.y.toRadians()),
                 360f.toRadians() - (((animation.origin.rotation.z + 180 % 360) + 180f).toRadians()),
            )
            startRotation = startRotation

            startPosition = animation.origin.position.scale(1.0 / 20.0)
            startPosition = rotation.transform(startPosition!!.toVector3f()).toVec3d().add(pokemon.position())
        }

        if (delegate.animationSeconds > startTime + animation.length) {
            startTime = 0.0
            attackAnimationPokemon = null
            this.animation = null
            cameraPosition = Vec3.ZERO
            cameraRotation = Vector3f()
            startPosition = null
            startRotation = null
            return
        }

        val cameraOriginPosition = startPosition ?: return
        val cameraOriginRotation = startRotation ?: return

        val animationDelta = delegate.animationSeconds - startTime

        val toPositionIndex = animation.timeline.position.entries.sortedBy { it.key }.indexOfFirst { it.key > animationDelta }
        val toPosition = if (toPositionIndex != -1) {
            animation.timeline.position.entries.sortedBy { it.key }[toPositionIndex]
        } else {
            animation.timeline.position.entries.sortedBy { it.key }.last()
        }
        val fromPosition = when (toPositionIndex) {
            0 -> animation.timeline.position.entries.sortedBy { it.key }.first()
            -1 -> toPosition
            else -> animation.timeline.position.entries.sortedBy { it.key }[toPositionIndex - 1]
        }

        val pokemonRotation = Quaternionf().fromEulerXYZ(0f, 360f.toRadians() - (((pokemon.yBodyRot + 90 % 360) + 180f).toRadians()), 0f)
        val position = if (fromPosition.value == toPosition.value) {
            toPosition.value.scale(1.0 / 20.0)
        }
        else {
            Vec3(
                fromPosition.value.x + (toPosition.value.x - fromPosition.value.x) * linearLerpAlpha(fromPosition.key, toPosition.key, animationDelta),
                fromPosition.value.y + (toPosition.value.y - fromPosition.value.y) * linearLerpAlpha(fromPosition.key, toPosition.key, animationDelta),
                fromPosition.value.z + (toPosition.value.z - fromPosition.value.z) * linearLerpAlpha(fromPosition.key, toPosition.key, animationDelta),
            ).scale(1.0 / 20.0)
        }
        cameraPosition = cameraOriginPosition.add(pokemonRotation.transform(position.toVector3f()).toVec3d())

        val toRotationIndex = animation.timeline.rotation.entries.sortedBy { it.key }.indexOfFirst { it.key > animationDelta }
        val toRotation = if (toRotationIndex != -1) {
            animation.timeline.rotation.entries.sortedBy { it.key }[toRotationIndex]
        } else {
            animation.timeline.rotation.entries.sortedBy { it.key }.last()
        }
        val fromRotation = when (toRotationIndex) {
            0 -> animation.timeline.rotation.entries.sortedBy { it.key }.first()
            -1 -> toRotation
            else -> animation.timeline.rotation.entries.sortedBy { it.key }[toRotationIndex - 1]
        }

        val rotation = if (fromRotation.value == toRotation.value) {
            Vector3f(
                fromRotation.value.x.toRadians(),
                fromRotation.value.y.toRadians(),
                fromRotation.value.z.toRadians(),
            )
        }
        else {
            Vector3f(
                (fromRotation.value.x.toRadians() + (toRotation.value.x.toRadians() - fromRotation.value.x.toRadians()) * linearLerpAlpha(fromRotation.key, toRotation.key, animationDelta)).toFloat(),
                (fromRotation.value.y.toRadians() + (toRotation.value.y.toRadians() - fromRotation.value.y.toRadians()) * linearLerpAlpha(fromRotation.key, toRotation.key, animationDelta)).toFloat(),
                (fromRotation.value.z.toRadians() + (toRotation.value.z.toRadians() - fromRotation.value.z.toRadians()) * linearLerpAlpha(fromRotation.key, toRotation.key, animationDelta)).toFloat(),
            )
        }
        cameraRotation = Vector3f(cameraOriginRotation).add(rotation)

//        if (!shouldUseStadiumCamera()) return
//
//        val environment = runtime.environment
//        updateMolangVar()
//        environment.setSimpleVariable("instructionChangeTime", DoubleValue(InstructionChangeTime + delta/20))
//
//        val currentManager = StadiumCameraManagers.managers[activeCameraManager]!!
//        val currentState = currentManager.states[currentCameraState]!!
//        val currentCamera = StadiumCameras.cameras[runtime.resolveString(currentState.camera).asResource()]
//
//        if (currentCamera != null) {
//            var renderTime = cameraTimer + delta/20
//            if (renderTime > currentCamera.length && currentCamera.length != 0.0 && !calculateNextState) {
//                val newState = currentManager.states[nextCameraState]!!
//                val newCamera = StadiumCameras.cameras[runtime.resolveString(newState.camera).asResource()] ?: return
//
//                cameraPosition = newCamera.resolvePosition(renderTime - currentCamera.length)
//                cameraRotation = newCamera.resolveRotation(renderTime - currentCamera.length)
//            }
//            else {
//                if (renderTime > currentCamera.length && currentCamera.length != 0.0) renderTime = currentCamera.length
//                cameraPosition = currentCamera.resolvePosition(renderTime)
//                cameraRotation = currentCamera.resolveRotation(renderTime)
//            }
//        }
    }
}
