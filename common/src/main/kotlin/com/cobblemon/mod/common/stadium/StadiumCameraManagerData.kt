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

class StadiumCameraManagerEntry(
    var id: ResourceLocation,
    val states: MutableMap<String, StadiumCameraState>,
    val startingState: String
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeIdentifier(id)
        buf.writeMap(states, {_, key -> buf.writeString(key)}, {_, value -> value.encode(buf)})
        buf.writeString(startingState)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraManagerEntry {
            val id = buf.readIdentifier()
            val states = buf.readMap({buf.readString()}, {StadiumCameraState.decode(buf)}).toMutableMap()
            val startState = buf.readString()
            return StadiumCameraManagerEntry(id, states, startState)
        }
    }
}

class StadiumCameraState(
    val camera: Expression,
    val target: StadiumCameraTarget,
    val listOfTransitions: MutableList<StadiumCameraTransition>,
    val loop: Boolean
) {
    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeString(camera.originalString)
        buf.writeEnum(target)
        buf.writeCollection(listOfTransitions) {_, transition -> transition.encode(buf)}
        buf.writeBoolean(loop)
    }

    companion object {
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraState {
            val camera = buf.readString().asExpression()
            val target = buf.readEnum(StadiumCameraTarget::class.java)
            val listOfTransitions = buf.readList { StadiumCameraTransition.decode(buf) }.toMutableList()
            val loop = buf.readBoolean()
            return StadiumCameraState(camera, target, listOfTransitions, loop)
        }
    }
}

class StadiumCameraTransition(
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
        fun decode(buf: RegistryFriendlyByteBuf): StadiumCameraTransition {
            val targetState = buf.readString()
            val checkOnEnd = buf.readBoolean()
            val condition = buf.readString().asExpression()
            return StadiumCameraTransition(targetState, checkOnEnd, condition)
        }
    }
}

enum class StadiumCameraTarget {
    CENTRE,
    ACTIVE_POKEMON,
    ENEMY_POKEMON,
    TRAINER
}

object StadiumCameraManagerAdapter: JsonDeserializer<StadiumCameraManagerEntry> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): StadiumCameraManagerEntry {
        if (json is JsonObject) {
            val states = mutableMapOf<String, StadiumCameraState>()
            json["states"]?.asJsonObject?.entrySet()?.forEach { (name, state) ->
                val camera = state.asJsonObject["camera"]?.asString?.asExpression() ?: "".asExpression()
                val target = when (state.asJsonObject["target"]?.asString) {
                    "CENTRE" -> StadiumCameraTarget.CENTRE
                    "ACTIVE_POKEMON" -> StadiumCameraTarget.ACTIVE_POKEMON
                    "ENEMY_POKEMON" -> StadiumCameraTarget.ENEMY_POKEMON
                    "TRAINER" -> StadiumCameraTarget.TRAINER
                    else -> StadiumCameraTarget.CENTRE
                }
                val listOfTransitions = mutableListOf<StadiumCameraTransition>()
                state.asJsonObject["transitions"]?.asJsonArray?.forEach {
                    val targetState = it.asJsonObject["targetState"]?.asString ?: ""
                    val checkOnEnd = it.asJsonObject["checkOnEnd"]?.asBoolean ?: false
                    val condition = it.asJsonObject["condition"]?.asString?.asExpression() ?: "0.0".asExpression()
                    listOfTransitions.add(StadiumCameraTransition(targetState, checkOnEnd, condition))
                }
                val loop = state.asJsonObject["loop"]?.asBoolean ?: false
                states[name] = StadiumCameraState(camera, target, listOfTransitions, loop)
            }
            val startingState = json["startingState"].asString ?: ""
            return StadiumCameraManagerEntry(cobblemonResource(""), states, startingState)
        }
        throw IllegalStateException("StadiumCameraManager could not be parsed")
    }
}

