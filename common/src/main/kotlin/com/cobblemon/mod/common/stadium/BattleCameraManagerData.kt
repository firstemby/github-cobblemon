package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.util.*
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.lang.reflect.Type

class BattleCameraManagerEntry(
    var id: ResourceLocation,
    val states: MutableMap<String, BattleCameraState>,
    val startingState: String
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeMap(states, {_, key -> buf.writeString(key)}, {_, value -> value.encode(buf)})
        buf.writeString(startingState)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): BattleCameraManagerEntry {
            val id = buf.readIdentifier()
            val states = buf.readMap({buf.readString()}, {BattleCameraState.decode(buf)}).toMutableMap()
            val startState = buf.readString()
            return BattleCameraManagerEntry(id, states, startState)
        }
    }
}

class BattleCameraState(
    val camera: ResourceLocation,
    val target: StadiumCameraTarget,
    val listOfTransitions: MutableList<BattleCameraTransition>,
    val loop: Boolean
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(camera)
        buf.writeEnum(target)
        buf.writeCollection(listOfTransitions) {_, transition -> transition.encode(buf)}
        buf.writeBoolean(loop)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): BattleCameraState {
            val camera = buf.readIdentifier()
            val target = buf.readEnum(StadiumCameraTarget::class.java)
            val listOfTransitions = buf.readList { BattleCameraTransition.decode(buf) }.toMutableList()
            val loop = buf.readBoolean()
            return BattleCameraState(camera, target, listOfTransitions, loop)
        }
    }
}

class BattleCameraTransition(
    val targetState: String,
    val checkOnEnd: Boolean,
    val condition: Expression
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeString(targetState)
        buf.writeBoolean(checkOnEnd)
        buf.writeString(condition.originalString)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): BattleCameraTransition {
            val targetState = buf.readString()
            val checkOnEnd = buf.readBoolean()
            val condition = buf.readString().asExpression()
            return BattleCameraTransition(targetState, checkOnEnd, condition)
        }
    }
}

enum class StadiumCameraTarget {
    CENTRE,
    ACTIVE_POKEMON,
    ENEMY_POKEMON,
    TRAINER
}

object BattleCameraManagerAdapter: JsonDeserializer<BattleCameraManagerEntry> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): BattleCameraManagerEntry {
        if (json is JsonObject) {
            val states = mutableMapOf<String, BattleCameraState>()
            json["states"]?.asJsonObject?.entrySet()?.forEach { (name, state) ->
                val camera = state.asJsonObject["camera"]?.asString?.asResource() ?: "".asResource()
                val target = when (state.asJsonObject["target"]?.asString) {
                    "CENTRE" -> StadiumCameraTarget.CENTRE
                    "ACTIVE_POKEMON" -> StadiumCameraTarget.ACTIVE_POKEMON
                    "ENEMY_POKEMON" -> StadiumCameraTarget.ENEMY_POKEMON
                    "TRAINER" -> StadiumCameraTarget.TRAINER
                    else -> StadiumCameraTarget.CENTRE
                }
                val listOfTransitions = mutableListOf<BattleCameraTransition>()
                state.asJsonObject["transitions"]?.asJsonArray?.forEach {
                    val targetState = it.asJsonObject["targetState"]?.asString ?: ""
                    val checkOnEnd = it.asJsonObject["checkOnEnd"]?.asBoolean ?: false
                    val condition = it.asJsonObject["condition"]?.asString?.asExpression() ?: "0.0".asExpression()
                    listOfTransitions.add(BattleCameraTransition(targetState, checkOnEnd, condition))
                }
                val loop = state.asJsonObject["loop"]?.asBoolean ?: false
                states[name] = BattleCameraState(camera, target, listOfTransitions, loop)
            }
            val startingState = json["startingState"].asString ?: ""
            return BattleCameraManagerEntry(cobblemonResource(""), states, startingState)
        }
        throw IllegalStateException("StadiumCameraManager could not be parsed")
    }
}

