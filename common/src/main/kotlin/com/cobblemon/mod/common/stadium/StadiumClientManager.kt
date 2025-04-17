package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.linearLerpAlpha
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.math.fromEulerXYZ
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.math.toEulerXYZ
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
import com.cobblemon.mod.common.util.plus

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
        cameraRotation = Quaternionf()
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
    var cameraRotation: Quaternionf = Quaternionf()

    var startTime = 0.0
    var startPosition: Vec3? = null
    var startRotation: Vector3f? = null
    var attackAnimationPokemon: PokemonEntity? = null
    var animation: PrimaryAnimation? = null

    fun playAnimation(pokemon: PokemonEntity, animation: PrimaryAnimation) {
        if (startTime != 0.0) return
        attackAnimationPokemon = pokemon
        this.animation = animation
        animation.afterAction += {
            cameraPosition = Vec3.ZERO
            cameraRotation = Quaternionf()
            attackAnimationPokemon = null
            this.animation = null
        }
    }

    fun updateCameraData(delta: Double) {
        val pokemon = attackAnimationPokemon ?: return
        val delegate = pokemon.delegate as? PokemonClientDelegate ?: return
        if (delegate.poseIntensity > 10000000) return
        val animation = animation ?: return

        delegate.updatePartialTicks(delta.toFloat())
        MountedPokemonAnimationRenderController.reset()
        MountedPokemonAnimationRenderController.setup(pokemon, delta.toFloat())

        val locator = delegate.locatorStates["camera"] ?: return
        cameraPosition = locator.position.add(locator.matrix.getTranslation(Vector3f()).toVec3d())
        cameraRotation = locator.matrix.getUnnormalizedRotation(Quaternionf()).normalize()
    }
}
